package com.karmaos.workflow;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parses a Flow Designer canvas (nodes + edges) into a deterministic,
 * topologically-ordered execution sequence.
 *
 * <p>The design is stored as the {@code "design"} key inside the Flow's JSONB
 * {@code parameters}. All edges define execution order; edges flagged
 * {@code handoff=false} are order-only and do not pass data to the target.
 */
public final class DesignPlan {

    public static final String KEY_DESIGN = "design";

    public static final class Node {
        public final String nodeId;
        public final UUID agentId;
        public final String stepKind;
        public final List<String> upstreams = new ArrayList<>();

        Node(String nodeId, UUID agentId, String stepKind) {
            this.nodeId = nodeId;
            this.agentId = agentId;
            this.stepKind = stepKind;
        }
    }

    private DesignPlan() {
    }

    /**
     * Topologically sorts the canvas nodes using its edges (Kahn's algorithm).
     *
     * @throws ApiException if the graph contains a cycle
     */
    public static List<Node> resolve(List<Map<String, Object>> nodes, List<Map<String, String>> edges) {
        List<Node> result = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return result;
        }

        Map<String, Node> byId = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            Object idObj = node.get("id");
            if (idObj == null) {
                continue;
            }
            String id = idObj.toString();
            UUID agentId = null;
            Object agentIdObj = node.get("agentId");
            if (agentIdObj != null) {
                try {
                    agentId = UUID.fromString(agentIdObj.toString());
                } catch (Exception ignored) {
                }
            }
            String stepKind = node.get("stepKind") != null ? node.get("stepKind").toString() : null;
            byId.put(id, new Node(id, agentId, stepKind));
        }
        if (byId.isEmpty()) {
            return result;
        }

        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : byId.keySet()) {
            inDegree.put(id, 0);
        }
        if (edges != null) {
            for (Map<String, String> edge : edges) {
                String src = edge.get("source");
                String tgt = edge.get("target");
                if (src == null || tgt == null || !byId.containsKey(tgt)) {
                    continue;
                }
                inDegree.merge(tgt, 1, Integer::sum);
                boolean handoff = !"false".equalsIgnoreCase(edge.get("handoff"));
                if (handoff && byId.containsKey(src)) {
                    byId.get(tgt).upstreams.add(src);
                }
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        Set<String> scheduled = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!scheduled.add(id)) {
                continue;
            }
            result.add(byId.get(id));
            for (Map<String, String> edge : edges == null ? Collections.<Map<String, String>>emptyList() : edges) {
                if (!id.equals(edge.get("source"))) {
                    continue;
                }
                String tgt = edge.get("target");
                if (tgt == null || !byId.containsKey(tgt)) {
                    continue;
                }
                int deg = inDegree.get(tgt) - 1;
                inDegree.put(tgt, deg);
                if (deg == 0) {
                    queue.add(tgt);
                }
            }
        }

        if (result.size() != byId.size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                "Workflow contains a cycle; connect nodes without circular references");
        }
        return result;
    }
}
