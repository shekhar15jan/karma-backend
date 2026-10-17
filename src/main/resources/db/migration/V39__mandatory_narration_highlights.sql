-- ============================================================
-- V39: Make narration-synced highlights MANDATORY for any scene
-- with multiple referenceable components (diagrams, roadmaps,
-- dashboards, grids), not just an available option. This is the
-- "lightboard" cue: the viewer should always be able to tell which
-- element the narration is talking about right now, the same way a
-- presenter's hand would point at it.
-- ============================================================

UPDATE public.skills SET content = content ||
'

## Narration-synced highlights (MANDATORY, not optional)
This is what makes a faceless video feel presented instead of just displayed - the AI-pipeline
equivalent of a presenter pointing at the board. It is required, not a nice-to-have:
- ANY scene whose layout has 3 or more referenceable components (architecture-diagram, roadmap-timeline,
  dashboard, infographic, bento, feature-grid, two-column, comparison, big-number with caption) MUST include
  a non-empty "highlights" array inside "animation".
- Every distinct thing the narration names or explains MUST have a matching highlight entry: { "id": <component id>,
  "at": <seconds into the scene when the narration starts talking about it>, "style": "glow" }. Walk through
  your own "speaker_text" sentence by sentence and time each highlight to when that sentence begins, not when
  the scene starts.
- Order highlights chronologically by "at" and space them across the FULL narration duration of the scene -
  do not front-load every highlight into the first 2 seconds and leave the rest of the scene static.
- For "architecture-diagram" scenes, also highlight the two endpoints of a connection right as the narration
  describes that hop (e.g. narration says "the gateway forwards the request to the order service" -> highlight
  "gateway" then "order-service" a beat later), mirroring how the request would actually move.
- A scene with a diagram or list and zero highlights is a rejected scene: it reads as decoration, not explanation.
', updated_at = now()
WHERE id = 'a0000000-0000-0000-0000-000000000001';
