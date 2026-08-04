-- V5: Gemini Music (Lyria 3) provider + Music Generation agent
-- The provider uses the same Gemini API key infrastructure as Google Gemini/Imagen.
INSERT INTO public.providers (id, name, provider_type, status, api_endpoint, capabilities, models, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111104', 'Google Music (Lyria)', 'google-music', 'DISCONNECTED',
 'https://generativelanguage.googleapis.com/v1beta/models',
 '["MUSIC_GENERATION"]'::jsonb,
 '["lyria-3-clip-preview","lyria-3-pro-preview"]'::jsonb, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.agents (id, name, description, category, icon, status, default_prompt_id, default_provider_id, configuration, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-00000000a00b', 'Music Agent',
 'Generates background music (Gemini Lyria 3) matching the mission tone as a standalone audio track',
 'MEDIA', 'music_note', 'ACTIVE', NULL, NULL, '{}'::jsonb, now(), now())
ON CONFLICT (id) DO NOTHING;
