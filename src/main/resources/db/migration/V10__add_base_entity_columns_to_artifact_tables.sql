-- Artifact, Review and Publication entities now extend BaseEntity (UUID PK,
-- plus created_at/updated_at/deleted_at/status columns). Add the missing
-- BaseEntity columns to their tables to match the entity mapping.
ALTER TABLE artifacts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE publications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
