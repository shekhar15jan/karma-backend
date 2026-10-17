-- ============================================================
-- 43. Director Agent — catalogue-aware frame selection.
--
-- Inserted between Voice (step 3) and Video (step 4) in the default video
-- flow. Ships OFF by default (video.director_enabled = false in settings) —
-- runs as a visible no-op passthrough step until explicitly enabled. See
-- V44 for the real skill content, generated from the renderer's frame
-- catalogue rather than hand-typed here.
-- ============================================================

INSERT INTO public.skills (id, name, description, category, status, content, validation_rules, execution_parameters, created_at, updated_at) VALUES
('a0000000-0000-0000-0000-00000000000e', 'Scene Director & Frame Selection',
 'Picks the best-fit rendering frame per scene from the full frame catalogue, instead of the script writer guessing from a fixed list.',
 'agent', 'ACTIVE',
 'placeholder — replaced by V44 with content generated from the renderer catalogue',
 '{}'::jsonb, '{}'::jsonb, now(), now());

INSERT INTO public.agents (id, name, description, category, icon, status, default_prompt_id, default_provider_id, configuration, created_at, updated_at) VALUES
('00000000-0000-0000-0000-00000000a011', 'Director Agent',
 'Selects the rendering frame for each scene from the full catalogue, given the script and its narration.',
 'MEDIA', 'compass', 'ACTIVE', NULL, '8da8224b-7773-48dc-b3dd-9f19136f332e', '{}'::jsonb, now(), now());

INSERT INTO public.agent_skills (agent_id, skill_id) VALUES
('00000000-0000-0000-0000-00000000a011', 'a0000000-0000-0000-0000-00000000000e');

-- Shift Video from step 4 to step 5, insert Director at step 4.
UPDATE public.flow_agents SET step_order = 5
WHERE flow_id = 'f0000000-0000-0000-0000-000000000001'
  AND agent_id = '00000000-0000-0000-0000-00000000a008';

INSERT INTO public.flow_agents (flow_id, agent_id, step_order) VALUES
('f0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000a011', 4)
ON CONFLICT (flow_id, agent_id) DO UPDATE SET step_order = EXCLUDED.step_order;

-- Feature flags: both default off. director_shadow_mode runs the full
-- selection + validation pipeline and logs metrics without patching the
-- script, so there's a real signal to decide when director_enabled is safe
-- to flip — unlike video.rendering_mode, which has had no such path.
UPDATE public.settings SET
  settings_data = settings_data
    || '{"video.director_enabled": false, "video.director_shadow_mode": false}',
  updated_at = now()
WHERE user_id IS NULL;
