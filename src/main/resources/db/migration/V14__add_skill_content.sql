-- V14: Give skills a behavioral content body. Skills become the mandatory
-- per-stage behavioral contract: content is injected into the agent's system
-- prompt, validation_rules drive output validation, execution_parameters carry
-- style/token/provider hints. Existing JSONB columns are reused.
ALTER TABLE skills ADD COLUMN IF NOT EXISTS content TEXT;
