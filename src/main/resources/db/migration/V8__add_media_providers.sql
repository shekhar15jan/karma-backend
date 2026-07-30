INSERT INTO providers (id, name, provider_type, status, capabilities) VALUES (gen_random_uuid(), 'Stability AI', 'STABILITY', 'CONNECTED', '["IMAGE_GENERATION"]'::jsonb);
INSERT INTO providers (id, name, provider_type, status, capabilities) VALUES (gen_random_uuid(), 'Google Cloud', 'GOOGLE', 'CONNECTED', '["TTS", "IMAGE_GENERATION"]'::jsonb);
