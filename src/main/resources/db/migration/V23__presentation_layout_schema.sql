-- ============================================================
-- V23: Append the Presentation Layout schema to the Video Script Generator skill.
-- This forces the LLM to use the new simplified layout for educational videos.
-- ============================================================

UPDATE public.skills
SET content = content || E'\n\n## Presentation Layout (Recommended for Educational Videos)\nWhen creating educational/training videos, use the "presentation" layout. This is a simplified, highly professional layout that splits the screen between a central diagram (optional HTML) and a list of explanatory content blocks that automatically highlight in sync with the voiceover.\n\n### Allowed Schema for Presentation Layout\n{\n  "title": "...",\n  "subtitle": "...",\n  "theme": "codeatcloud",\n  "layout": "presentation",\n  "components": [\n    { "id": "a", "type": "box", "label": "MAIN", "fill": "#eff6ff", "line": "#00E5FF" }\n  ],\n  "connections": [],\n  "content_blocks": [\n    { "heading": "Heading 1", "text": "Explanation text..." },\n    { "heading": "Heading 2", "text": "More explanation..." }\n  ],\n  "branding": {\n    "logo": "CODE AT CLOUD AI"\n  }\n}\n\nRules:\n- Set `layout` to "presentation".\n- Set `theme` to "codeatcloud" for the dark-mode neon aesthetic.\n- You MUST design a beautiful technical diagram using `components` and `connections` as usual. This diagram will be displayed on the left half of the screen.\n- You MUST ALSO provide `content_blocks` (array of objects with `heading` and `text`). This text will be displayed on the right half of the screen and will animate in sync with the voiceover!'
WHERE id = 'a0000000-0000-0000-0000-000000000001'
  AND content NOT LIKE '%Presentation Layout%';
