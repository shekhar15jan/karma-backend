--
-- PostgreSQL database dump
--

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
-- SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


SET default_table_access_method = heap;

--
-- Name: agent_knowledge_packs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_knowledge_packs (
    agent_id uuid NOT NULL,
    knowledge_pack_id uuid NOT NULL
);


--
-- Name: agent_skills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_skills (
    agent_id uuid NOT NULL,
    skill_id uuid NOT NULL
);


--
-- Name: agents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    category character varying(50),
    icon character varying(50),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    default_prompt_id uuid,
    default_provider_id uuid,
    configuration jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    fallback_provider_id uuid
);


--
-- Name: artifact_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifact_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    artifact_id uuid NOT NULL,
    version integer NOT NULL,
    content_text text,
    content_type character varying(100),
    storage_location text,
    checksum character varying(64),
    size bigint DEFAULT 0,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_by uuid,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: artifacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artifacts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    mission_id uuid NOT NULL,
    execution_step_id uuid,
    name character varying(255) NOT NULL,
    artifact_type character varying(50) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    storage_location text,
    checksum character varying(64),
    size bigint DEFAULT 0,
    content_text text,
    content_type character varying(100) DEFAULT 'text/markdown'::character varying,
    metadata jsonb DEFAULT '{}'::jsonb,
    review_status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    publication_status character varying(50) DEFAULT 'UNPUBLISHED'::character varying NOT NULL,
    tags jsonb DEFAULT '[]'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    current_version integer DEFAULT 1 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_type character varying(100) NOT NULL,
    entity_type character varying(50),
    entity_id uuid,
    actor_id uuid,
    action character varying(50) NOT NULL,
    changes jsonb,
    metadata jsonb,
    correlation_id character varying(100),
    ip_address character varying(45),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: execution_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.execution_steps (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    execution_id uuid NOT NULL,
    flow_id uuid,
    agent_id uuid,
    step_order integer DEFAULT 0 NOT NULL,
    step_type character varying(50) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    input_data text,
    output_data text,
    prompt_used text,
    prompt_version integer,
    provider_id uuid,
    model_used character varying(100),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost numeric(10,4),
    latency_ms integer,
    error_message text,
    retry_count integer DEFAULT 0,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    review_status character varying(20) DEFAULT 'NONE'::character varying NOT NULL
);


--
-- Name: executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.executions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    mission_id uuid NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    duration_ms bigint,
    provider_id uuid,
    model_used character varying(100),
    total_cost numeric(10,4),
    error_message text,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    mode character varying(20) DEFAULT 'REVIEW'::character varying NOT NULL
);


--
-- Name: flow_agents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flow_agents (
    flow_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    step_order integer DEFAULT 0 NOT NULL
);


--
-- Name: flows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flows (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    category character varying(50),
    execution_order integer DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    parameters jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: karma_interaction_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.karma_interaction_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    user_message text,
    karma_reply text,
    action_type character varying(50),
    action_params text,
    action_status character varying(20) DEFAULT 'PENDING'::character varying,
    outcome_note text,
    page character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    deleted_at timestamp without time zone
);


--
-- Name: karma_user_memories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.karma_user_memories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    memory_key character varying(100) NOT NULL,
    memory_value character varying(500) NOT NULL,
    source character varying(20) DEFAULT 'AUTO'::character varying,
    confidence integer DEFAULT 50,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    deleted_at timestamp without time zone
);


--
-- Name: knowledge_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    knowledge_pack_id uuid NOT NULL,
    filename character varying(255) NOT NULL,
    content text NOT NULL,
    size bigint DEFAULT 0 NOT NULL,
    indexed boolean DEFAULT false NOT NULL,
    checksum character varying(64),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: knowledge_packs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_packs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    workspace_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    category character varying(50),
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    tags jsonb DEFAULT '[]'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: mission_flows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mission_flows (
    mission_id uuid NOT NULL,
    flow_id uuid NOT NULL,
    exec_order integer DEFAULT 0 NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL
);


--
-- Name: missions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.missions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    project_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    mission_type character varying(50) DEFAULT 'VIDEO'::character varying NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    priority character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    provider_id uuid,
    progress integer DEFAULT 0 NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    execution_plan jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    workspace_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    category character varying(50),
    tags jsonb DEFAULT '[]'::jsonb,
    brand_profile character varying(100),
    default_provider_id uuid,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: prompt_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prompt_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    prompt_id uuid NOT NULL,
    version integer NOT NULL,
    content text NOT NULL,
    variables jsonb DEFAULT '[]'::jsonb,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: prompts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prompts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    category character varying(50),
    content text NOT NULL,
    variables jsonb DEFAULT '[]'::jsonb,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    default_provider_id uuid,
    tags jsonb DEFAULT '[]'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    current_version integer DEFAULT 1 NOT NULL
);


