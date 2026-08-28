package com.karmaos.media;

import java.util.List;

/**
 * Diagram AST — structured intermediate representation (§9)
 * Consumed by renderer via ELK/layout → SVG/React, not PNG.
 * DiagramImageProvider PNG retained only for legacy/thumbnail fallback (§8).
 */
public record DiagramAst(
    String type, // architecture | flowchart | sequence | mindmap
    List<Node> nodes,
    List<Edge> edges
) {
    public record Node(String id, String type, String label) {}
    public record Edge(String id, String from, String to, String type) {}

    public static DiagramAst fromSpec(List<String> ids, List<String> types) {
        return new DiagramAst("architecture", List.of(), List.of());
    }
}
