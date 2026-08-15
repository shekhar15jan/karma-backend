-- ============================================================
-- V27: Add Remotion Video Provider, Disable FFmpeg for Video
-- ============================================================

-- 0. Self-heal: restore the Google Music (Lyria) row if a previous
--    (incorrect) run of this migration corrupted it into a remotion provider.
--    It is legitimately seeded by V5 with id 1111...104 as google-music.
UPDATE public.providers
SET provider_type = 'google-music',
    capabilities = '["MUSIC_GENERATION"]'::jsonb,
    models = '["lyria-3-clip-preview","lyria-3-pro-preview"]'::jsonb,
    api_endpoint = 'https://generativelanguage.googleapis.com/v1beta/models',
    status = COALESCE(NULLIF(status, ''), 'DISCONNECTED'),
    updated_at = now()
WHERE id = '11111111-1111-1111-1111-111111111104'
  AND name = 'Google Music (Lyria)'
  AND provider_type = 'remotion';

-- 1. Update any existing active 'Remotion Local' row (keeps its id/status/api key if already configured).
UPDATE public.providers
SET provider_type = 'remotion',
    capabilities = '["VIDEO_GENERATION"]'::jsonb,
    models = '["remotion-local"]'::jsonb,
    api_endpoint = COALESCE(NULLIF(api_endpoint, ''), 'http://localhost:3000'),
    status = COALESCE(NULLIF(status, ''), 'DISCONNECTED'),
    updated_at = now()
WHERE id = '11111111-1111-1111-1111-111111111105'
   OR (name = 'Remotion Local' AND deleted_at IS NULL AND status <> 'PUBLISHING');

-- 2. Insert the canonical Remotion Local provider only when no row (canonical id OR active name) exists.
--    Note: a different seed id (…105) is used so it never collides with Google Music (id …104 from V5).
INSERT INTO public.providers (id, name, provider_type, status, api_endpoint, capabilities, models, created_at, updated_at)
SELECT '11111111-1111-1111-1111-111111111105', 'Remotion Local', 'remotion', 'DISCONNECTED', 'http://localhost:3000',
       '["VIDEO_GENERATION"]'::jsonb, '["remotion-local"]'::jsonb, now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM public.providers
  WHERE id = '11111111-1111-1111-1111-111111111105'
     OR (name = 'Remotion Local' AND deleted_at IS NULL AND status <> 'PUBLISHING')
);

-- 3. Disable FFmpeg Local for video generation (keep for other media if needed, but mark as DELETED)
UPDATE public.providers
SET status = 'DELETED', deleted_at = now(), updated_at = now()
WHERE provider_type = 'FFMPEG' AND capabilities ? 'VIDEO_GENERATION';

-- 4. Update FFmpeg Local to remove VIDEO_GENERATION capability (if it has other capabilities)
-- This is a safety measure - FFmpeg provider should only be used for non-video media operations if at all
UPDATE public.providers
SET capabilities = '[]'::jsonb, updated_at = now()
WHERE provider_type = 'FFMPEG' AND status = 'DELETED';