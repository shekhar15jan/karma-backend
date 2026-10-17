-- ============================================================
-- V41: On-screen text must never repeat narration verbatim.
--
-- Found via a real generated scene: a paragraph component's data.body was the exact
-- first four sentences of that scene's own speaker_text — the voice reading a
-- sentence while the screen printed the same sentence in full. Every reference
-- channel this project is positioning against (ByteByteGo, Fireship) treats on-screen
-- text as condensed labels/diagrams that ACCOMPANY narration, never a transcript of
-- it. V34 already implies this ("visual_description: what appears on screen in
-- words" as a SEPARATE field from speaker_text) but never states the rule directly,
-- so the model had no explicit reason not to just copy speaker_text into a component.
-- ============================================================

UPDATE public.skills SET content = content ||
'

## On-screen text is never a transcript of the narration (STRICT)
The voice carries full sentences. The screen carries labels, numbers, code, and
diagrams. Never put a full sentence or multiple sentences of speaker_text onto the
screen as a paragraph/heading/bullet component — if the narration says it, do not
also print it.
- Condense: a component''s label/body is a short noun phrase or fragment (2-6 words),
  not a restatement of what is being said. "Silent Outage Trigger" and "10 threads,
  5 increments" are on-screen text. "Imagine ten threads racing to increment a
  counter, but only five increments survive" is narration ONLY — it must never also
  appear verbatim in a component''s label or data.body.
- The one exception: a single short phrase under ~8 words MAY echo the narration
  when it is the scene''s hook/punchline being landed as a title or big-number
  caption — never a full sentence, never more than one such echo per scene.
- This applies to every text-bearing field: heading.label, paragraph.data.body,
  bullet-list items, card labels/bodies, and quote text.
', updated_at = now()
WHERE id = 'a0000000-0000-0000-0000-000000000001';
