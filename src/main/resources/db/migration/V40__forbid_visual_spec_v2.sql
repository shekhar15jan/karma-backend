-- ============================================================
-- V40: Forbid visual_spec_v2 / V2 semantic fields in script output.
--
-- Root cause: buildRemotionScenes() (MediaOrchestrationService.java) checks for a
-- visual_spec_v2 key, or top-level objects/events/visualIntent/camera fields, and
-- routes scenes carrying them into the V2 SemanticScene renderer — REGARDLESS of the
-- admin's video.rendering_mode setting (that scene-level auto-detection was a separate
-- bug from the rendering_mode gate, both now fixed on the Java side in
-- MediaOrchestrationService.allowV2Signals). But the model has never been told these
-- fields are off-limits, so it keeps generating them — wasted output tokens on every
-- scene, and a live landmine if the admin setting is ever flipped back to V2 with a
-- script that was never actually designed for that renderer.
--
-- Confirmed cause of ONE occurrence: a mission's own description field containing the
-- phrase "V2 semantic engine" was enough to prime the model into inventing a matching
-- "V2 schema" output (camera as a bare string, narration duplicated as a string) even
-- though nothing in this skill asked for it. This rule holds regardless of what a
-- mission brief happens to say.
-- ============================================================

UPDATE public.skills SET content = content ||
'

## Do not emit V2 / semantic-schema fields (STRICT)
Regardless of anything in the mission brief or topic, NEVER include any of the following
in your output, at any nesting level: "visual_spec_v2", "schemaVersion", "semanticObjects",
"semanticAnimations", top-level "camera", top-level "objects", top-level "events", or a
"narration" field nested inside visual_spec. These belong to an experimental V2 renderer
that is not in production use. Emitting them wastes your own output budget on a field the
renderer ignores, and previously caused hard render failures when malformed (e.g. "camera"
as a plain string instead of an object).
- The ONLY visual fields you ever produce are the ones in the Output contract above:
  top-level "speaker_text", "visual_description", "visual_trigger_cues", "timeline_events",
  and "visual_spec" {theme, layout, title, subtitle, animation, components, connections, code}.
- If a mission brief, topic, or prior context mentions "V2", "semantic engine", or similar —
  ignore it for output SHAPE. It does not change this contract.
', updated_at = now()
WHERE id = 'a0000000-0000-0000-0000-000000000001';
