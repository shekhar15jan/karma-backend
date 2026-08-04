package com.karmaos.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.access.OwnershipService;
import com.karmaos.agent.AgentResponse;
import com.karmaos.agent.AgentService;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.knowledge.EmbeddingService;
import com.karmaos.knowledge.KarmaDocService;
import com.karmaos.knowledge.KarmaDocService.DocEntry;
import com.karmaos.llm.LLMProvider;
import com.karmaos.llm.LLMProviderFactory;
import com.karmaos.llm.LLMRequest;
import com.karmaos.llm.LLMResponse;
import com.karmaos.memory.KarmaMemoryService;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.Project;
import com.karmaos.project.ProjectRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.report.ReportService;
import com.karmaos.settings.RuntimeSettings;
import com.karmaos.system.SystemInfoService;
import com.karmaos.workspace.Workspace;
import com.karmaos.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class VoiceChatService {

    private static final Logger log = LoggerFactory.getLogger(VoiceChatService.class);
    private static final String LLM_MODEL = "deepseek-v4-flash-free";

    private final AgentService agentService;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final ReportService reportService;
    private final MissionRepository missionRepository;
    private final ExecutionRepository executionRepository;
    private final SystemInfoService systemInfoService;
    private final LLMProviderFactory llmProviderFactory;
    private final ObjectMapper objectMapper;
    private final KarmaDocService karmaDocService;
    private final OwnershipService ownershipService;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final KarmaMemoryService karmaMemoryService;
    private final EmbeddingService embeddingService;
    private final RuntimeSettings runtimeSettings;

    public VoiceChatService(AgentService agentService, ProviderRepository providerRepository,
                            ProviderKeyCipher providerKeyCipher,
                            ReportService reportService, MissionRepository missionRepository,
                            ExecutionRepository executionRepository, SystemInfoService systemInfoService,
                            LLMProviderFactory llmProviderFactory, ObjectMapper objectMapper,
                            KarmaDocService karmaDocService, OwnershipService ownershipService,
                            WorkspaceRepository workspaceRepository, ProjectRepository projectRepository,
                            KarmaMemoryService karmaMemoryService, EmbeddingService embeddingService,
                            RuntimeSettings runtimeSettings) {
        this.agentService = agentService;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.reportService = reportService;
        this.missionRepository = missionRepository;
        this.executionRepository = executionRepository;
        this.systemInfoService = systemInfoService;
        this.llmProviderFactory = llmProviderFactory;
        this.objectMapper = objectMapper;
        this.karmaDocService = karmaDocService;
        this.ownershipService = ownershipService;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.karmaMemoryService = karmaMemoryService;
        this.embeddingService = embeddingService;
        this.runtimeSettings = runtimeSettings;
    }

    public VoiceChatResponse chat(VoiceChatRequest request, UUID userId, String role) {
        String text = request.getText() != null ? request.getText().trim().toLowerCase() : "";
        if (text.isEmpty()) {
            return VoiceChatResponse.staticReply("I didn't catch that. Could you please repeat?", "chat", "");
        }

        VoiceChatResponse languageMatch = tryLanguageSwitch(text);
        if (languageMatch != null) {
            return languageMatch;
        }

        VoiceChatResponse staticMatch = tryStaticFaq(text);
        if (staticMatch != null) {
            return staticMatch;
        }

        return callLlm(request, userId, role);
    }

    // ========== LANGUAGE SWITCH (intent "language" + target code) ==========

    private VoiceChatResponse tryLanguageSwitch(String text) {
        String lang = matchLanguagePhrase(text);
        if (lang == null && containsAny(text, "change language", "switch language", "change the language", "भाषा बदलो", "भाषा बदल")) {
            lang = detectScriptLanguage(text);
        }
        if (lang == null) {
            return null;
        }
        String confirmation = LANGUAGE_CONFIRMATIONS.getOrDefault(lang, LANGUAGE_CONFIRMATIONS.get("en"));
        VoiceChatResponse resp = VoiceChatResponse.staticReply(confirmation, "language", lang);
        resp.setLanguage(lang);
        return resp;
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String matchLanguagePhrase(String text) {
        String best = null;
        int bestLen = -1;
        for (Map.Entry<String, List<String>> e : LANGUAGE_PATTERNS.entrySet()) {
            for (String phrase : e.getValue()) {
                if (text.contains(phrase) && phrase.length() > bestLen) {
                    best = e.getKey();
                    bestLen = phrase.length();
                }
            }
        }
        return best;
    }

    private String detectScriptLanguage(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') {
                return "hi";
            }
        }
        return "en";
    }

    private String detectLanguage(VoiceChatRequest request) {
        String pref = request.getPreferredLanguage();
        if (pref != null && !pref.isBlank() && SUPPORTED_LANGUAGES.contains(pref)) {
            return pref;
        }
        if (request.getText() != null) {
            String detected = detectScriptLanguage(request.getText());
            if (detected != null) {
                return detected;
            }
        }
        return "en";
    }

    private String languageName(String code) {
        return LANGUAGE_NAMES.getOrDefault(code, "English");
    }

    private static final Map<String, List<String>> LANGUAGE_PATTERNS = new java.util.LinkedHashMap<>();
    static {
        LANGUAGE_PATTERNS.put("en", List.of("speak in english", "speak english", "switch to english", "change to english", "talk in english",
                "respond in english", "english me bol", "in english", "english", "अंग्रेजी में बोलो", "इंग्लिश में", "अंग्रेज़ी में", "अंग्रेजी", "इंग्लिश"));
        LANGUAGE_PATTERNS.put("hi", List.of("speak in hindi", "speak hindi", "switch to hindi", "change to hindi", "talk in hindi",
                "hindi me bol", "hindi me bolo", "in hindi", "hindi", "bolo hindi", "बोलो हिंदी", "बोलो हिन्दी",
                "हिंदी में बोलो", "हिन्दी में", "हिंदी में", "हिंदी", "हिन्दी"));
        LANGUAGE_PATTERNS.put("mr", List.of("speak in marathi", "speak marathi", "switch to marathi", "change to marathi", "talk in marathi",
                "marathi me bol", "marathi me bolo", "in marathi", "marathi", "bolo marathi", "मराठीत बोलो", "मराठीत",
                "मराठी बोल", "मराठी"));
        LANGUAGE_PATTERNS.put("ta", List.of("speak in tamil", "speak tamil", "switch to tamil", "change to tamil", "in tamil", "tamil", "தமிழில் பேசு", "தமிழில்", "தமிழ்"));
        LANGUAGE_PATTERNS.put("te", List.of("speak in telugu", "speak telugu", "switch to telugu", "change to telugu", "in telugu", "telugu", "తెలుగులో మాట్లాడు", "తెలుగులో", "తెలుగు"));
        LANGUAGE_PATTERNS.put("bn", List.of("speak in bengali", "speak bengali", "switch to bengali", "change to bengali", "in bengali", "bengali", "বাংলায় কথা বলো", "বাংলায়", "বাংলা"));
        LANGUAGE_PATTERNS.put("gu", List.of("speak in gujarati", "speak gujarati", "switch to gujarati", "change to gujarati", "in gujarati", "gujarati", "ગુજરાતીમાં બોલો", "ગુજરાતીમાં", "ગુજરાતી"));
        LANGUAGE_PATTERNS.put("kn", List.of("speak in kannada", "speak kannada", "switch to kannada", "change to kannada", "in kannada", "kannada", "ಕನ್ನಡದಲ್ಲಿ ಮಾತನಾಡು", "ಕನ್ನಡದಲ್ಲಿ", "ಕನ್ನಡ"));
        LANGUAGE_PATTERNS.put("ml", List.of("speak in malayalam", "speak malayalam", "switch to malayalam", "change to malayalam", "in malayalam", "malayalam", "മലയാളത്തിൽ സംസാരിക്കുക", "മലയാളത്തിൽ", "മലയാളം"));
        LANGUAGE_PATTERNS.put("ur", List.of("speak in urdu", "speak urdu", "switch to urdu", "change to urdu", "in urdu", "urdu", "اردو میں بولیں", "اردو میں", "اردو"));
        LANGUAGE_PATTERNS.put("fr", List.of("speak in french", "speak french", "switch to french", "change to french", "in french", "french", "parler français", "parlez français", "en français"));
        LANGUAGE_PATTERNS.put("de", List.of("speak in german", "speak german", "switch to german", "change to german", "in german", "german", "auf deutsch", "deutsch sprechen", "deutsch"));
        LANGUAGE_PATTERNS.put("ja", List.of("speak in japanese", "speak japanese", "switch to japanese", "change to japanese", "in japanese", "japanese", "日本語で話す", "日本語"));
        LANGUAGE_PATTERNS.put("zh", List.of("speak in chinese", "speak chinese", "switch to chinese", "change to chinese", "in chinese", "chinese", "用中文说", "中文", "普通话"));
    }

    private static final Map<String, String> LANGUAGE_NAMES = new java.util.LinkedHashMap<>();
    static {
        LANGUAGE_NAMES.put("en", "English");
        LANGUAGE_NAMES.put("hi", "Hindi");
        LANGUAGE_NAMES.put("mr", "Marathi");
        LANGUAGE_NAMES.put("ta", "Tamil");
        LANGUAGE_NAMES.put("te", "Telugu");
        LANGUAGE_NAMES.put("bn", "Bengali");
        LANGUAGE_NAMES.put("gu", "Gujarati");
        LANGUAGE_NAMES.put("kn", "Kannada");
        LANGUAGE_NAMES.put("ml", "Malayalam");
        LANGUAGE_NAMES.put("ur", "Urdu");
        LANGUAGE_NAMES.put("fr", "French");
        LANGUAGE_NAMES.put("de", "German");
        LANGUAGE_NAMES.put("ja", "Japanese");
        LANGUAGE_NAMES.put("zh", "Chinese");
    }

    private static final java.util.Set<String> SUPPORTED_LANGUAGES = LANGUAGE_NAMES.keySet();

    private static final Map<String, String> LANGUAGE_CONFIRMATIONS = new java.util.LinkedHashMap<>();
    static {
        LANGUAGE_CONFIRMATIONS.put("en", "Got it! I'll respond in English from now on. What would you like to know?");
        LANGUAGE_CONFIRMATIONS.put("hi", "ठीक है! मैं अब हिंदी में जवाब दूँगा। आप क्या जानना चाहेंगे?");
        LANGUAGE_CONFIRMATIONS.put("mr", "ठीक आहे! मी आता मराठीत उत्तर देईन. तुम्हाला काय जाणून घ्यायचं आहे?");
        LANGUAGE_CONFIRMATIONS.put("ta", "சரி! இனி நான் தமிழில் பதிலளிக்கிறேன். நீங்கள் என்ன தெரிந்து கொள்ள விரும்புகிறீர்கள்?");
        LANGUAGE_CONFIRMATIONS.put("te", "సరే! ఇకపై నేను తెలుగులో సమాధానం చెబుతాను. మీరు ఏమి తెలుసుకోవాలనుకుంటున్నారు?");
        LANGUAGE_CONFIRMATIONS.put("bn", "ঠিক আছে! এখন থেকে আমি বাংলায় উত্তর দেব। আপনি কী জানতে চান?");
        LANGUAGE_CONFIRMATIONS.put("gu", "બરાબર! હવેથી હું ગુજરાતીમાં જવાબ આપીશ. તમે શું જાણવા માંગો છો?");
        LANGUAGE_CONFIRMATIONS.put("kn", "ಸರಿ! ಇನ್ನು ಮುಂದೆ ನಾನು ಕನ್ನಡದಲ್ಲಿ ಉತ್ತರಿಸುತ್ತೇನೆ. ನೀವು ಏನು ತಿಳಿದುಕೊಳ್ಳಲು ಬಯಸುತ್ತೀರಿ?");
        LANGUAGE_CONFIRMATIONS.put("ml", "ശരി! ഇനി മുതൽ ഞാൻ മലയാളത്തിൽ ഉത്തരം നൽകും. നിങ്ങൾ എന്താണ് അറിയാൻ ആഗ്രഹിക്കുന്നത്?");
        LANGUAGE_CONFIRMATIONS.put("ur", "ٹھیک ہے! اب سے میں اردو میں جواب دوں گا۔ آپ کیا جاننا چاہیں گے؟");
        LANGUAGE_CONFIRMATIONS.put("fr", "Très bien ! Je vais désormais répondre en français. Que souhaitez-vous savoir ?");
        LANGUAGE_CONFIRMATIONS.put("de", "Alles klar! Ich antworte ab jetzt auf Deutsch. Was möchten Sie wissen?");
        LANGUAGE_CONFIRMATIONS.put("ja", "わかりました！これから日本語でお答えします。何をお知りになりたいですか？");
        LANGUAGE_CONFIRMATIONS.put("zh", "好的！从现在开始我用中文回答。您想了解什么？");
    }

    // ========== STATIC FAQ (instant replies, no LLM call) ==========

    private VoiceChatResponse tryStaticFaq(String text) {
        for (FaqEntry entry : FAQ) {
            for (String keyword : entry.keywords) {
                if (matchesKeyword(text, keyword)) {
                    VoiceChatResponse resp = VoiceChatResponse.staticReply(entry.reply, entry.intent, entry.target);
                    resp.setLanguage("en");
                    return resp;
                }
            }
        }
        return null;
    }

    private boolean matchesKeyword(String text, String keyword) {
        return Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b").matcher(text).find();
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
                "Providers are the external AI services KARMA OS connects to — Google Gemini, OpenCode, Groq, OpenRouter, Edge TTS, and more. "
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

        new FaqEntry(List.of("go to prompt studio", "take me to prompt studio", "navigate to prompt studio", "open prompt studio", "show prompt studio"),
                "Opening Prompt Studio.", "navigate", "prompt-studio")
    );

    private record FaqEntry(List<String> keywords, String reply, String intent, String target) {}

    // ========== LLM FALLBACK (for dynamic system queries) ==========

    private VoiceChatResponse callLlm(VoiceChatRequest request, UUID userId, String role) {
        try {
            String language = detectLanguage(request);
            String systemPrompt = buildSystemPrompt(request, language, userId, role);
            LLMRequest llmRequest = new LLMRequest(systemPrompt, null);
            llmRequest.setModel(LLM_MODEL);
            llmRequest.setTemperature(0.5);
            llmRequest.setMaxTokens(1200);
            llmRequest.setMessages(buildHistoryMessages(request));

            LLMProvider llmProvider = resolveLlmProvider();
            if (llmProvider == null) {
                return VoiceChatResponse.staticReply(
                        "I'm having trouble connecting to my AI backend. Please check your provider settings.", "chat", "");
            }

            if ("gemini".equals(llmProvider.getName())
                    && runtimeSettings.getBoolean(RuntimeSettings.KEY_GOOGLE_SEARCH_GROUNDING, false)) {
                llmRequest.setAdditionalParams(Map.of("googleSearchGrounding", true));
            }

            LLMResponse llmResponse = llmProvider.chat(llmRequest);
            if (!llmResponse.isSuccess()) {
                log.warn("LLM call failed: {}", llmResponse.getErrorMessage());
                return VoiceChatResponse.staticReply(
                        "I couldn't process that right now. Please try again.", "chat", "");
            }

            return parseLlmResponse(llmResponse.getContent(), language);

        } catch (Exception e) {
            log.error("VoiceChatService error", e);
            return VoiceChatResponse.staticReply(
                    "Something went wrong. Please try again.", "chat", "");
        }
    }

    private List<Map<String, String>> buildHistoryMessages(VoiceChatRequest request) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        List<Map<String, String>> history = request.getHistory();
        if (history != null) {
            for (Map<String, String> entry : history) {
                if (entry == null) continue;
                String role = entry.get("role");
                String content = entry.get("content");
                if (content == null || content.isBlank()) continue;
                String llmRole;
                if ("assistant".equalsIgnoreCase(role)) {
                    llmRole = "assistant";
                } else {
                    llmRole = "user";
                }
                messages.add(Map.of("role", llmRole, "content", content));
            }
        }
        if (request.getText() != null && !request.getText().isBlank()) {
            messages.add(Map.of("role", "user", "content", request.getText()));
        }
        int max = 20;
        if (messages.size() > max) {
            return messages.subList(messages.size() - max, messages.size());
        }
        return messages;
    }

    private LLMProvider resolveLlmProvider() {
        List<Provider> connected = providerRepository.findByStatus("CONNECTED").stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                .sorted(Comparator.comparing((Provider p) -> !"OPENCODE".equalsIgnoreCase(p.getProviderType()))
                        .thenComparing(Provider::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (Provider p : connected) {
            try {
                String apiKey = providerKeyCipher.decrypt(p.getEncryptedApiKey());
                return llmProviderFactory.getProvider(p.getProviderType(), apiKey, p.getApiEndpoint());
            } catch (Exception e) {
                log.warn("Failed to create provider {}: {}", p.getProviderType(), e.getMessage());
            }
        }
        return null;
    }

    private String buildSystemPrompt(VoiceChatRequest request, String language, UUID userId, String role) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Karma AI — the master intelligence and guide for the entire KARMA OS platform. ");
        sb.append("You have complete knowledge of the system's architecture, all its agents, providers, skills, flows, and documentation. ");
        sb.append("You answer questions accurately, help users navigate, diagnose system health, and explain any part of KARMA OS.\n\n");

        sb.append("HUMAN TONE: Speak like a friendly, knowledgeable human — never robotic. ");
        sb.append("No markdown, no asterisks, no arrows (→), no bullet symbols (•, -, *) in your reply unless the user asks for structured technical detail. ");
        sb.append("Default to the SHORTEST answer that fully answers the question — one short sentence is almost always enough. ");
        sb.append("Match the depth of your answer to the question: functional questions (what KARMA OS can do, features, workflows) get plain everyday explanations of value and use; ");
        sb.append("non-functional questions (performance, security, reliability, monitoring, architecture at a high level) get confident, simple, trustworthy explanations; ");
        sb.append("technical questions (API endpoints, request/response payloads, configuration, providers, SDKs) get precise, structured technical detail with exact names and fields.\n\n");

        sb.append("LANGUAGE: The user prefers to communicate in ").append(languageName(language));
        sb.append(" (ISO code: ").append(language).append("). ALWAYS write your entire \"reply\" in ").append(languageName(language));
        sb.append(" unless the user explicitly asks you to use another language. Set the \"language\" field in your JSON output to the ISO code you actually used.\n\n");

        sb.append("SYSTEM KNOWLEDGE (BLACK BOOK):\n");
        try {
            String blackBook = karmaDocService.getCuratedContext();
            if (!blackBook.isBlank()) {
                sb.append(blackBook).append("\n\n");
            }
        } catch (Exception e) {
            sb.append("Black Book: unavailable\n");
        }

        sb.append("KNOWLEDGE POLICY:\n");
        sb.append("The SYSTEM KNOWLEDGE above (the Black Book) is your ONLY knowledge source for KARMA OS. ");
        sb.append("There are no other on-demand documents to fetch — the /docs files are internal development artifacts and you must NOT reference, list, fetch, or paste them. ");
        sb.append("Never use intent \"doc\". Answer everything about KARMA OS from the Black Book above.\n\n");

        try {
            String query = request.getText();
            if (query != null && !query.isBlank() && embeddingService.isAvailable()) {
                List<EmbeddingService.SemanticHit> hits = embeddingService.search(query, 4);
                if (!hits.isEmpty()) {
                    sb.append("RELEVANT KNOWLEDGE (semantic search over the user's uploaded knowledge packs and past outputs):\n");
                    for (EmbeddingService.SemanticHit hit : hits) {
                        sb.append("[").append(hit.sourceType()).append("]\n");
                        sb.append(hit.content()).append("\n\n");
                    }
                    sb.append("Use the RELEVANT KNOWLEDGE above as supporting context only when it matches the user's question about their own content. It is the user's material, not KARMA OS documentation, and never changes the Black Book.\n\n");
                }
            }
        } catch (Exception e) {
            sb.append("");
        }

        sb.append("ACTION-ORIENTED MINDSET: You are the operator of KARMA OS. When the user asks you to do something in the system, DO it through the UI action contract — do not decline, do not just describe it, and do not restate limitations. Open the right screen or modal, pre-fill the form, and tell the user what to confirm in the UI. Use the live system state above to pick the correct workspace, project, provider, and run mode.\n\n");

        sb.append("OPERATOR ROLE: The signed-in user has role \"").append(role).append("\". Only two roles exist: ADMIN and OPERATOR. Respect role boundaries: do NOT emit or offer actions the role cannot perform, and never suggest bypassing permissions. If the user asks for something outside their role, explain briefly what their role can do and, when possible, offer the closest permitted action or navigate them to the relevant screen.\n");
        sb.append("Role boundaries (verb → allowed roles):\n");
        sb.append("  ADMIN: ALL actions — everything below, plus create_agent, provider connect/test/edit, publishing channel config, flows designer edits, prompts/skills management, user/role/settings administration.\n");
        sb.append("  OPERATOR: privileged access to the content pipeline only — navigate, open_modal, prefill_mission, create_workspace, create_project, create_mission, trigger_mission, set_run_mode, approve_artifact, reject_artifact, and publish content. Operators may VIEW agents, providers, flows, prompts, and channels read-only but must NOT create/edit/delete agents, connect/test/edit providers, edit flows, manage prompts/skills/channels, or touch user/role/settings.\n");
        sb.append("  Neither role: nothing beyond these. If an OPERATOR asks for an ADMIN-only action (e.g. create_agent, connect a provider), do not emit it — explain it is admin-only and offer the closest permitted alternative or navigation.\n\n");

        sb.append("LEARNED MEMORY (auto-curated per user; personalization, not authority):\n");
        try {
            String memoryBlock = karmaMemoryService.buildMemoryPromptBlock(userId);
            if (!memoryBlock.isBlank()) {
                sb.append(memoryBlock);
            } else {
                sb.append("No learned memory yet for this user.\n\n");
            }
        } catch (Exception e) {
            sb.append("No learned memory available.\n\n");
        }
        sb.append("Memory rules: these are hints from past conversations/actions to personalize the experience. If the user's NEW instruction contradicts a learned memory, the new instruction wins. Do not treat learned memory as a role or permission grant.\n\n");

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
            List<Provider> providers = providerRepository.findByStatus("CONNECTED").stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                    .toList();
            if (providers.isEmpty()) {
                sb.append("- Connected Providers: none\n");
            } else {
                sb.append("- Connected Providers:\n");
                for (Provider p : providers) {
                    sb.append("  * ").append(p.getName()).append(" (id=").append(p.getId())
                      .append(", type=").append(p.getProviderType()).append(")\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Connected Providers: unavailable\n");
        }

        // Workspaces and projects (owned, with IDs)
        try {
            List<Workspace> workspaces = workspaceRepository.findByOwnerIdAndStatus(userId, "ACTIVE");
            if (workspaces.isEmpty()) {
                sb.append("- Workspaces: none (the user must create one first)\n");
            } else {
                sb.append("- Workspaces:\n");
                for (Workspace w : workspaces) {
                    sb.append("  * ").append(w.getName()).append(" (id=").append(w.getId()).append(")\n");
                    List<Project> projects = projectRepository.findByWorkspaceIdAndStatus(w.getId(), "ACTIVE");
                    for (Project p : projects) {
                        sb.append("      - Project: ").append(p.getName()).append(" (id=").append(p.getId()).append(")\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("- Workspaces: unavailable\n");
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
            Map<String, Object> dashboard = reportService.dashboard(userId);
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
            List<Mission> recentMissions = missionRepository
                    .findByProjectIdInAndStatusNot(ownershipService.getOwnedProjectIds(userId), "DELETED").stream()
                    .sorted(java.util.Comparator.comparing(Mission::getCreatedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());
            if (!recentMissions.isEmpty()) {
                sb.append("- Recent Missions:\n");
                for (Mission m : recentMissions) {
                    sb.append("  * ").append(m.getName()).append(" (id=").append(m.getId())
                      .append(", status=").append(m.getStatus()).append(", type=")
                      .append(m.getMissionType()).append(")\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Recent Missions: unavailable\n");
        }

        sb.append("\nCurrent Page: ").append(request.getCurrentPage() != null ? request.getCurrentPage() : "unknown").append("\n\n");

        sb.append("\nYou are the authority on KARMA OS — the Black Book above is the single source of truth and is accurate. ");
        sb.append("Use it to answer questions about system architecture, agents, providers, execution, configuration, security, performance, operations, and any other topic users ask about.\n\n");
        sb.append("When the user asks about current system state (agents, health, providers, missions), use the context above.\n");
        sb.append("When they want to navigate, respond with intent \"navigate\" and the target page name.\n");
        sb.append("When they ask about documentation or how something works, use your Black Book knowledge.\n");
        sb.append("When they ask whether a feature works, answer confidently from the Black Book and, whenever the request maps to a UI action, carry it out via the action contract — do not refuse or dwell on limitations.\n");
        sb.append("When they ask a general question, respond conversationally with intent \"chat\".\n");
        sb.append("When they ask about system status, respond with intent \"query\".\n\n");

        sb.append("WALKTHROUGH MODE (when the user asks to \"walk through the system\", \"tour\", \"guide me\", \"walk me through\", \"onboarding\"):\n");
        sb.append("Use the operator sequence in the Black Book (Part A: log in, workspace, project, AI provider, first mission, REVIEW approval, final content, artifacts, publishing, voice/chat) as your script. Do NOT paste the file or quote large blocks. ");
        sb.append("Lead them phase-by-phase conversationally, exactly in order. ");
        sb.append("For each phase: explain in 2-3 short sentences what they should do and what they should see on screen, then STOP and ask them to tell you \"next\" when they are done. ");
        sb.append("Before starting, quickly check the live context above (agents, connected providers, active missions) and adapt: if no AI provider is connected, warn them to fix that before running a real mission; if they already have a workspace, skip straight to the next missing step. ");
        sb.append("Keep each phase short — this is a guided tour, not a document dump.\n\n");
        sb.append("Respond ONLY with valid JSON in this exact format:\n");
        sb.append("{ \"reply\": \"your natural language response here\", \"intent\": \"navigate|chat|query\", \"target\": \"page name or empty\", \"language\": \"ISO code of the language used\", \"action\": { \"type\": \"...\", \"params\": { ... } } }\n");
        sb.append("ACTION CONTRACT: you OPERATE the KARMA OS UI for the user — like an operator clicking through the app. When the user asks you to DO something in KARMA OS (create a mission, run a mission, create a workspace/project, approve/reject, connect a provider, change run mode, open a screen), set the \"action\" object to the UI operation and set intent \"navigate\" with the target page when a screen change is needed. Supported action types:\n");
        sb.append("  * \"open_modal\": params { \"modal\": \"create-mission|flows|providers|agents|approvals\" } — open that modal on the page.\n");
        sb.append("  * \"prefill_mission\": params { \"workspaceId\", \"projectId\", \"name\", \"description\", \"missionType\", \"priority\", \"providerId\", \"runMode\": \"REVIEW|AUTO\" } — pre-fill the New Mission form (only include fields you know to be correct; omit unknown ids).\n");
        sb.append("  * \"navigate\": params { \"page\": \"dashboard|mission-control|executions|reviews|publishing|workspaces|agents|providers|calendar|knowledge|prompts|flows|artifacts|analytics|settings\" } — go to that screen.\n");
        sb.append("  * \"create_workspace\": params { \"name\", \"description\" } — actually create a workspace now. Use the name the user gave.\n");
        sb.append("  * \"create_project\": params { \"workspaceId\", \"name\" } — actually create a project in that workspace (use an id from the live Workspaces list; omit if unknown).\n");
        sb.append("  * \"create_mission\": params { \"projectId\", \"name\", \"description\", \"missionType\", \"priority\", \"providerId\" } — actually create the mission now (no form). Only emit if you have a valid projectId from the live state; otherwise use \"prefill_mission\".\n");
        sb.append("  * \"trigger_mission\": params { \"missionId\", \"runMode\": \"REVIEW|AUTO\" } — start executing an existing mission now (use an id from Recent Missions; runMode defaults to the user's preference or REVIEW).\n");
        sb.append("  * \"approve_artifact\": params { \"id\" } — approve a pending artifact review.\n");
        sb.append("  * \"reject_artifact\": params { \"id\" } — reject a pending artifact review.\n");
        sb.append("  * \"set_run_mode\": params { \"runMode\": \"REVIEW|AUTO\" } — change the run mode used when triggering missions.\n");
        sb.append("  * \"create_agent\": params { \"name\", \"category\", \"description\" } — actually create an agent.\n");
        sb.append("  * \"remember_memory\": params { \"key\", \"value\" } — store an explicit user preference/fact for future sessions (e.g. \"always use AUTO mode\", \"never do X\", \"I prefer Y\"). Emit ONLY when the user states a durable preference; do NOT emit for one-off facts, greetings, or questions. The saved memory is injected into future conversations for this user.\n");
        sb.append("When emitting an action, your \"reply\" must briefly tell the user what you did or what you need from them (e.g. \"I have opened the New Mission form, please review and click Create & Run\" or \"Mission created and running in REVIEW mode\"). If the action requires user confirmation in the UI, say so. Prefer actually performing the action (create_workspace/create_project/create_mission/trigger_mission/approve_artifact/reject_artifact/set_run_mode/create_agent) whenever you have the required ids and values from the live state — only fall back to open_modal/prefill when you are missing something. When the user simply asks a question (no action needed), omit \"action\" entirely.\n");
        sb.append("RULES: the \"reply\" value must be plain conversational text on ONE line — no literal line breaks, no quotes, no embedded JSON inside it. ");
        sb.append("BE BRIEF: answer in ONE short sentence (under 25 words) whenever possible. ");
        sb.append("Only expand to 2-3 short sentences (max ~50 words) for genuinely technical or step-by-step questions. ");
        sb.append("Never use lists, bullet points, or multiple paragraphs unless the user explicitly asks for detail.");

        return sb.toString();
    }

    private VoiceChatResponse parseLlmResponse(String content, String fallbackLanguage) {
        try {
            JsonNode node = parseJsonObject(content);
            if (node == null) {
                String salvaged = extractReplyValue(content);
                if (salvaged != null && !salvaged.isBlank()) {
                    return withLanguage(VoiceChatResponse.fromLlm(salvaged, "chat", ""), fallbackLanguage);
                }
                return withLanguage(VoiceChatResponse.fromLlm(cleanUp(content), "chat", ""), fallbackLanguage);
            }
            String reply = node.has("reply") ? node.get("reply").asText() : content;
            String intent = node.has("intent") ? node.get("intent").asText() : "chat";
            String target = node.has("target") ? node.get("target").asText() : "";
            String language = node.has("language") ? node.get("language").asText() : fallbackLanguage;
            Map<String, Object> action = node.has("action") && node.get("action").isObject()
                    ? objectMapper.convertValue(node.get("action"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                    : null;
            if ("doc".equalsIgnoreCase(intent)) {
                intent = "chat";
                target = "";
            }

            for (int depth = 0; depth < 3; depth++) {
                JsonNode inner = parseJsonObject(reply);
                if (inner == null) break;
                reply = inner.has("reply") ? inner.get("reply").asText() : reply;
                if (inner.has("intent")) intent = inner.get("intent").asText();
                if (inner.has("target")) target = inner.get("target").asText();
                if (inner.has("language")) language = inner.get("language").asText();
                if (inner.has("action") && inner.get("action").isObject()) {
                    action = objectMapper.convertValue(inner.get("action"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                }
            }

            String trimmed = reply.trim();
            if (trimmed.startsWith("{") && trimmed.contains("\"reply\"")) {
                String salvaged = extractReplyValue(trimmed);
                if (salvaged != null && !salvaged.isBlank()) {
                    reply = salvaged;
                }
            }

            if (reply == null || reply.isBlank()) {
                return VoiceChatResponse.staticReply(
                        "I'm having trouble forming an answer right now. Could you rephrase?", "chat", "");
            }

            if ("doc".equalsIgnoreCase(intent)) {
                return withLanguage(withDocument(reply, target), language);
            }
            VoiceChatResponse resp = withLanguage(VoiceChatResponse.fromLlm(reply, intent, target), language);
            resp.setAction(action);
            return resp;
        } catch (Exception e) {
            return withLanguage(VoiceChatResponse.fromLlm(cleanUp(content), "chat", ""), fallbackLanguage);
        }
    }

    private VoiceChatResponse withLanguage(VoiceChatResponse resp, String language) {
        if (resp != null && language != null && !language.isBlank()) {
            resp.setLanguage(language);
        }
        return resp;
    }

    private String extractReplyValue(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isObject() && node.has("reply")) {
                return node.get("reply").asText();
            }
        } catch (Exception ignored) {}
        java.util.regex.Matcher m = Pattern.compile("\"reply\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(text);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
        }
        return null;
    }

    private String cleanUp(String text) {
        if (text == null || text.isBlank()) {
            return "I'm having trouble forming an answer right now. Could you rephrase?";
        }
        return text;
    }

    private JsonNode parseJsonObject(String text) {
        if (text == null) return null;
        int idx = text.indexOf('{');
        while (idx != -1) {
            try {
                try (com.fasterxml.jackson.core.JsonParser parser =
                             objectMapper.getFactory().createParser(text.substring(idx))) {
                    JsonNode node = objectMapper.readTree(parser);
                    if (node != null && node.isObject()) {
                        return node;
                    }
                }
            } catch (Exception ignored) {}
            idx = text.indexOf('{', idx + 1);
        }
        return null;
    }

    private VoiceChatResponse withDocument(String reply, String target) {
        if (target == null || target.isBlank()) {
            return VoiceChatResponse.fromLlm(reply, "chat", "");
        }
        DocEntry doc = karmaDocService.getDocument(target);
        if (doc == null) {
            return VoiceChatResponse.fromLlm(
                    reply + " (Note: I could not find a document named \"" + target + "\". Ask me to list the available documents.)",
                    "chat", "");
        }
        int maxDocChars = 6000;
        String docContent = doc.content();
        if (docContent.length() > maxDocChars) {
            docContent = docContent.substring(0, maxDocChars).replaceAll("\\s+\\S*$", "") + "\n…(truncated)";
        }
        return VoiceChatResponse.fromLlm(
                reply + "\n\n--- " + doc.title() + " (" + doc.category() + ") ---\n" + docContent,
                "chat", "");
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
