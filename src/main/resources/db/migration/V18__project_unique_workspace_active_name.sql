-- Dedupe case-insensitive duplicate ACTIVE projects per workspace,
-- keeping the earliest-created one so existing missions are preserved.

UPDATE projects p
SET status = 'DELETED', deleted_at = NOW()
WHERE p.status = 'ACTIVE'
  AND p.id NOT IN (
      SELECT DISTINCT ON (workspace_id, LOWER(name)) id
      FROM projects
      WHERE status = 'ACTIVE'
      ORDER BY workspace_id, LOWER(name), created_at ASC
  );

-- Replace the case-sensitive unique index with a case-insensitive one
-- so uniqueness applies to (workspace_id, LOWER(name)) among ACTIVE projects,
-- matching the workspace name behavior (V17).

DROP INDEX IF EXISTS uq_projects_name_workspace;

CREATE UNIQUE INDEX IF NOT EXISTS uq_projects_workspace_active_name
ON projects (workspace_id, LOWER(name))
WHERE status = 'ACTIVE';
