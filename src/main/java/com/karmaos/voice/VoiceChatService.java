package com.karmaos.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.agent.AgentResponse;
import com.karmaos.agent.AgentService;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.knowledge.KarmaDocService;
import com.karmaos.knowledge.KarmaDocService.DocEntry;
import com.karmaos.llm.LLMProvider;
import com.karmaos.llm.LLMProviderFactory;
import com.karmaos.llm.LLMRequest;
import com.karmaos.llm.LLMResponse;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.report.ReportService;
import com.karmaos.system.SystemInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VoiceChatService {

    private static final Logger log = LoggerFactory.getLogger(VoiceChatService.class);
    private static final String LLM_MODEL = "deepseek-v4-flash-free";

    private final AgentService agentService;
    private final ProviderRepository providerRepository;
    private final ReportService reportService;
    private final MissionRepository missionRepository;
    private final ExecutionRepository executionRepository;
    private final SystemInfoService systemInfoService;
    private final LLMProviderFactory llmProviderFactory;
    private final ObjectMapper objectMapper;
    private final KarmaDocService karmaDocService;

    public VoiceChatService(AgentService agentService, ProviderRepository providerRepository,
                            ReportService reportService, MissionRepository missionRepository,
                            ExecutionRepository executionRepository, SystemInfoService systemInfoService,
                            LLMProviderFactory llmProviderFactory, ObjectMapper objectMapper,
                            KarmaDocService karmaDocService) {
        this.agentService = agentService;
        this.providerRepository = providerRepository;
        this.reportService = reportService;
        this.missionRepository = missionRepository;
        this.executionRepository = executionRepository;
        this.systemInfoService = systemInfoService;
        this.llmProviderFactory = llmProviderFactory;
        this.objectMapper = objectMapper;
        this.karmaDocService = karmaDocService;
    }

    public VoiceChatResponse chat(VoiceChatRequest request) {
        String text = request.getText() != null ? request.getText().trim().toLowerCase() : "";
        if (text.isEmpty()) {
            return VoiceChatResponse.staticReply("I didn't catch that. Could you please repeat?", "chat", "");
        }

        VoiceChatResponse staticMatch = tryStaticFaq(text);
        if (staticMatch != null) {
            return staticMatch;
        }

        return callLlm(request);
    }

    // ========== STATIC FAQ (instant replies, no LLM call) ==========

    private VoiceChatResponse tryStaticFaq(String text) {
        for (FaqEntry entry : FAQ) {
            for (String keyword : entry.keywords) {
                if (text.contains(keyword)) {
                    return VoiceChatResponse.staticReply(entry.reply, entry.intent, entry.target);
                }
            }
        }
        return null;
    }

    private static final List<FaqEntry> FAQ = List.of(

        // ---- EXPLAIN / WHAT IS ----
        new FaqEntry(List.of("explain dashboard", "what is dashboard", "tell me about dashboard", "dashboard overview"),
                "The Dashboard is your command center. It shows the KARMA CORE hub in the center, surrounded by your agents in orbit. "
                        + "You'll see system status, recent activity, agent health, and quick stats at a glance. "
                        + "Use it to monitor everything happening across KARMA OS.", "chat", ""),

        new FaqEntry(List.of("explain agents", "what are agents", "tell me about agents", "agents overview"),
                "Agents are AI workers that handle specific tasks. Each agent has a specialty — Writing, Image, Video, Research, Planning, and more. "
                        + "You can view their status, assign them to missions, and configure their providers. "
                        + "Go to the Agents page to manage them.", "navigate", "agents"),

        new FaqEntry(List.of("explain workflow", "what is workflow", "tell me about workflow", "workflow designer", "explain workflow designer"),
                "The Workflow Designer lets you visually build multi-step automation pipelines. "
                        + "Drag and drop nodes (Generate Image, Transcribe Audio, Run Script) and connect them in sequence. "
                        + "Each step can use a different agent or provider. It's like a visual programming canvas for AI tasks.", "navigate", "workflow"),

        new FaqEntry(List.of("explain provider", "what is provider", "tell me about providers", "providers overview", "explain provider center"),
                "Providers are the external AI services KARMA OS connects to — OpenAI, Google Gemini, OpenCode, Stability AI, and more. "
                        + "Each provider has a type, API endpoint, and status. You can add, edit, or remove them in the Provider Center. "
                        + "Without providers, agents have no AI backend to work with.", "navigate", "providers"),

        new FaqEntry(List.of("explain mission", "what is mission", "tell me about missions", "mission control", "explain mission control"),
                "Mission Control is where you manage AI tasks from start to finish. Each mission has a type (Generate, Analyze, Review, Publish, Transcribe), "
                        + "a priority level, progress tracking, and execution history. "
                        + "You can trigger, pause, or cancel missions and view detailed execution logs.", "navigate", "missions"),

        new FaqEntry(List.of("explain system", "what is karma os", "tell me about karma os", "about this system"),
                "KARMA OS is your AI orchestration platform. It connects multiple AI agents, providers, and tools into a unified workspace. "
                        + "You can create content (text, images, video, audio), automate workflows, manage providers, "
                        + "review and publish results — all through voice or chat commands. "
                        + "Think of it as your personal AI production studio.", "chat", ""),

        // ---- HELP / CAPABILITIES ----
        new FaqEntry(List.of("what can you do", "what can you help", "capabilities", "what are your capabilities", "help me"),
                "I can help you with:\n"
                        + "1. Navigate anywhere — say \"go to dashboard\" or \"take me to agents\"\n"
                        + "2. Explain any feature — ask \"explain workflow designer\" or \"what are agents\"\n"
                        + "3. Check system health — ask \"how is the system\" or \"are all agents online\"\n"
                        + "4. Monitor missions — ask \"what missions are running\" or \"show recent activity\"\n"
                        + "5. Manage providers — ask \"are all providers connected\"\n"
                        + "What would you like help with?", "chat", ""),

        new FaqEntry(List.of("hello", "hi", "hey", "good morning", "good evening"),
                "Hello! I'm Karma AI, your guide to KARMA OS. How can I help you today?", "chat", ""),

        new FaqEntry(List.of("thank", "thanks", "appreciate"),
                "You're welcome! Let me know if you need anything else.", "chat", ""),

        // ---- NAVIGATION ----
        new FaqEntry(List.of("go to dashboard", "take me to dashboard", "navigate to dashboard", "open dashboard", "show dashboard"),
                "Opening the Dashboard for you.", "navigate", "dashboard"),

        new FaqEntry(List.of("go to agents", "take me to agents", "navigate to agents", "open agents", "show agents", "agent page"),
                "Taking you to the Agents page.", "navigate", "agents"),

        new FaqEntry(List.of("go to workflow", "take me to workflow", "navigate to workflow", "open workflow designer", "show workflow"),
                "Opening the Workflow Designer.", "navigate", "workflow"),

        new FaqEntry(List.of("go to providers", "take me to providers", "navigate to providers", "open provider center", "show providers"),
                "Opening the Provider Center.", "navigate", "providers"),

        new FaqEntry(List.of("go to missions", "take me to missions", "navigate to missions", "open mission control", "show missions"),
                "Opening Mission Control.", "navigate", "missions"),

        new FaqEntry(List.of("go to calendar", "take me to calendar", "navigate to calendar", "open calendar", "show calendar"),
                "Opening the Calendar.", "navigate", "calendar"),

        new FaqEntry(List.of("go to settings", "take me to settings", "navigate to settings", "open settings", "show settings", "administration"),
                "Opening Administration.", "navigate", "administration"),

        new FaqEntry(List.of("go to reviews", "take me to reviews", "navigate to reviews", "open reviews"),
                "Opening the Reviews page.", "navigate", "reviews"),

        new FaqEntry(List.of("go to publishing", "take me to publishing", "navigate to publishing", "open publishing"),
                "Opening the Publishing page.", "navigate", "publishing"),

        new FaqEntry(List.of("go to knowledge", "take me to knowledge", "navigate to knowledge", "open knowledge"),
                "Opening the Knowledge page.", "navigate", "knowledge"),

        new FaqEntry(List.of("go to marketplace", "take me to marketplace", "navigate to marketplace", "open marketplace", "show marketplace"),
                "Opening the Marketplace.", "navigate", "marketplace"),

        new FaqEntry(List.of("go to prompt studio", "take me to prompt studio", "navigate to prompt studio", "open prompt studio", "show prompt studio"),
                "Opening Prompt Studio.", "navigate", "prompt-studio")
    );

    private record FaqEntry(List<String> keywords, String reply, String intent, String target) {}

    // ========== LLM FALLBACK (for dynamic system queries) ==========

    private VoiceChatResponse callLlm(VoiceChatRequest request) {
        try {
            String systemPrompt = buildSystemPrompt(request);
            LLMRequest llmRequest = new LLMRequest(systemPrompt, request.getText());
            llmRequest.setModel(LLM_MODEL);
            llmRequest.setTemperature(0.5);
            llmRequest.setMaxTokens(500);

            LLMProvider llmProvider = resolveLlmProvider();
            if (llmProvider == null) {
                return VoiceChatResponse.staticReply(
                        "I'm having trouble connecting to my AI backend. Please check your provider settings.", "chat", "");
            }

            LLMResponse llmResponse = llmProvider.chat(llmRequest);
            if (!llmResponse.isSuccess()) {
                log.warn("LLM call failed: {}", llmResponse.getErrorMessage());
                return VoiceChatResponse.staticReply(
                        "I couldn't process that right now. Please try again.", "chat", "");
            }

            return parseLlmResponse(llmResponse.getContent());

        } catch (Exception e) {
            log.error("VoiceChatService error", e);
            return VoiceChatResponse.staticReply(
                    "Something went wrong. Please try again.", "chat", "");
        }
    }

    private LLMProvider resolveLlmProvider() {
        List<Provider> connected = providerRepository.findByStatus("CONNECTED");
        for (Provider p : connected) {
            if ("OPENCODE".equalsIgnoreCase(p.getProviderType())) {
                return llmProviderFactory.getProvider(
                        p.getProviderType(),
                        p.getEncryptedApiKey() != null ? new String(p.getEncryptedApiKey()) : "",
                        p.getApiEndpoint());
            }
        }
        for (Provider p : connected) {
            try {
                return llmProviderFactory.getProvider(
                        p.getProviderType(),
                        p.getEncryptedApiKey() != null ? new String(p.getEncryptedApiKey()) : "",
                        p.getApiEndpoint());
            } catch (Exception e) {
                log.warn("Failed to create provider {}: {}", p.getProviderType(), e.getMessage());
            }
        }
        return null;
    }

    private String buildSystemPrompt(VoiceChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Karma AI — the master intelligence and guide for the entire KARMA OS platform. ");
        sb.append("You have complete knowledge of the system's architecture, all its agents, providers, skills, flows, and documentation. ");
        sb.append("You answer questions accurately, help users navigate, diagnose system health, and explain any part of KARMA OS.\n\n");

        sb.append("SYSTEM DOCUMENTATION KNOWLEDGE:\n");
        try {
            String docsSummary = karmaDocService.getSummarizedContext();
            if (!docsSummary.isBlank()) {
                sb.append(docsSummary).append("\n");
            }
        } catch (Exception e) {
            sb.append("Documentation: unavailable\n");
        }

        sb.append("CURRENT SYSTEM STATE:\n");

        // Agents
        try {
            List<AgentResponse> agents = agentService.list();
            if (agents.isEmpty()) {
                sb.append("- Active Agents: none\n");
            } else {
                sb.append("- Active Agents:\n");
                for (AgentResponse a : agents) {
                    sb.append("  * ").append(a.getName()).append(" (").append(a.getStatus()).append(")\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Active Agents: unavailable\n");
        }

        // Providers
        try {
            List<Provider> providers = providerRepository.findByStatus("CONNECTED");
            if (providers.isEmpty()) {
                sb.append("- Connected Providers: none\n");
            } else {
                sb.append("- Connected Providers:\n");
                for (Provider p : providers) {
                    sb.append("  * ").append(p.getName()).append(" (").append(p.getProviderType()).append(")\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Connected Providers: unavailable\n");
        }

        // System health
        try {
            double cpu = systemInfoService.getCpuLoad();
            long usedMem = systemInfoService.getUsedMemory();
            long totalMem = systemInfoService.getTotalMemory();
            int cores = systemInfoService.getAvailableProcessors();
            sb.append(String.format("- System Health: CPU %.1f%%, Memory %d/%d MB, %d cores\n", cpu, usedMem, totalMem, cores));
        } catch (Exception e) {
            sb.append("- System Health: unavailable\n");
        }

        // Dashboard stats
        try {
            Map<String, Object> dashboard = reportService.dashboard();
            sb.append("- Dashboard:\n");
            sb.append("  * Projects: ").append(dashboard.getOrDefault("totalProjects", "?")).append("\n");
            sb.append("  * Active Workflows: ").append(dashboard.getOrDefault("activeWorkflows", "?")).append("\n");
            sb.append("  * Completed Today: ").append(dashboard.getOrDefault("completedToday", "?")).append("\n");
            sb.append("  * Failed: ").append(dashboard.getOrDefault("failed", "?")).append("\n");
            sb.append("  * Total Artifacts: ").append(dashboard.getOrDefault("totalArtifacts", "?")).append("\n");
        } catch (Exception e) {
            sb.append("- Dashboard: unavailable\n");
        }

        // Recent activity
        try {
            List<Mission> recentMissions = missionRepository.findAll(
                    Sort.by(Sort.Direction.DESC, "createdAt")).stream().limit(5).collect(Collectors.toList());
            if (!recentMissions.isEmpty()) {
                sb.append("- Recent Missions:\n");
                for (Mission m : recentMissions) {
                    sb.append("  * ").append(m.getName()).append(" (").append(m.getStatus()).append(")\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Recent Missions: unavailable\n");
        }

        sb.append("\nCurrent Page: ").append(request.getCurrentPage() != null ? request.getCurrentPage() : "unknown").append("\n\n");

        sb.append("You have deep knowledge of the KARMA OS documentation. Use it to answer questions about system architecture, ");
        sb.append("setup, configuration, API contracts, deployment, and any other topic users ask about.\n\n");
        sb.append("When the user asks about current system state (agents, health, providers, missions), use the context above.\n");
        sb.append("When they want to navigate, respond with intent \"navigate\" and the target page name.\n");
        sb.append("When they ask about documentation or how something works, use your documentation knowledge.\n");
        sb.append("When they ask a general question, respond conversationally with intent \"chat\".\n");
        sb.append("When they ask about system status, respond with intent \"query\".\n\n");
        sb.append("Respond ONLY with valid JSON in this exact format:\n");
        sb.append("{ \"reply\": \"your natural language response here\", \"intent\": \"navigate|chat|query\", \"target\": \"page name or empty\" }");

        return sb.toString();
    }

    private VoiceChatResponse parseLlmResponse(String content) {
        try {
            String json = extractJson(content);
            if (json == null) {
                return VoiceChatResponse.fromLlm(content, "chat", "");
            }
            JsonNode node = objectMapper.readTree(json);
            String reply = node.has("reply") ? node.get("reply").asText() : content;
            String intent = node.has("intent") ? node.get("intent").asText() : "chat";
            String target = node.has("target") ? node.get("target").asText() : "";
            return VoiceChatResponse.fromLlm(reply, intent, target);
        } catch (Exception e) {
            return VoiceChatResponse.fromLlm(content, "chat", "");
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}
