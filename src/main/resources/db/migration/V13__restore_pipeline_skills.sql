-- Restore the V5 skills that were linked to pipeline agents by V6 (agent_skills)
-- and accidentally removed by V12. Only the truly orphaned V5 skills stay deleted.
INSERT INTO skills (id, name, description, category, status) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Video Script Generator', 'Generates optimized video scripts from research data', 'agent', 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000002', 'Thumbnail Creator', 'AI-powered thumbnail design and generation', 'agent', 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000004', 'Blog Post Generator', 'Converts video scripts into SEO-optimized blog posts', 'agent', 'ACTIVE');

INSERT INTO agent_skills (agent_id, skill_id) VALUES
    ('00000000-0000-0000-0000-00000000a003', 'a0000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-00000000a006', 'a0000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-00000000a004', 'a0000000-0000-0000-0000-000000000004');
