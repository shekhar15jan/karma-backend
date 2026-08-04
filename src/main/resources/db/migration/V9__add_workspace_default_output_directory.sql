-- V9: Allow a per-workspace default artifact output folder (relative to the mounted storage root).
ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS default_output_directory VARCHAR(500);