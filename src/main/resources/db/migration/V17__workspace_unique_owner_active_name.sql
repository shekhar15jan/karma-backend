-- V17: Enforce unique active workspace name per owner
-- Soft-deletes duplicate ACTIVE workspaces with the same (owner_id, LOWER(name)),
-- keeping the earliest-created one so existing projects are preserved.

UPDATE workspaces w
SET status = 'DELETED', deleted_at = NOW(), updated_at = NOW()
WHERE w.id IN (
    SELECT w2.id
    FROM workspaces w2
    JOIN (
        SELECT owner_id, LOWER(name) AS norm_name, MIN(created_at) AS min_created
        FROM workspaces
        WHERE status = 'ACTIVE'
        GROUP BY owner_id, LOWER(name)
        HAVING COUNT(*) > 1
    ) dup ON dup.owner_id = w2.owner_id AND LOWER(w2.name) = dup.norm_name
    WHERE w2.status = 'ACTIVE'
      AND w2.created_at > dup.min_created
);

-- Partial unique index: uniqueness only among ACTIVE workspaces,
-- so deleted/archived names can be reused.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workspaces_owner_active_name
ON workspaces (owner_id, LOWER(name))
WHERE status = 'ACTIVE';
