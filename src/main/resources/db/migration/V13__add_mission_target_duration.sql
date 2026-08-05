-- V13: Allow a per-mission target video duration override (seconds). When set,
-- the story/script agent targets this length instead of the global default.
ALTER TABLE missions ADD COLUMN IF NOT EXISTS target_duration_seconds INTEGER;
