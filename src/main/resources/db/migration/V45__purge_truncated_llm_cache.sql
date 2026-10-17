-- Gemini thinking models used most of maxOutputTokens for reasoning, so many cached
-- responses are answers cut off mid-JSON (finishReason MAX_TOKENS) that were stored
-- as successes. The cache is safe to rebuild; clear it so those are not replayed.
DELETE FROM llm_cache;
