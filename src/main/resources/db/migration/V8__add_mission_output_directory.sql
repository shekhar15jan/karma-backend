-- V8: Allow a per-mission artifact output folder (relative to the mounted storage root).
ALTER TABLE missions ADD COLUMN IF NOT EXISTS output_directory VARCHAR(500);