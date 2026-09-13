-- ============================================================
-- V36: Add MCP Servers tables
-- ============================================================

CREATE TABLE IF NOT EXISTS public.mcp_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    transport_type VARCHAR(50) NOT NULL DEFAULT 'stdio', -- 'stdio' or 'sse'
    command_or_url VARCHAR(1024) NOT NULL,
    args TEXT, -- JSON array of strings for stdio arguments
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.agent_mcp_servers (
    agent_id UUID NOT NULL,
    mcp_server_id UUID NOT NULL,
    PRIMARY KEY (agent_id, mcp_server_id),
    CONSTRAINT fk_agent_mcp_servers_agent FOREIGN KEY (agent_id)
        REFERENCES public.agents(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_mcp_servers_mcp FOREIGN KEY (mcp_server_id)
        REFERENCES public.mcp_servers(id) ON DELETE CASCADE
);
