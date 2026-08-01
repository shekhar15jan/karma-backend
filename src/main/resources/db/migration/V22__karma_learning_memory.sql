-- KARMA AI learning & memory layer.
-- Stores per-user interaction events (chat turns + emitted actions + outcome) and
-- long-term learned memories (preferences / facts surfaced back into the prompt).

CREATE TABLE IF NOT EXISTS karma_interaction_events (
    id             UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id        UUID NOT NULL,
    user_message   TEXT,
    karma_reply    TEXT,
    action_type    VARCHAR(50),
    action_params  TEXT,
    action_status  VARCHAR(20) DEFAULT 'PENDING',
    outcome_note   TEXT,
    page           VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_karma_interaction_events_user_created
    ON karma_interaction_events (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS karma_user_memories (
    id         UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID NOT NULL,
    memory_key VARCHAR(100) NOT NULL,
    memory_value VARCHAR(500) NOT NULL,
    source     VARCHAR(20) DEFAULT 'AUTO',
    confidence INTEGER DEFAULT 50,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_karma_user_memory UNIQUE (user_id, memory_key)
);

CREATE INDEX IF NOT EXISTS idx_karma_user_memories_user
    ON karma_user_memories (user_id, confidence DESC);
