-- Google deprecated/removed gemini-3-pro-preview (Mar 2026) and gemini-2.5-flash is not available
-- to the seeded key, so update the Google Gemini provider to current GA model IDs.
UPDATE providers
SET models = '["gemini-3.5-flash","gemini-3.6-flash","gemini-3.1-pro"]'::jsonb
WHERE name = 'Google Gemini';
