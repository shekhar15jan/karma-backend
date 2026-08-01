-- Replace the dead partial index (providers are never 'ACTIVE') with a
-- meaningful unique index over active AI providers (non-deleted, non-PUBLISHING).
DROP INDEX IF EXISTS uq_providers_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_providers_active_name
    ON providers(name) WHERE deleted_at IS NULL AND status <> 'PUBLISHING';

-- Seed the AI provider catalog (idempotent by name). All start DISCONNECTED with
-- empty keys; an admin must configure and test each before use.
INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'OpenAI', 'openai', 'DISCONNECTED', 'https://api.openai.com/v1',
       '["TEXT_GENERATION","IMAGE_GENERATION","TTS"]'::jsonb,
       '["gpt-4o","gpt-4o-mini","gpt-4-turbo"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'OpenAI');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'Anthropic Claude', 'anthropic', 'DISCONNECTED', 'https://api.anthropic.com',
       '["TEXT_GENERATION"]'::jsonb,
       '["claude-3-5-sonnet-20241022","claude-3-opus-20240229","claude-3-haiku-20240307"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Anthropic Claude');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'Google Gemini', 'google', 'DISCONNECTED', NULL,
       '["TEXT_GENERATION","TTS","IMAGE_GENERATION"]'::jsonb,
       '["gemini-2.5-flash","gemini-3-pro-preview"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Google Gemini');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'Google Imagen', 'google', 'DISCONNECTED', NULL,
       '["IMAGE_GENERATION"]'::jsonb,
       '["imagen-3.0-generate-002"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Google Imagen');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'OpenRouter', 'openrouter', 'DISCONNECTED', 'https://openrouter.ai/api/v1',
       '["TEXT_GENERATION"]'::jsonb,
       '["meta-llama/llama-3.1-405b-instruct","qwen/qwen-2.5-72b-instruct"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'OpenRouter');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'OpenCode Zen', 'opencode', 'DISCONNECTED', 'https://opencode.ai/api',
       '["TEXT_GENERATION"]'::jsonb,
       '["deepseek-v4-flash-free","big-pickle","mimo-v2.5-free"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'OpenCode Zen');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities, models)
SELECT gen_random_uuid(), 'Groq Cloud', 'groq', 'DISCONNECTED', 'https://api.groq.com/openai/v1',
       '["TEXT_GENERATION"]'::jsonb,
       '["llama-3.3-70b-versatile","llama-3.1-8b-instant"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Groq Cloud');

INSERT INTO providers (id, name, provider_type, status, capabilities)
SELECT gen_random_uuid(), 'Stability AI', 'stability', 'DISCONNECTED', '["IMAGE_GENERATION"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Stability AI');

INSERT INTO providers (id, name, provider_type, status, capabilities)
SELECT gen_random_uuid(), 'ElevenLabs', 'elevenlabs', 'DISCONNECTED', '["TTS"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'ElevenLabs');

INSERT INTO providers (id, name, provider_type, status, capabilities)
SELECT gen_random_uuid(), 'Edge TTS', 'edge-tts', 'DISCONNECTED', '["TTS"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Edge TTS');

INSERT INTO providers (id, name, provider_type, status, capabilities)
SELECT gen_random_uuid(), 'FFmpeg Local', 'ffmpeg', 'DISCONNECTED', '["VIDEO_GENERATION"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'FFmpeg Local');
