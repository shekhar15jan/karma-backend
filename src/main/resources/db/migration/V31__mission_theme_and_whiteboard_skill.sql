-- ============================================================
-- V31: Mission-level video theme (default whiteboard)
-- 1) Adds missions.theme so each mission can pick its video theme.
-- 2) Rewrites the Video Script Generator skill from the dark
--    "codeatcloud" aesthetic to the light "whiteboard" theme so
--    generated visual_specs match the default.
-- ============================================================

ALTER TABLE missions ADD COLUMN IF NOT EXISTS theme VARCHAR(50) NOT NULL DEFAULT 'whiteboard';

UPDATE public.skills
SET content = replace(content, 'codeatcloud', 'whiteboard'),
    updated_at = now()
WHERE id = 'a0000000-0000-0000-0000-000000000001';
