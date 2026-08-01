-- Store execution step input/output as free text.
-- The orchestrator passes raw LLM output between steps; LLM responses are not
-- guaranteed to be valid JSON, so JSONB columns rejected valid executions.
ALTER TABLE execution_steps ALTER COLUMN input_data TYPE TEXT USING input_data::text;
ALTER TABLE execution_steps ALTER COLUMN output_data TYPE TEXT USING output_data::text;
