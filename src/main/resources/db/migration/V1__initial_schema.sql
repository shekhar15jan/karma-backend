-- ============================================================
-- Karma OS - V1: Initial Schema
-- ============================================================

-- ============================================================
-- USERS & AUTHENTICATION
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',
    avatar_url      VARCHAR(512),
    last_login_at   TIMESTAMPTZ,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    preferences     JSONB DEFAULT '{}'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE status = 'ACTIVE';

-- ============================================================
-- WORKSPACES
-- ============================================================

CREATE TABLE workspaces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    owner_id        UUID NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspaces_owner_id ON workspaces(owner_id);
CREATE UNIQUE INDEX uq_workspaces_name_owner ON workspaces(name, owner_id) WHERE status = 'ACTIVE';

-- ============================================================
-- PROJECTS
-- ============================================================

CREATE TABLE projects (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id),
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    tags                JSONB DEFAULT '[]'::jsonb,
    brand_profile       VARCHAR(100),
    default_provider_id UUID,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_workspace_id ON projects(workspace_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE UNIQUE INDEX uq_projects_name_workspace ON projects(name, workspace_id) WHERE status = 'ACTIVE';

-- ============================================================
-- MISSIONS
-- ============================================================

CREATE TABLE missions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL REFERENCES projects(id),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    mission_type        VARCHAR(50) NOT NULL DEFAULT 'VIDEO',
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    deleted_at          TIMESTAMPTZ,
    priority            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    provider_id         UUID,
    progress            INTEGER NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    execution_plan      JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_missions_project_id ON missions(project_id);
CREATE INDEX idx_missions_status ON missions(status);
CREATE INDEX idx_missions_mission_type ON missions(mission_type);
CREATE INDEX idx_missions_created_at ON missions(created_at DESC);

-- ============================================================
-- FLOWS
-- ============================================================

CREATE TABLE flows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    category        VARCHAR(50),
    execution_order INTEGER NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    parameters      JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_flows_name ON flows(name) WHERE status = 'ACTIVE';

-- Mission-Flow junction
CREATE TABLE mission_flows (
    mission_id  UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    flow_id     UUID NOT NULL REFERENCES flows(id),
    exec_order  INTEGER NOT NULL DEFAULT 0,
    status      VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (mission_id, flow_id)
);

-- ============================================================
-- AGENTS
-- ============================================================

CREATE TABLE agents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    icon                VARCHAR(50),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ,
    default_prompt_id   UUID,
    default_provider_id UUID,
    configuration       JSONB DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_agents_name ON agents(name) WHERE status = 'ACTIVE';

-- Flow-Agent junction
CREATE TABLE flow_agents (
    flow_id  UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,
    agent_id UUID NOT NULL REFERENCES agents(id),
    PRIMARY KEY (flow_id, agent_id)
);

-- ============================================================
-- AI PROVIDERS
-- ============================================================

CREATE TABLE providers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    provider_type   VARCHAR(50) NOT NULL,
    api_endpoint    VARCHAR(500),
    encrypted_api_key BYTEA,
    models          JSONB DEFAULT '[]'::jsonb,
    capabilities    JSONB DEFAULT '[]'::jsonb,
    status          VARCHAR(50) NOT NULL DEFAULT 'DISCONNECTED',
    deleted_at      TIMESTAMPTZ,
    configuration   JSONB DEFAULT '{}'::jsonb,
    health          JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_providers_name ON providers(name) WHERE status = 'ACTIVE';

-- ============================================================
-- PROMPT TEMPLATES
-- ============================================================

CREATE TABLE prompts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    content             TEXT NOT NULL,
    variables           JSONB DEFAULT '[]'::jsonb,
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    deleted_at          TIMESTAMPTZ,
    default_provider_id UUID,
    tags                JSONB DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    current_version     INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_prompts_category ON prompts(category);
CREATE INDEX idx_prompts_status ON prompts(status);
CREATE UNIQUE INDEX uq_prompts_name ON prompts(name) WHERE status != 'ARCHIVED';

-- Prompt version history
CREATE TABLE prompt_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id   UUID NOT NULL REFERENCES prompts(id) ON DELETE CASCADE,
    version     INTEGER NOT NULL,
    content     TEXT NOT NULL,
    variables   JSONB DEFAULT '[]'::jsonb,
    status      VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    deleted_at  TIMESTAMPTZ,
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_prompt_versions ON prompt_versions(prompt_id, version);

-- ============================================================
-- KNOWLEDGE PACKS
-- ============================================================

CREATE TABLE knowledge_packs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    category        VARCHAR(50),
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    deleted_at      TIMESTAMPTZ,
    tags            JSONB DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_packs_workspace_id ON knowledge_packs(workspace_id);
CREATE INDEX idx_knowledge_packs_category ON knowledge_packs(category);
CREATE UNIQUE INDEX uq_knowledge_packs_name_workspace ON knowledge_packs(name, workspace_id) WHERE status != 'ARCHIVED';

-- Knowledge pack documents
CREATE TABLE knowledge_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_pack_id UUID NOT NULL REFERENCES knowledge_packs(id) ON DELETE CASCADE,
    filename          VARCHAR(255) NOT NULL,
    content           TEXT NOT NULL,
    size              BIGINT NOT NULL DEFAULT 0,
    indexed           BOOLEAN NOT NULL DEFAULT FALSE,
    checksum          VARCHAR(64),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_documents_pack_id ON knowledge_documents(knowledge_pack_id);

-- ============================================================
-- SKILLS
-- ============================================================

CREATE TABLE skills (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    deleted_at          TIMESTAMPTZ,
    validation_rules    JSONB DEFAULT '{}'::jsonb,
    execution_parameters JSONB DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_skills_name ON skills(name) WHERE status != 'ARCHIVED';

-- Skill-Prompt junction
CREATE TABLE skill_prompts (
    skill_id  UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    prompt_id UUID NOT NULL REFERENCES prompts(id),
    PRIMARY KEY (skill_id, prompt_id)
);

-- Skill-KnowledgePack junction
CREATE TABLE skill_knowledge_packs (
    skill_id         UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    knowledge_pack_id UUID NOT NULL REFERENCES knowledge_packs(id),
    PRIMARY KEY (skill_id, knowledge_pack_id)
);

-- Agent-Skill junction
CREATE TABLE agent_skills (
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id),
    PRIMARY KEY (agent_id, skill_id)
);

-- Agent-KnowledgePack junction
CREATE TABLE agent_knowledge_packs (
    agent_id         UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    knowledge_pack_id UUID NOT NULL REFERENCES knowledge_packs(id),
    PRIMARY KEY (agent_id, knowledge_pack_id)
);

-- ============================================================
-- SOURCE DOCUMENTS (Mission Input)
-- ============================================================

CREATE TABLE source_documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id  UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    filename    VARCHAR(255) NOT NULL,
    content     TEXT NOT NULL,
    format      VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN',
    size        BIGINT NOT NULL DEFAULT 0,
    checksum    VARCHAR(64),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_source_documents_mission_id ON source_documents(mission_id);

-- ============================================================
-- EXECUTIONS
-- ============================================================

CREATE TABLE executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id      UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    deleted_at      TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    duration_ms     BIGINT,
    provider_id     UUID,
    model_used      VARCHAR(100),
    total_cost      NUMERIC(10,4),
    error_message   TEXT,
    metadata        JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_mission_id ON executions(mission_id);
CREATE INDEX idx_executions_status ON executions(status);
CREATE INDEX idx_executions_started_at ON executions(started_at DESC);

-- Execution steps
CREATE TABLE execution_steps (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id      UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    flow_id           UUID REFERENCES flows(id),
    agent_id          UUID REFERENCES agents(id),
    step_order        INTEGER NOT NULL DEFAULT 0,
    step_type         VARCHAR(50) NOT NULL,
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    deleted_at        TIMESTAMPTZ,
    input_data        JSONB,
    output_data       JSONB,
    prompt_used       TEXT,
    prompt_version    INTEGER,
    provider_id       UUID,
    model_used        VARCHAR(100),
    prompt_tokens     INTEGER DEFAULT 0,
    completion_tokens INTEGER DEFAULT 0,
    total_tokens      INTEGER DEFAULT 0,
    cost              NUMERIC(10,4),
    latency_ms        INTEGER,
    error_message     TEXT,
    retry_count       INTEGER DEFAULT 0,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_execution_steps_execution_id ON execution_steps(execution_id);
CREATE INDEX idx_execution_steps_status ON execution_steps(status);

-- ============================================================
-- ARTIFACTS
-- ============================================================

CREATE TABLE artifacts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id          UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    execution_step_id   UUID REFERENCES execution_steps(id),
    name                VARCHAR(255) NOT NULL,
    artifact_type       VARCHAR(50) NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    storage_location    TEXT,
    checksum            VARCHAR(64),
    size                BIGINT DEFAULT 0,
    content_text        TEXT,
    content_type        VARCHAR(100) DEFAULT 'text/markdown',
    metadata            JSONB DEFAULT '{}'::jsonb,
    review_status       VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    publication_status  VARCHAR(50) NOT NULL DEFAULT 'UNPUBLISHED',
    tags                JSONB DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    current_version     INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_artifacts_mission_id ON artifacts(mission_id);
CREATE INDEX idx_artifacts_type ON artifacts(artifact_type);
CREATE INDEX idx_artifacts_review_status ON artifacts(review_status);
CREATE INDEX idx_artifacts_publication_status ON artifacts(publication_status);

-- Artifact version history
CREATE TABLE artifact_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id     UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    version         INTEGER NOT NULL,
    content_text    TEXT,
    content_type    VARCHAR(100),
    storage_location TEXT,
    checksum        VARCHAR(64),
    size            BIGINT DEFAULT 0,
    metadata        JSONB DEFAULT '{}'::jsonb,
    created_by      UUID REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_artifact_versions ON artifact_versions(artifact_id, version);

-- ============================================================
-- REVIEWS
-- ============================================================

CREATE TABLE reviews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES users(id),
    decision    VARCHAR(50) NOT NULL,
    comments    TEXT,
    version_at_review INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reviews_artifact_id ON reviews(artifact_id);
CREATE INDEX idx_reviews_decision ON reviews(decision);

-- ============================================================
-- PUBLICATIONS
-- ============================================================

CREATE TABLE publications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id     UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    destination     VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    published_at    TIMESTAMPTZ,
    url             VARCHAR(500),
    error_message   TEXT,
    metadata        JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_publications_artifact_id ON publications(artifact_id);
CREATE INDEX idx_publications_status ON publications(status);
CREATE INDEX idx_publications_destination ON publications(destination);

-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    actor_id        UUID REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,
    changes         JSONB,
    metadata        JSONB,
    correlation_id  VARCHAR(100),
    ip_address      VARCHAR(45),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_type ON audit_events(event_type);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_events_actor ON audit_events(actor_id);
CREATE INDEX idx_audit_events_created_at ON audit_events(created_at DESC);

-- ============================================================
-- SETTINGS
-- ============================================================

CREATE TABLE settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID REFERENCES workspaces(id),
    user_id         UUID REFERENCES users(id),
    settings_data   JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_settings_workspace_user ON settings(workspace_id, user_id);

-- ============================================================
-- WORKFLOW DEFINITIONS
-- ============================================================

CREATE TABLE workflow_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID REFERENCES projects(id),
    name            VARCHAR(255) NOT NULL,
    nodes           JSONB NOT NULL DEFAULT '[]'::jsonb,
    edges           JSONB NOT NULL DEFAULT '[]'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workflow_definitions_project_id ON workflow_definitions(project_id);

-- Workflow runs
CREATE TABLE workflow_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id     UUID REFERENCES workflow_definitions(id),
    project_id      UUID REFERENCES projects(id),
    workflow_name   VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    duration        INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workflow_runs_workflow_id ON workflow_runs(workflow_id);
CREATE INDEX idx_workflow_runs_project_id ON workflow_runs(project_id);
CREATE INDEX idx_workflow_runs_status ON workflow_runs(status);
CREATE INDEX idx_workflow_runs_started_at ON workflow_runs(started_at DESC);

-- ============================================================
-- SEED DATA
-- ============================================================

-- Standard Flows
INSERT INTO flows (id, name, description, category, execution_order) VALUES
    ('00000000-0000-0000-0000-00000000f001', 'Research Flow', 'Gather and validate research information', 'RESEARCH', 1),
    ('00000000-0000-0000-0000-00000000f002', 'Content Planning Flow', 'Plan content structure and outline', 'PLANNING', 2),
    ('00000000-0000-0000-0000-00000000f003', 'Script Generation Flow', 'Generate video scripts from research', 'CONTENT', 3),
    ('00000000-0000-0000-0000-00000000f004', 'Blog Generation Flow', 'Generate blog articles from research', 'CONTENT', 3),
    ('00000000-0000-0000-0000-00000000f005', 'Thumbnail Generation', 'Create video thumbnails', 'MEDIA', 4),
    ('00000000-0000-0000-0000-00000000f006', 'Image Generation Flow', 'Generate supporting images', 'MEDIA', 4),
    ('00000000-0000-0000-0000-00000000f007', 'Voice Generation Flow', 'Generate voiceover audio', 'MEDIA', 5),
    ('00000000-0000-0000-0000-00000000f008', 'Video Generation Flow', 'Assemble final video', 'MEDIA', 6),
    ('00000000-0000-0000-0000-00000000f009', 'Review Flow', 'Quality assurance review', 'REVIEW', 7),
    ('00000000-0000-0000-0000-00000000f010', 'Publishing Flow', 'Publish to configured destinations', 'PUBLISHING', 8);

-- Standard Agents
INSERT INTO agents (id, name, description, category, icon) VALUES
    ('00000000-0000-0000-0000-00000000a001', 'Research Agent', 'Gathers and validates information from multiple sources', 'RESEARCH', 'search'),
    ('00000000-0000-0000-0000-00000000a002', 'Planner Agent', 'Creates content plans and outlines', 'PLANNING', 'clipboard'),
    ('00000000-0000-0000-0000-00000000a003', 'Script Agent', 'Writes video scripts from research', 'CONTENT', 'file-text'),
    ('00000000-0000-0000-0000-00000000a004', 'Blog Agent', 'Generates blog articles and technical posts', 'CONTENT', 'book-open'),
    ('00000000-0000-0000-0000-00000000a005', 'Image Agent', 'Creates image generation prompts', 'MEDIA', 'image'),
    ('00000000-0000-0000-0000-00000000a006', 'Thumbnail Agent', 'Designs video thumbnails', 'MEDIA', 'camera'),
    ('00000000-0000-0000-0000-00000000a007', 'Voice Agent', 'Prepares voiceover scripts and narration', 'MEDIA', 'mic'),
    ('00000000-0000-0000-0000-00000000a008', 'Video Agent', 'Orchestrates video production', 'MEDIA', 'video'),
    ('00000000-0000-0000-0000-00000000a009', 'Review Agent', 'Evaluates quality and consistency', 'REVIEW', 'check-circle'),
    ('00000000-0000-0000-0000-00000000a010', 'Publishing Agent', 'Prepares and formats content for publication', 'PUBLISHING', 'upload');

-- Flow-Agent Assignments
INSERT INTO flow_agents (flow_id, agent_id) VALUES
    ('00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000a001'),
    ('00000000-0000-0000-0000-00000000f002', '00000000-0000-0000-0000-00000000a002'),
    ('00000000-0000-0000-0000-00000000f003', '00000000-0000-0000-0000-00000000a003'),
    ('00000000-0000-0000-0000-00000000f004', '00000000-0000-0000-0000-00000000a004'),
    ('00000000-0000-0000-0000-00000000f005', '00000000-0000-0000-0000-00000000a005'),
    ('00000000-0000-0000-0000-00000000f005', '00000000-0000-0000-0000-00000000a006'),
    ('00000000-0000-0000-0000-00000000f007', '00000000-0000-0000-0000-00000000a007'),
    ('00000000-0000-0000-0000-00000000f008', '00000000-0000-0000-0000-00000000a003'),
    ('00000000-0000-0000-0000-00000000f008', '00000000-0000-0000-0000-00000000a008'),
    ('00000000-0000-0000-0000-00000000f009', '00000000-0000-0000-0000-00000000a009'),
    ('00000000-0000-0000-0000-00000000f010', '00000000-0000-0000-0000-00000000a010');
