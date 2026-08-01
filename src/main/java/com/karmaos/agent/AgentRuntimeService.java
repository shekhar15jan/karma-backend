package com.karmaos.agent;

import com.karmaos.execution.ExecutionService;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.flow.Flow;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.AgentSkill;
import com.karmaos.junction.AgentSkillRepository;
import com.karmaos.llm.LLMProvider;
import com.karmaos.llm.LLMProviderFactory;
import com.karmaos.llm.LLMRequest;
import com.karmaos.llm.LLMResponse;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.prompt.Prompt;
import com.karmaos.prompt.PromptRepository;
import com.karmaos.skill.Skill;
import com.karmaos.skill.SkillRepository;
import com.karmaos.sourcedocument.SourceDocument;
import com.karmaos.sourcedocument.SourceDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);
    private static final Map<String, Double> COST_PER_TOKEN = Map.of(
        "gpt-4o", 2.5e-6,
        "gpt-4o-mini", 0.15e-6,
        "gpt-4-turbo", 10e-6,
        "claude-3-5-sonnet-20241022", 3e-6,
        "claude-3-opus-20240229", 15e-6,
        "claude-3-haiku-20240307", 0.25e-6,
        "gemini-1.5-pro", 1.25e-6,
        "gemini-1.5-flash", 0.075e-6
    );

    private final LLMProviderFactory providerFactory;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final PromptRepository promptRepository;
    private final SkillRepository skillRepository;
    private final FlowRepository flowRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final AgentRepository agentRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ExecutionService executionService;
    private final ObjectMapper objectMapper;

    public AgentRuntimeService(LLMProviderFactory providerFactory,
                               ProviderRepository providerRepository,
                               ProviderKeyCipher providerKeyCipher,
                               PromptRepository promptRepository,
                               SkillRepository skillRepository,
                               FlowRepository flowRepository,
                               AgentSkillRepository agentSkillRepository,
                               AgentRepository agentRepository,
                               SourceDocumentRepository sourceDocumentRepository,
                               ExecutionService executionService,
                               ObjectMapper objectMapper) {
        this.providerFactory = providerFactory;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.promptRepository = promptRepository;
        this.skillRepository = skillRepository;
        this.flowRepository = flowRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.agentRepository = agentRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }

    public ExecutionStep executeAgentStep(UUID executionId, UUID flowId, UUID agentId, int stepOrder,
                                          String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = new ExecutionStep();
        step.setExecutionId(executionId);
        step.setFlowId(flowId);
        step.setAgentId(agentId);
        step.setStepOrder(stepOrder);
        step.setStepType("LLM_CALL");
        step.setStatus("RUNNING");
        step.setStartedAt(LocalDateTime.now());
        step.setInputData(contextData);

        try {
            Flow flow = flowRepository.findByIdAndStatus(flowId, "ACTIVE").orElse(null);
            Agent agent = agentRepository.findById(agentId).orElse(null);
            if (agent == null) {
                throw new RuntimeException("Agent not found: " + agentId);
            }

            String systemPrompt = buildSystemPrompt(agent, flow, missionId);
            String userMessage = buildUserMessage(agent, contextData, missionId, reviewerFeedback);

            Provider provider = resolveProvider(agent);
            LLMProvider llmProvider = resolveLLMProvider(provider);

            String modelUsed = resolveModel(provider, llmProvider);
            step.setProviderId(provider != null ? provider.getId() : null);
            step.setModelUsed(modelUsed);
            step.setPromptUsed(systemPrompt + "\n\n" + userMessage);

            LLMRequest request = new LLMRequest(systemPrompt, userMessage);
            request.setModel(modelUsed);
            request.setTemperature(resolveTemperature(agent));

            LLMResponse response = llmProvider.chat(request);

            if (!response.isSuccess() && agent.getFallbackProviderId() != null) {
                log.warn("Primary provider {} failed, trying fallback provider {}", provider != null ? provider.getName() : "none", agent.getFallbackProviderId());
                provider = providerRepository.findById(agent.getFallbackProviderId()).orElse(null);
                llmProvider = resolveLLMProvider(provider);
                if (llmProvider != null && !llmProvider.getName().equals("mock")) {
                    modelUsed = resolveModel(provider, llmProvider);
                    request.setModel(modelUsed);
                    step.setProviderId(provider != null ? provider.getId() : null);
                    step.setModelUsed(modelUsed);
                    response = llmProvider.chat(request);
                }
            }

            step.setPromptTokens(response.getPromptTokens());
            step.setCompletionTokens(response.getCompletionTokens());
            step.setTotalTokens(response.getTotalTokens());
            step.setLatencyMs((int) response.getLatencyMs());
            step.setCost(calculateCost(modelUsed, response.getPromptTokens(), response.getCompletionTokens()));

            if (response.isSuccess()) {
                step.setStatus("COMPLETED");
                step.setOutputData(response.getContent());
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Agent execution failed: agentId={}, flowId={}", agentId, flowId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    private String buildSystemPrompt(Agent agent, Flow flow, UUID missionId) {
        StringBuilder sb = new StringBuilder();

        if (agent != null && agent.getDefaultPromptId() != null) {
            Prompt prompt = promptRepository.findById(agent.getDefaultPromptId()).orElse(null);
            if (prompt != null) {
                sb.append(prompt.getContent()).append("\n\n");
            }
        }

        sb.append("You are the '").append(agent != null ? agent.getName() : "Unknown Agent")
          .append("' agent in a Karma OS video automation pipeline.\n");

        if (flow != null) {
            sb.append("Current flow: ").append(flow.getName())
              .append(" (").append(flow.getCategory()).append(")\n");
        }

        sb.append("\nRespond with well-structured content. Use Markdown formatting where appropriate.");

        List<Skill> skills = getAgentSkills(agent != null ? agent.getId() : null);
        if (!skills.isEmpty()) {
            sb.append("\n\nAvailable skills:\n");
            for (Skill s : skills) {
                sb.append("- ").append(s.getName()).append(": ").append(s.getDescription()).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildUserMessage(Agent agent, String contextData, UUID missionId, String reviewerFeedback) {
        StringBuilder sb = new StringBuilder();

        if (contextData != null && !contextData.isEmpty()) {
            sb.append("Context from previous steps:\n").append(contextData).append("\n\n");
        }

        List<SourceDocument> docs = sourceDocumentRepository.findByMissionId(missionId);
        if (!docs.isEmpty()) {
            sb.append("Source documents for this mission:\n");
            for (SourceDocument doc : docs) {
                sb.append("--- ").append(doc.getFilename()).append(" ---\n");
                sb.append(doc.getContent()).append("\n\n");
            }
        }

        if (agent != null) {
            sb.append("Instructions for ").append(agent.getName()).append(":\n");
            sb.append("Process the above context and produce the required output for this step.\n");
        }

        if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
            sb.append("\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n").append(reviewerFeedback)
              .append("\nPlease revise your output accordingly and address every point above.\n");
        }

        return sb.toString();
    }

    private double resolveTemperature(Agent agent) {
        if (agent == null || agent.getConfiguration() == null) return 0.7;
        try {
            JsonNode config = objectMapper.readTree(agent.getConfiguration());
            if (config.has("temperature")) {
                return config.get("temperature").asDouble();
            }
        } catch (Exception ignored) {}
        return 0.7;
    }

    private Provider resolveProvider(Agent agent) {
        if (agent == null) return null;
        UUID providerId = agent.getDefaultProviderId();
        if (providerId != null) {
            Provider p = providerRepository.findById(providerId).orElse(null);
            if (p != null && p.getDeletedAt() == null && !"PUBLISHING".equalsIgnoreCase(p.getProviderType())) {
                log.info("Resolved default provider {} for agent {}", p.getProviderType(), agent.getName());
                return p;
            }
        }
        List<Provider> providers = providerRepository.findByStatus("CONNECTED").stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                .filter(p -> !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank())
                .sorted(Comparator.comparing(Provider::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!providers.isEmpty()) {
            Provider p = providers.get(0);
            log.info("No default provider for agent {}, using first connected provider with a key: {}", agent.getName(), p.getProviderType());
            return p;
        }
        log.warn("No configured provider found for agent {}, execution will fall back to mock", agent.getName());
        return null;
    }

    private LLMProvider resolveLLMProvider(Provider provider) {
        if (provider == null) {
            log.warn("Resolving mock LLM provider: no configured AI provider available");
            return providerFactory.getFallbackProvider();
        }
        try {
            String apiKey = providerKeyCipher.decrypt(provider.getEncryptedApiKey());
            return providerFactory.getProvider(provider.getProviderType(), apiKey, provider.getApiEndpoint());
        } catch (Exception e) {
            log.warn("Failed to create provider for type={}, using mock", provider.getProviderType(), e);
            return providerFactory.getFallbackProvider();
        }
    }

    private String resolveModel(Provider provider, LLMProvider llmProvider) {
        if (provider != null && provider.getModels() != null && !provider.getModels().isBlank()) {
            try {
                List<String> models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {});
                if (models != null && !models.isEmpty()) {
                    return models.get(0);
                }
            } catch (Exception ignored) {}
        }
        return llmProvider.getDefaultModel();
    }

    private List<Skill> getAgentSkills(UUID agentId) {
        if (agentId == null) return List.of();
        try {
            return agentSkillRepository.findByIdAgentId(agentId).stream()
                .map(as -> skillRepository.findById(as.getId().getSkillId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> executePromptSync(String systemPrompt, String userMessage) {
        try {
            LLMRequest request = new LLMRequest(systemPrompt, userMessage);

            for (Provider provider : resolveProviderChain()) {
                LLMProvider llmProvider = resolveLLMProvider(provider);
                if (llmProvider == null || "mock".equals(llmProvider.getName())) {
                    continue;
                }
                request.setModel(resolveModel(provider, llmProvider));
                LLMResponse response = llmProvider.chat(request);
                if (response.isSuccess()) {
                    log.info("executePromptSync succeeded via provider {} model {}", provider.getName(), response.getModel());
                    return Map.of(
                        "content", response.getContent(),
                        "model", response.getModel(),
                        "tokens", response.getTotalTokens(),
                        "success", true,
                        "error", "",
                        "provider", provider.getName()
                    );
                }
                log.warn("Provider {} failed for sync prompt: {}", provider.getName(), response.getErrorMessage());
            }

            return Map.of(
                "content", "",
                "model", "",
                "tokens", 0,
                "success", false,
                "error", "All providers failed to process the request"
            );
        } catch (Exception e) {
            log.error("executePromptSync error", e);
            return Map.of(
                "content", "",
                "model", "",
                "tokens", 0,
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
            );
        }
    }

    private List<Provider> resolveProviderChain() {
        List<Provider> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        try {
            Optional<Agent> karmaAi = agentRepository.findByNameContainingIgnoreCase("Karma AI")
                    .stream().filter(a -> "ACTIVE".equals(a.getStatus())).findFirst();
            if (karmaAi.isPresent()) {
                addProviderCandidate(result, seen, karmaAi.get().getDefaultProviderId());
                addProviderCandidate(result, seen, karmaAi.get().getFallbackProviderId());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve Karma AI default provider", e);
        }

        for (Provider p : providerRepository.findByStatus("CONNECTED")) {
            if (p.getDeletedAt() == null
                    && isLlmProvider(p.getProviderType())
                    && !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank()
                    && !seen.contains(p.getId())) {
                result.add(p);
                seen.add(p.getId());
            }
        }
        return result;
    }

    private void addProviderCandidate(List<Provider> result, Set<UUID> seen, UUID providerId) {
        if (providerId == null || seen.contains(providerId)) return;
        providerRepository.findById(providerId).ifPresent(p -> {
            if ("CONNECTED".equals(p.getStatus())
                    && p.getDeletedAt() == null
                    && isLlmProvider(p.getProviderType())
                    && !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank()) {
                result.add(p);
                seen.add(p.getId());
            }
        });
    }

    private boolean isLlmProvider(String providerType) {
        if (providerType == null) return false;
        String t = providerType.toLowerCase();
        return t.equals("openai") || t.equals("anthropic") || t.equals("google")
            || t.equals("gemini") || t.equals("openrouter") || t.equals("opencode")
            || t.equals("groq");
    }

    private BigDecimal calculateCost(String model, int promptTokens, int completionTokens) {
        Double rate = COST_PER_TOKEN.getOrDefault(model, 1.0e-6);
        double cost = (promptTokens + completionTokens) * rate;
        return BigDecimal.valueOf(cost);
    }
}
