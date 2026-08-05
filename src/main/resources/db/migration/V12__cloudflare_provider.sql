-- Cloudflare Workers AI image generation provider.
-- Requires a Cloudflare API Token with Workers AI: Run permission.
-- The account_id is extracted from the apiEndpoint URL.
INSERT INTO public.providers (id, name, provider_type, status, api_endpoint, capabilities, models, created_at, updated_at)
VALUES ('c0a1b2c3-d4e5-f6a7-b8c9-d0e1f2a3b4c5', 'Cloudflare Workers AI', 'cloudflare', 'DISCONNECTED',
  'https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run',
  '["IMAGE_GENERATION"]'::jsonb,
  '["@cf/black-forest-labs/flux-1-schnell","@cf/black-forest-labs/flux-2-dev","@cf/stabilityai/stable-diffusion-xl-base-1.0","@cf/stabilityai/dreamshaper-8-lcm"]'::jsonb,
  now(), now())
ON CONFLICT DO NOTHING;