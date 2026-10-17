-- New missions default to the dark "codeatcloud" theme instead of the light whiteboard.
-- Existing missions keep the theme they were created with.
ALTER TABLE missions ALTER COLUMN theme SET DEFAULT 'codeatcloud';
