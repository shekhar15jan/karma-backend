ALTER TABLE mcp_servers
ADD COLUMN env_vars JSONB DEFAULT '{}'::jsonb;
