-- ============================================================
-- Karma OS - V6: Freeware-only provider policy + semantic search
-- ============================================================
-- 1. Remove paid-only providers (OpenAI, Anthropic, Stability, ElevenLabs).
--    Safe: no agents/missions reference these seeded ids.
DELETE FROM public.providers
WHERE provider_type IN ('openai', 'anthropic', 'stability', 'elevenlabs');

-- 2. OpenRouter: keep the provider but only expose free (:free) models.
UPDATE public.providers
SET models = '["google/gemma-2-27b-it:free","google/gemma-2-9b-it:free","meta-llama/llama-3.1-8b-instruct:free","meta-llama/llama-3.3-70b-instruct:free","mistralai/mistral-7b-instruct:free","qwen/qwen-2.5-72b-instruct:free"]'::jsonb
WHERE provider_type = 'openrouter';

-- 3. Refresh the "Provider & LLM Configuration" skill description
--    (drop paid provider names).
UPDATE public.skills
SET description = 'Knowledge of all provider types: Google Gemini, OpenRouter, OpenCode, Groq, Edge TTS, FFmpeg. Knows how to configure API keys, endpoints, models, and capabilities. Source of truth: KARMA-BLACK-BOOK.md §6.'
WHERE id = 'd0000000-0000-0000-0000-000000000003';

-- 4. Semantic embedding storage (JSONB + in-Java cosine similarity;
--    no pgvector extension required on the stock postgres image).
CREATE TABLE IF NOT EXISTS public.semantic_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    content TEXT NOT NULL,
    embedding JSONB NOT NULL,
    model VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_semantic_embeddings_source
    ON public.semantic_embeddings (source_type, source_id);
