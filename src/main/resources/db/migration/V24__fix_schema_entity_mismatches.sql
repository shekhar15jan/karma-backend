-- Agent maps fallback_provider_id; no earlier migration created it.
ALTER TABLE agents ADD COLUMN IF NOT EXISTS fallback_provider_id UUID;

-- ArtifactVersion extends BaseEntity, which maps updated_at.
ALTER TABLE artifact_versions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- PromptVersion extends BaseEntity as well.
ALTER TABLE prompt_versions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
