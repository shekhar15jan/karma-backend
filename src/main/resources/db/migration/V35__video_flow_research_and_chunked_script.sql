-- ============================================================
-- 35a. Default Video Flow: add Research Agent as step 1
-- Result: Research (1) -> Script (2) -> Voice (3) -> Video (4)
-- ============================================================

UPDATE public.flow_agents SET step_order = 2
WHERE flow_id = 'f0000000-0000-0000-0000-000000000001'
  AND agent_id = '00000000-0000-0000-0000-00000000a003';

UPDATE public.flow_agents SET step_order = 3
WHERE flow_id = 'f0000000-0000-0000-0000-000000000001'
  AND agent_id = '00000000-0000-0000-0000-00000000a007';

UPDATE public.flow_agents SET step_order = 4
WHERE flow_id = 'f0000000-0000-0000-0000-000000000001'
  AND agent_id = '00000000-0000-0000-0000-00000000a008';

INSERT INTO public.flow_agents (flow_id, agent_id, step_order) VALUES
('f0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000a001', 1)
ON CONFLICT (flow_id, agent_id) DO UPDATE SET step_order = EXCLUDED.step_order;

-- ============================================================
-- 35b. Research Agent skill: produce structured raw content
-- that the chunked Script Agent can divide into scenes.
-- ============================================================

UPDATE public.skills SET
  name = 'Video Content Research & Raw Material Synthesis',
  description = 'Gathers, cross-references and structures raw source material into ordered content sections for a video script pipeline.',
  content = 'You are the research and intelligence analyst for a professional YouTube technical channel (@CodeAtCloudAI). Your single job: turn the mission topic, brief, and any source documents into COMPREHENSIVE, well-structured RAW CONTENT that a downstream script writer will divide into short video scenes.

## Output contract
Return ONE valid JSON object (raw JSON, no markdown fences, no commentary) with exactly:
{
  "title": "short, SEO-friendly working title of the video",
  "sections": [
    {
      "section_number": 1,
      "heading": "Short section heading",
      "purpose": "hook | concept | example | walkthrough | comparison | summary",
      "content": "2-6 sentences of factual raw material. Include concrete facts, numbers, stats, APIs, class names, code snippets (inline or short fenced blocks), and precise technical detail."
    }
  ]
}

## Raw material rules (STRICT)
- Write FACTUAL, TRACEABLE raw material. No fluff, no filler, no promotional language.
- Every section must contain the exact technical details the script needs: method names, parameter semantics, thread states, error conditions, real numbers, benchmarks, and any code that will appear on screen.
- For code topics, include the exact Java/JS/other snippets (short) so the script writer can quote them verbatim.
- Cover enough ground that the final video reaches the target duration. The script writer will split this material into ~5-10 second scenes, so you need DENSITY, not brevity.
- If source documents are provided, synthesize faithfully from them and flag anything you could not verify with "UNVERIFIED:".
- If no source documents are provided, write authoritative raw material from your own knowledge on the mission topic.
- STRICT CONTENT SAFETY RULES: family-friendly, age-appropriate. ABSOLUTELY FORBIDDEN: violence, war, weapons, guns, blood, gore, injury, death, killing, fear, horror, nudity, adult/sexual content, drugs, alcohol, smoking, or anything inappropriate.

## Structure guidance
- Section 1 should be the HOOK material: a concrete problem or surprising fact the video will open with.
- Middle sections should follow ONE narrative arc (e.g. banking system: race condition -> visibility -> locking -> pools -> hazards), each building on the last.
- The final section should be the SUMMARY material: what the video solved, plus a call to action.
- Return ONLY the raw JSON object. Do NOT include markdown headings, bullet lists, explanatory prose, chain-of-thought reasoning, or any text outside the JSON.' ,
  validation_rules = '{}',
  updated_at = now()
WHERE id = 'a0000000-0000-0000-0000-000000000006';

-- ============================================================
-- 35c. Script Agent: enable iterative chunked generation.
-- ============================================================

UPDATE public.agents SET
  configuration = '{"model": "gemini-3.5-flash", "memoryMode": "OFF", "temperature": 0.8, "iterative": true, "scenesPerChunk": 15}',
  updated_at = now()
WHERE id = '00000000-0000-0000-0000-00000000a003';

-- 35c2. Research Agent: explicit working model (provider default 404s).
UPDATE public.agents SET
  configuration = '{"model": "gemini-3.5-flash", "memoryMode": "OFF", "temperature": 0.7}',
  updated_at = now()
WHERE id = '00000000-0000-0000-0000-00000000a001';

-- ============================================================
-- 35d. Short-scene settings: 5-10s scenes, ~20 words each.
-- ============================================================

UPDATE public.settings SET
  settings_data = settings_data
    || '{"video.words_per_scene": 20, "video.min_scenes": 1, "video.max_scenes": 120}',
  updated_at = now()
WHERE user_id IS NULL;