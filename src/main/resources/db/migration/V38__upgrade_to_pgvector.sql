-- V38: Upgrade RAG to pgvector (768 dims for text-embedding-004)
-- Enable extension (requires pgvector image: pgvector/pgvector:pg16)
CREATE EXTENSION IF NOT EXISTS vector;

-- Add new vector column
ALTER TABLE public.semantic_embeddings ADD COLUMN IF NOT EXISTS embedding_vec vector(768);

-- Migrate existing JSONB data to vector (cast JSON text '[1,2]' -> '{1,2}' -> vector)
-- Works only for rows where embedding is valid JSON array of 768 floats
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding' AND data_type='jsonb') THEN
    UPDATE public.semantic_embeddings
    SET embedding_vec = CAST(translate(embedding::text, '[]', '{}') AS vector)
    WHERE embedding_vec IS NULL
      AND embedding IS NOT NULL
      AND jsonb_typeof(embedding) = 'array';
  END IF;
END $$;

-- Drop old JSONB column and rename (only if embedding_vec was successfully populated or table was empty)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='semantic_embeddings' AND column_name='embedding_vec') THEN
    -- Keep both columns for backward compat if migration fails; app will prefer embedding_vec
    -- Only drop if we have a vector column and no null vectors remain for non-empty table
    IF NOT EXISTS (SELECT 1 FROM public.semantic_embeddings WHERE embedding_vec IS NULL) THEN
      ALTER TABLE public.semantic_embeddings DROP COLUMN IF EXISTS embedding;
      ALTER TABLE public.semantic_embeddings RENAME COLUMN embedding_vec TO embedding;
    END IF;
  END IF;
END $$;

-- HNSW index for cosine similarity (vector_cosine_ops)
CREATE INDEX IF NOT EXISTS idx_semantic_embeddings_embedding_hnsw
  ON public.semantic_embeddings USING hnsw (embedding vector_cosine_ops);
