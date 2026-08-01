-- Two-mode execution: AUTO (chain steps, review only the final content) vs REVIEW (human gate per step)
ALTER TABLE executions ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'REVIEW';

-- Per-step human review gating (used in REVIEW mode)
ALTER TABLE execution_steps ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- Generalize reviews to cover step reviews; artifact_id becomes optional
ALTER TABLE reviews ALTER COLUMN artifact_id DROP NOT NULL;
ALTER TABLE reviews ADD COLUMN step_id UUID REFERENCES execution_steps(id) ON DELETE CASCADE;
CREATE INDEX idx_reviews_step_id ON reviews(step_id);
