-- ============================================================
-- Karma OS - V2: Add missing updated_at columns
-- ============================================================
-- BaseEntity expects created_at, updated_at, deleted_at on all
-- entities that extend it. V1 omitted updated_at for these tables.

ALTER TABLE source_documents ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE executions ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE execution_steps ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
