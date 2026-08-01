-- Two-role model: ADMIN and OPERATOR only.
-- REVIEWER and USER are removed; existing accounts become OPERATOR.
-- ADMIN accounts are preserved.

UPDATE users SET role = 'OPERATOR' WHERE role IS NULL OR role NOT IN ('ADMIN', 'OPERATOR');
