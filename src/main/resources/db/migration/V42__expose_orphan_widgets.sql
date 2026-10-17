-- ============================================================
-- V42: Expose the 30 implemented-but-invisible widgets to the script-writer.
--
-- Verified via the renderer's widget catalogue (src/catalogue/widgets.ts) against
-- the vocabulary list below: 54 widgets are implemented and render correctly, but
-- only 26 type names were ever listed in this skill. Zero renderer work — every one
-- of these already works, the model was just never told it could ask for them. No
-- fallback registry entry exists for these types either, so requesting one that
-- isn't listed here still renders nothing; this migration is what actually turns
-- them on.
-- ============================================================

UPDATE public.skills SET content = content ||
'

## Additional component types (STRICT — all of these already render correctly)
These extend the Component Vocabulary above. Use them instead of forcing content into
"card" or "architecture-diagram" when one of these is the more precise fit.

### Sequence diagrams
For "who calls whom, in what order" — a request lifecycle, an async pipeline, a
handshake. Prefer this over architecture-diagram when ORDER and TIMING are the point,
not topology.
- "uml-lifeline": one vertical participant column. Pass label (participant name).
- "uml-arrow": one message between two lifelines. Pass label (the call/return) and
  data.direction ("right" | "left").
- "uml-syncbar": the activation bar showing a lifeline is actively processing.

### UML structure diagrams
For class relationships, actors, and system boundaries.
- "uml-class-node": pass label (class name), data.attributes[], data.methods[].
- "uml-actor-node", "uml-usecase-node": actor and use-case ellipse.
- "uml-decision": a branch point.
- "uml-component", "uml-package", "uml-database", "uml-note", "uml-cloud",
  "uml-deployment", "uml-interface": standard UML shapes, pass label.

### Flowcharts
For decision-driven processes with real branches — prefer this over
architecture-diagram when the diagram has yes/no or if/else branching, not just a
pipeline.
- "flow-terminal": start/end oval, pass label.
- "flow-process": a rectangular step, pass label.
- "flow-io": a parallelogram input/output step, pass label.
- "flow-document": a document-shaped step, pass label.

### Technology brand marks
Use these whenever a scene names a real technology, service, or vendor — they render
the actual brand logo, not a generic icon. Prefer this over a plain "card" whenever
the subject IS a named technology.
- "tech-node": a labelled node carrying a real brand mark. Pass data.tech (a slug —
  "java", "spring-boot", "kafka", "postgres", "kubernetes", "docker", "aws", "gcp",
  "azure", "react", "python", "openai", etc. — loose names like "K8s" also resolve).
  Optionally label and sublabel.
- "tech-icon": the brand mark alone, no label — for a small inline reference.
- "tech-stack": a row of several tech-node marks in one component. Pass data.items
  as an array of tech slugs, e.g. ["java","spring-boot","kafka","postgres"].
- data.tech also works as an EXTRA field on any existing node type (api-gateway,
  microservice-node, docker-node, etc.) to give that node a real logo instead of a
  generic shape — e.g. a microservice-node with data.tech: "spring-boot".

### Structure primitives
- "container": a labelled group wrapping other components. Pass label, items[].
- "grid": an even grid of child components. Pass items[], data.cols.
- "architecture-pipeline": stages with arrows auto-inserted between them — a
  one-component pipeline diagram. Pass data.stages[] (each stage is itself a
  component).
- "connector": a standalone arrow between two already-placed components. Pass
  data.direction, data.lineType.

### Misc
- "table": real structured columns and rows. Pass columns[] (each column has header
  and cells[]). Prefer this over bullet-list when comparing 3+ items across the same
  criteria.
- "badge": a short status/tag chip. Pass label, data.tone.
- "roadmap-component" (aliases: "timeline", "roadmap", "learning-path", "sprint"):
  ordered steps with completed/active/pending status — the roadmap-timeline layout''s
  native widget, usable inside other layouts too via items[].
', updated_at = now()
WHERE id = 'a0000000-0000-0000-0000-000000000001';
