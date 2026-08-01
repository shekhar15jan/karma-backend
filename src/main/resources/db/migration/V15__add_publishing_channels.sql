-- Seed the full catalog of social publishing destinations (idempotent by name).
INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Facebook', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Facebook');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'X (Twitter)', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'X (Twitter)');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Pinterest', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Pinterest');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Threads', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Threads');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Snapchat', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Snapchat');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'WordPress', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'WordPress');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Blogger', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Blogger');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Medium', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Medium');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Substack', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Substack');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Reddit', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Reddit');

INSERT INTO providers (id, name, provider_type, status, api_endpoint, capabilities)
SELECT gen_random_uuid(), 'Tumblr', 'PUBLISHING', 'DISCONNECTED', NULL, '["PUBLISHING"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM providers WHERE name = 'Tumblr');
