-- ============================================================
-- 7. Default Flows
-- ============================================================
INSERT INTO public.flows (id, name, description, category, execution_order, enabled, is_system, created_at, updated_at) VALUES
('f0000000-0000-0000-0000-000000000001', 'Default Video Flow', 'Default execution flow for VIDEO missions', 'VIDEO', 0, true, true, now(), now()),
('f0000000-0000-0000-0000-000000000002', 'Default Text Flow', 'Default execution flow for TEXT missions', 'TEXT', 0, true, true, now(), now());


