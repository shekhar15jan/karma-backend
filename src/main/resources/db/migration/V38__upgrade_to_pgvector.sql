-- V38: Upgrade RAG to pgvector (768 dims for text-embedding-004)
-- Enable extension (requires pgvector image: pgvector/pgvector:pg16). Gracefully skip if not available (e.g. external RDS without pgvector).
DO $$
BEGIN
  CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'pgvector extension not available, skipping vector migration: %', SQLERRM;
END
$$;

-- Add new vector column only if extension exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
    EXECUTE 'ALTER TABLE public.semantic_embeddings ADD COLUMN IF NOT EXISTS embedding_vec vector(768)';
  END IF;
END
$$;

-- Migrate existing JSONB data to vector (cast JSON text '[1,2]' -> '{1,2}' -> vector)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding' AND data_type='jsonb')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding_vec') THEN
    UPDATE public.semantic_embeddings
    SET embedding_vec = CAST(translate(embedding::text, '[]', '{}') AS vector)
    WHERE embedding_vec IS NULL
      AND embedding IS NOT NULL
      AND jsonb_typeof(embedding) = 'array';
  END IF;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'vector JSONB->vector migration skipped: %', SQLERRM;
END
$$;

-- Drop old JSONB column and rename (only if embedding_vec was successfully populated or table was empty)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding_vec') THEN
    IF NOT EXISTS (SELECT 1 FROM public.semantic_embeddings WHERE embedding_vec IS NULL) THEN
      ALTER TABLE public.semantic_embeddings DROP COLUMN IF EXISTS embedding;
      ALTER TABLE public.semantic_embeddings RENAME COLUMN embedding_vec TO embedding;
    END IF;
  END IF;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'vector column rename skipped: %', SQLERRM;
END
$$;

-- HNSW index for cosine similarity (vector_cosine_ops) — only if pgvector available and column is vector
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding' AND udt_name='vector') THEN
    CREATE INDEX IF NOT EXISTS idx_semantic_embeddings_embedding_hnsw
      ON public.semantic_embeddings USING hnsw (embedding vector_cosine_ops);
  ELSIF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding_vec') THEN
    CREATE INDEX IF NOT EXISTS idx_semantic_embeddings_embedding_vec_hnsw
      ON public.semantic_embeddings USING hnsw (embedding_vec vector_cosine_ops);
  END IF;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'HNSW index creation skipped: %', SQLERRM;
END
$$;
