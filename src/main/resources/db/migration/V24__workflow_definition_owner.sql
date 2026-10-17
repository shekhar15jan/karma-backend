-- Saved workflow definitions are private to the user who saved them.
-- Legacy rows without an owner stay hidden until re-saved by their author.
ALTER TABLE workflow_definitions ADD COLUMN owner_id UUID;
CREATE INDEX idx_workflow_definitions_owner_id ON workflow_definitions(owner_id);