--
-- Name: providers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.providers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    provider_type character varying(50) NOT NULL,
    api_endpoint character varying(500),
    encrypted_api_key bytea,
    models jsonb DEFAULT '[]'::jsonb,
    capabilities jsonb DEFAULT '[]'::jsonb,
    status character varying(50) DEFAULT 'DISCONNECTED'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    configuration jsonb DEFAULT '{}'::jsonb,
    health jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: publications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.publications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    artifact_id uuid NOT NULL,
    destination character varying(50) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    published_at timestamp with time zone,
    url character varying(500),
    error_message text,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    artifact_id uuid,
    reviewer_id uuid NOT NULL,
    decision character varying(50) NOT NULL,
    comments text,
    version_at_review integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    step_id uuid
);


--
-- Name: settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    workspace_id uuid,
    user_id uuid,
    settings_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: skill_knowledge_packs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.skill_knowledge_packs (
    skill_id uuid NOT NULL,
    knowledge_pack_id uuid NOT NULL
);


--
-- Name: skill_prompts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.skill_prompts (
    skill_id uuid NOT NULL,
    prompt_id uuid NOT NULL
);


--
-- Name: skills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.skills (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    category character varying(50),
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    validation_rules jsonb DEFAULT '{}'::jsonb,
    execution_parameters jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: source_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    mission_id uuid NOT NULL,
    filename character varying(255) NOT NULL,
    content text NOT NULL,
    format character varying(20) DEFAULT 'MARKDOWN'::character varying NOT NULL,
    size bigint DEFAULT 0 NOT NULL,
    checksum character varying(64),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    display_name character varying(100) NOT NULL,
    role character varying(50) DEFAULT 'USER'::character varying NOT NULL,
    avatar_url character varying(512),
    last_login_at timestamp with time zone,
    email_verified boolean DEFAULT false NOT NULL,
    preferences jsonb DEFAULT '{}'::jsonb,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: workflow_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_definitions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    project_id uuid,
    name character varying(255) NOT NULL,
    nodes jsonb DEFAULT '[]'::jsonb NOT NULL,
    edges jsonb DEFAULT '[]'::jsonb NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: workflow_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_runs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    workflow_id uuid,
    project_id uuid,
    workflow_name character varying(255) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    duration integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    execution_id uuid,
    mission_id uuid,
    flow_id uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: workspaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workspaces (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    owner_id uuid NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_knowledge_packs agent_knowledge_packs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_knowledge_packs
    ADD CONSTRAINT agent_knowledge_packs_pkey PRIMARY KEY (agent_id, knowledge_pack_id);


--
-- Name: agent_skills agent_skills_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_skills
    ADD CONSTRAINT agent_skills_pkey PRIMARY KEY (agent_id, skill_id);


--
-- Name: agents agents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agents_pkey PRIMARY KEY (id);


--
-- Name: artifact_versions artifact_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_versions
    ADD CONSTRAINT artifact_versions_pkey PRIMARY KEY (id);


--
-- Name: artifacts artifacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifacts
    ADD CONSTRAINT artifacts_pkey PRIMARY KEY (id);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: execution_steps execution_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execution_steps
    ADD CONSTRAINT execution_steps_pkey PRIMARY KEY (id);


--
-- Name: executions executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executions
    ADD CONSTRAINT executions_pkey PRIMARY KEY (id);


--
-- Name: flow_agents flow_agents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flow_agents
    ADD CONSTRAINT flow_agents_pkey PRIMARY KEY (flow_id, agent_id);


--
-- Name: flows flows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flows
    ADD CONSTRAINT flows_pkey PRIMARY KEY (id);


--
-- Name: karma_interaction_events karma_interaction_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.karma_interaction_events
    ADD CONSTRAINT karma_interaction_events_pkey PRIMARY KEY (id);


--
-- Name: karma_user_memories karma_user_memories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.karma_user_memories
    ADD CONSTRAINT karma_user_memories_pkey PRIMARY KEY (id);


--
-- Name: knowledge_documents knowledge_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_documents
    ADD CONSTRAINT knowledge_documents_pkey PRIMARY KEY (id);


--
-- Name: knowledge_packs knowledge_packs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_packs
    ADD CONSTRAINT knowledge_packs_pkey PRIMARY KEY (id);


--
-- Name: mission_flows mission_flows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mission_flows
    ADD CONSTRAINT mission_flows_pkey PRIMARY KEY (mission_id, flow_id);


--
-- Name: missions missions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.missions
    ADD CONSTRAINT missions_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: prompt_versions prompt_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prompt_versions
    ADD CONSTRAINT prompt_versions_pkey PRIMARY KEY (id);


--
-- Name: prompts prompts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prompts
    ADD CONSTRAINT prompts_pkey PRIMARY KEY (id);


--
-- Name: providers providers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.providers
    ADD CONSTRAINT providers_pkey PRIMARY KEY (id);


--
-- Name: publications publications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.publications
    ADD CONSTRAINT publications_pkey PRIMARY KEY (id);


--
-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);


--
-- Name: settings settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_pkey PRIMARY KEY (id);


--
-- Name: skill_knowledge_packs skill_knowledge_packs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_knowledge_packs
    ADD CONSTRAINT skill_knowledge_packs_pkey PRIMARY KEY (skill_id, knowledge_pack_id);


--
-- Name: skill_prompts skill_prompts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_prompts
    ADD CONSTRAINT skill_prompts_pkey PRIMARY KEY (skill_id, prompt_id);


--
-- Name: skills skills_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skills
    ADD CONSTRAINT skills_pkey PRIMARY KEY (id);


--
-- Name: source_documents source_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_documents
    ADD CONSTRAINT source_documents_pkey PRIMARY KEY (id);


--
-- Name: karma_user_memories uk_karma_user_memory; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.karma_user_memories
    ADD CONSTRAINT uk_karma_user_memory UNIQUE (user_id, memory_key);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: workflow_definitions workflow_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_definitions
    ADD CONSTRAINT workflow_definitions_pkey PRIMARY KEY (id);


--
-- Name: workflow_runs workflow_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_runs
    ADD CONSTRAINT workflow_runs_pkey PRIMARY KEY (id);


--
-- Name: workspaces workspaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_pkey PRIMARY KEY (id);


--
-- Name: idx_artifacts_mission_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifacts_mission_id ON public.artifacts USING btree (mission_id);


--
-- Name: idx_artifacts_publication_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifacts_publication_status ON public.artifacts USING btree (publication_status);


--
-- Name: idx_artifacts_review_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifacts_review_status ON public.artifacts USING btree (review_status);


--
-- Name: idx_artifacts_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_artifacts_type ON public.artifacts USING btree (artifact_type);


--
-- Name: idx_audit_events_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_events_actor ON public.audit_events USING btree (actor_id);


--
-- Name: idx_audit_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_events_created_at ON public.audit_events USING btree (created_at DESC);


--
-- Name: idx_audit_events_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_events_entity ON public.audit_events USING btree (entity_type, entity_id);


--
-- Name: idx_audit_events_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_events_type ON public.audit_events USING btree (event_type);


--
-- Name: idx_execution_steps_execution_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_execution_steps_execution_id ON public.execution_steps USING btree (execution_id);


--
-- Name: idx_execution_steps_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_execution_steps_status ON public.execution_steps USING btree (status);


--
-- Name: idx_executions_mission_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_executions_mission_id ON public.executions USING btree (mission_id);


--
-- Name: idx_executions_started_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_executions_started_at ON public.executions USING btree (started_at DESC);


--
-- Name: idx_executions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_executions_status ON public.executions USING btree (status);


--
-- Name: idx_karma_interaction_events_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_karma_interaction_events_user_created ON public.karma_interaction_events USING btree (user_id, created_at DESC);


--
-- Name: idx_karma_user_memories_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_karma_user_memories_user ON public.karma_user_memories USING btree (user_id, confidence DESC);


--
-- Name: idx_knowledge_documents_pack_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_documents_pack_id ON public.knowledge_documents USING btree (knowledge_pack_id);


--
-- Name: idx_knowledge_packs_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_packs_category ON public.knowledge_packs USING btree (category);


--
-- Name: idx_knowledge_packs_workspace_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_packs_workspace_id ON public.knowledge_packs USING btree (workspace_id);


--
-- Name: idx_missions_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_missions_created_at ON public.missions USING btree (created_at DESC);


--
-- Name: idx_missions_mission_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_missions_mission_type ON public.missions USING btree (mission_type);


--
-- Name: idx_missions_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_missions_project_id ON public.missions USING btree (project_id);


--
-- Name: idx_missions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_missions_status ON public.missions USING btree (status);


--
-- Name: idx_projects_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_status ON public.projects USING btree (status);


--
-- Name: idx_projects_workspace_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_workspace_id ON public.projects USING btree (workspace_id);


--
-- Name: idx_prompts_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prompts_category ON public.prompts USING btree (category);


--
-- Name: idx_prompts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prompts_status ON public.prompts USING btree (status);


--
-- Name: idx_publications_artifact_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_publications_artifact_id ON public.publications USING btree (artifact_id);


--
-- Name: idx_publications_destination; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_publications_destination ON public.publications USING btree (destination);


--
-- Name: idx_publications_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_publications_status ON public.publications USING btree (status);


--
-- Name: idx_reviews_artifact_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_artifact_id ON public.reviews USING btree (artifact_id);


--
-- Name: idx_reviews_decision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_decision ON public.reviews USING btree (decision);


--
-- Name: idx_reviews_step_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_step_id ON public.reviews USING btree (step_id);


--
-- Name: idx_source_documents_mission_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_documents_mission_id ON public.source_documents USING btree (mission_id);


--
-- Name: idx_workflow_definitions_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_definitions_project_id ON public.workflow_definitions USING btree (project_id);


--
-- Name: idx_workflow_runs_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_runs_project_id ON public.workflow_runs USING btree (project_id);


--
-- Name: idx_workflow_runs_started_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_runs_started_at ON public.workflow_runs USING btree (started_at DESC);


--
-- Name: idx_workflow_runs_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_runs_status ON public.workflow_runs USING btree (status);


--
-- Name: idx_workflow_runs_workflow_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_runs_workflow_id ON public.workflow_runs USING btree (workflow_id);


--
-- Name: idx_workspaces_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workspaces_owner_id ON public.workspaces USING btree (owner_id);


--
-- Name: uq_agents_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_agents_name ON public.agents USING btree (name) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_artifact_versions; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_artifact_versions ON public.artifact_versions USING btree (artifact_id, version);


--
-- Name: uq_flows_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_flows_name ON public.flows USING btree (name) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_knowledge_packs_name_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_knowledge_packs_name_workspace ON public.knowledge_packs USING btree (name, workspace_id) WHERE ((status)::text <> 'ARCHIVED'::text);


--
-- Name: uq_projects_workspace_active_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_projects_workspace_active_name ON public.projects USING btree (workspace_id, lower((name)::text)) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_prompt_versions; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_prompt_versions ON public.prompt_versions USING btree (prompt_id, version);


--
-- Name: uq_prompts_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_prompts_name ON public.prompts USING btree (name) WHERE ((status)::text <> 'ARCHIVED'::text);


--
-- Name: uq_providers_active_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_providers_active_name ON public.providers USING btree (name) WHERE ((deleted_at IS NULL) AND ((status)::text <> 'PUBLISHING'::text));


--
-- Name: uq_settings_workspace_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_settings_workspace_user ON public.settings USING btree (workspace_id, user_id);


--
-- Name: uq_skills_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_skills_name ON public.skills USING btree (name) WHERE ((status)::text <> 'ARCHIVED'::text);


--
-- Name: uq_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_email ON public.users USING btree (email) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_workspaces_name_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_workspaces_name_owner ON public.workspaces USING btree (name, owner_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_workspaces_owner_active_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_workspaces_owner_active_name ON public.workspaces USING btree (owner_id, lower((name)::text)) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: agent_knowledge_packs agent_knowledge_packs_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_knowledge_packs
    ADD CONSTRAINT agent_knowledge_packs_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.agents(id) ON DELETE CASCADE;


--
-- Name: agent_knowledge_packs agent_knowledge_packs_knowledge_pack_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_knowledge_packs
    ADD CONSTRAINT agent_knowledge_packs_knowledge_pack_id_fkey FOREIGN KEY (knowledge_pack_id) REFERENCES public.knowledge_packs(id);


--
-- Name: agent_skills agent_skills_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_skills
    ADD CONSTRAINT agent_skills_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.agents(id) ON DELETE CASCADE;


--
-- Name: agent_skills agent_skills_skill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_skills
    ADD CONSTRAINT agent_skills_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES public.skills(id);


--
-- Name: agents agents_fallback_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agents_fallback_provider_id_fkey FOREIGN KEY (fallback_provider_id) REFERENCES public.providers(id);


--
-- Name: artifact_versions artifact_versions_artifact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_versions
    ADD CONSTRAINT artifact_versions_artifact_id_fkey FOREIGN KEY (artifact_id) REFERENCES public.artifacts(id) ON DELETE CASCADE;


--
-- Name: artifact_versions artifact_versions_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifact_versions
    ADD CONSTRAINT artifact_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: artifacts artifacts_execution_step_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifacts
    ADD CONSTRAINT artifacts_execution_step_id_fkey FOREIGN KEY (execution_step_id) REFERENCES public.execution_steps(id);


--
-- Name: artifacts artifacts_mission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artifacts
    ADD CONSTRAINT artifacts_mission_id_fkey FOREIGN KEY (mission_id) REFERENCES public.missions(id) ON DELETE CASCADE;


--
-- Name: audit_events audit_events_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(id);


--
-- Name: execution_steps execution_steps_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execution_steps
    ADD CONSTRAINT execution_steps_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.agents(id);


--
-- Name: execution_steps execution_steps_execution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execution_steps
    ADD CONSTRAINT execution_steps_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id) ON DELETE CASCADE;


--
-- Name: execution_steps execution_steps_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execution_steps
    ADD CONSTRAINT execution_steps_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES public.flows(id);


--
-- Name: executions executions_mission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executions
    ADD CONSTRAINT executions_mission_id_fkey FOREIGN KEY (mission_id) REFERENCES public.missions(id) ON DELETE CASCADE;


--
-- Name: flow_agents flow_agents_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flow_agents
    ADD CONSTRAINT flow_agents_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.agents(id);


--
-- Name: flow_agents flow_agents_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flow_agents
    ADD CONSTRAINT flow_agents_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES public.flows(id) ON DELETE CASCADE;


--
-- Name: knowledge_documents knowledge_documents_knowledge_pack_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_documents
    ADD CONSTRAINT knowledge_documents_knowledge_pack_id_fkey FOREIGN KEY (knowledge_pack_id) REFERENCES public.knowledge_packs(id) ON DELETE CASCADE;


--
-- Name: knowledge_packs knowledge_packs_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_packs
    ADD CONSTRAINT knowledge_packs_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id);


