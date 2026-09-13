-- Track the Flow Designer canvas node that produced each execution step.
-- Used to reconstruct upstream outputs for artifact handoff across review pauses.
ALTER TABLE public.execution_steps ADD COLUMN node_id varchar(64);