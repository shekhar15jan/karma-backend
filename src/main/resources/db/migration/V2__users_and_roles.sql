-- ============================================================
-- Karma OS - V2: Users & roles (two-role model)
-- Roles: ADMIN, OPERATOR only.
-- ============================================================

ALTER TABLE public.users ALTER COLUMN role SET DEFAULT 'OPERATOR';
ALTER TABLE public.users ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'OPERATOR'));

-- Seed the initial developer/admin account (dev-bypass login, password: dev123).
INSERT INTO public.users (id, email, password, display_name, role, email_verified, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001',
        'dev@karma.local',
        '$2b$10$dNwkQJqBSGtJTJYRi7Q1FuoWb2SMBrechFdWOpUtOqovGUq7nyBoq',
        'Dev User',
        'ADMIN',
        true,
        'ACTIVE',
        now(), now());