--
-- Name: mission_flows mission_flows_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mission_flows
    ADD CONSTRAINT mission_flows_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES public.flows(id);


--
-- Name: mission_flows mission_flows_mission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mission_flows
    ADD CONSTRAINT mission_flows_mission_id_fkey FOREIGN KEY (mission_id) REFERENCES public.missions(id) ON DELETE CASCADE;


--
-- Name: missions missions_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.missions
    ADD CONSTRAINT missions_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: projects projects_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id);


--
-- Name: prompt_versions prompt_versions_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prompt_versions
    ADD CONSTRAINT prompt_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: prompt_versions prompt_versions_prompt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prompt_versions
    ADD CONSTRAINT prompt_versions_prompt_id_fkey FOREIGN KEY (prompt_id) REFERENCES public.prompts(id) ON DELETE CASCADE;


--
-- Name: publications publications_artifact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.publications
    ADD CONSTRAINT publications_artifact_id_fkey FOREIGN KEY (artifact_id) REFERENCES public.artifacts(id) ON DELETE CASCADE;


--
-- Name: reviews reviews_artifact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_artifact_id_fkey FOREIGN KEY (artifact_id) REFERENCES public.artifacts(id) ON DELETE CASCADE;


--
-- Name: reviews reviews_reviewer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES public.users(id);


