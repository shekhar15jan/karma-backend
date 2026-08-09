-- ============================================================
-- V16: Remove Pollinations completely.
-- The image provider is no longer supported (unsafe content) and
-- has been removed from code. Delete its provider rows and repoint
-- any agent that referenced it to the seeded Cloudflare provider.
-- ============================================================

-- 1. Repoint agent default providers that referenced Pollinations.
UPDATE public.agents
SET default_provider_id = 'c0a1b2c3-d4e5-f6a7-b8c9-d0e1f2a3b4c5',
    updated_at = now()
WHERE default_provider_id IN (
    SELECT id FROM public.providers
    WHERE provider_type IN ('pollinations', 'pollinations-image')
);

-- 2. Repoint agent fallback providers that referenced Pollinations.
UPDATE public.agents
SET fallback_provider_id = 'c0a1b2c3-d4e5-f6a7-b8c9-d0e1f2a3b4c5',
    updated_at = now()
WHERE fallback_provider_id IN (
    SELECT id FROM public.providers
    WHERE provider_type IN ('pollinations', 'pollinations-image')
);

-- 3. Delete the Pollinations provider rows.
DELETE FROM public.providers
WHERE provider_type IN ('pollinations', 'pollinations-image');
