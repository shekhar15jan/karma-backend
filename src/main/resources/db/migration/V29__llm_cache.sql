CREATE TABLE IF NOT EXISTS public.llm_cache (
    hash_key VARCHAR(255) PRIMARY KEY,
    model VARCHAR(255),
    response_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
