-- ============================================================
-- Karma OS - V20: Add missing updated_at column
-- ============================================================
-- BaseEntity expects created_at, updated_at, deleted_at on all
-- entities that extend it. V1/V2/V7 omitted updated_at for
-- knowledge_documents.

ALTER TABLE knowledge_documents ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