--
-- Name: reviews reviews_step_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_step_id_fkey FOREIGN KEY (step_id) REFERENCES public.execution_steps(id) ON DELETE CASCADE;


--
-- Name: settings settings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: settings settings_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id);


--
-- Name: skill_knowledge_packs skill_knowledge_packs_knowledge_pack_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_knowledge_packs
    ADD CONSTRAINT skill_knowledge_packs_knowledge_pack_id_fkey FOREIGN KEY (knowledge_pack_id) REFERENCES public.knowledge_packs(id);


--
-- Name: skill_knowledge_packs skill_knowledge_packs_skill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_knowledge_packs
    ADD CONSTRAINT skill_knowledge_packs_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES public.skills(id) ON DELETE CASCADE;


--
-- Name: skill_prompts skill_prompts_prompt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_prompts
    ADD CONSTRAINT skill_prompts_prompt_id_fkey FOREIGN KEY (prompt_id) REFERENCES public.prompts(id);


--
-- Name: skill_prompts skill_prompts_skill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_prompts
    ADD CONSTRAINT skill_prompts_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES public.skills(id) ON DELETE CASCADE;


--
-- Name: source_documents source_documents_mission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_documents
    ADD CONSTRAINT source_documents_mission_id_fkey FOREIGN KEY (mission_id) REFERENCES public.missions(id) ON DELETE CASCADE;


--
-- Name: workflow_definitions workflow_definitions_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_definitions
    ADD CONSTRAINT workflow_definitions_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: workflow_runs workflow_runs_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_runs
    ADD CONSTRAINT workflow_runs_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: workflow_runs workflow_runs_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_runs
    ADD CONSTRAINT workflow_runs_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.workflow_definitions(id);


--
-- Name: workspaces workspaces_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

