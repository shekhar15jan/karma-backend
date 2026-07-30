package com.karmaos.setup;

import com.karmaos.agent.Agent;
import com.karmaos.agent.AgentRepository;
import com.karmaos.junction.AgentSkill;
import com.karmaos.junction.AgentSkillId;
import com.karmaos.junction.AgentSkillRepository;
import com.karmaos.knowledge.KarmaDocService;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.skill.Skill;
import com.karmaos.skill.SkillRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class KarmaSetupService {

    private static final Logger log = LoggerFactory.getLogger(KarmaSetupService.class);
    private static final String KARMA_AI_AGENT_NAME = "Karma AI";

    private final AgentRepository agentRepository;
    private final ProviderRepository providerRepository;
    private final SkillRepository skillRepository;
    private final AgentSkillRepository agentSkillRepository;

    public KarmaSetupService(AgentRepository agentRepository, ProviderRepository providerRepository,
                             SkillRepository skillRepository, AgentSkillRepository agentSkillRepository) {
        this.agentRepository = agentRepository;
        this.providerRepository = providerRepository;
        this.skillRepository = skillRepository;
        this.agentSkillRepository = agentSkillRepository;
    }

    @PostConstruct
    @Transactional
    public void init() {
        try {
            setupKarmaAiAgent();
        } catch (Exception e) {
            log.error("Karma AI setup failed", e);
        }
    }

    private void setupKarmaAiAgent() {
        UUID openCodeProviderId = findOpenCodeProviderId();
        UUID googleProviderId = findGoogleProviderId();
        UUID edgeTtsProviderId = findEdgeTtsProviderId();
        UUID ffmpegProviderId = findFfmpegProviderId();

        assignProvidersToAgents(openCodeProviderId, googleProviderId, edgeTtsProviderId, ffmpegProviderId);

        Optional<Agent> existing = agentRepository.findByNameContainingIgnoreCase(KARMA_AI_AGENT_NAME)
                .stream().filter(a -> a.getStatus().equals("ACTIVE")).findFirst();
        if (existing.isPresent()) {
            log.info("Karma AI agent already exists (id={})", existing.get().getId());
            return;
        }

        Agent agent = new Agent();
        agent.setName(KARMA_AI_AGENT_NAME);
        agent.setDescription("Master AI agent with complete knowledge of KARMA OS. Understands the entire system architecture, "
                + "all agents, providers, flows, skills, documentation, and can answer any question about the platform.");
        agent.setCategory("MASTER");
        agent.setIcon("psychology");
        agent.setDefaultProviderId(openCodeProviderId);
        agent.setStatus("ACTIVE");
        agent = agentRepository.save(agent);
        UUID agentId = agent.getId();

        createAndLinkSkills(agentId);

        log.info("Karma AI agent created successfully (id={})", agentId);
    }

    private void assignProvidersToAgents(UUID openCodeProviderId, UUID googleProviderId, UUID edgeTtsProviderId, UUID ffmpegProviderId) {
        List<Agent> allAgents = agentRepository.findAll();
        for (Agent a : allAgents) {
            if (a.getDefaultProviderId() != null) continue;

            UUID providerId = switch (a.getCategory() != null ? a.getCategory().toUpperCase() : "") {
                case "MEDIA" -> {
                    String name = a.getName() != null ? a.getName().toLowerCase() : "";
                    if (name.contains("image") || name.contains("thumbnail")) {
                        yield googleProviderId != null ? googleProviderId : openCodeProviderId;
                    } else if (name.contains("voice")) {
                        yield openCodeProviderId;
                    } else if (name.contains("video")) {
                        yield ffmpegProviderId != null ? ffmpegProviderId : openCodeProviderId;
                    } else {
                        yield openCodeProviderId;
                    }
                }
                default -> openCodeProviderId;
            };

            if (providerId != null) {
                a.setDefaultProviderId(providerId);
                agentRepository.save(a);
                log.info("Assigned provider {} to agent {}", providerId, a.getName());
            }
        }
    }

    private UUID findProviderByType(String providerType) {
        List<Provider> providers = providerRepository.findByStatus("CONNECTED");
        for (Provider p : providers) {
            if (providerType.equalsIgnoreCase(p.getProviderType())) {
                return p.getId();
            }
        }
        return providers.isEmpty() ? null : providers.get(0).getId();
    }

    private UUID findGoogleProviderId() {
        return findProviderByType("google");
    }

    private UUID findEdgeTtsProviderId() {
        return findProviderByType("edge-tts");
    }

    private UUID findFfmpegProviderId() {
        return findProviderByType("ffmpeg");
    }

    private UUID findOpenCodeProviderId() {
        return findProviderByType("opencode");
    }

    private void createAndLinkSkills(UUID agentId) {
        List<SkillDefinition> skills = List.of(
            new SkillDefinition("System Architecture & Design",
                    "Deep knowledge of KARMA OS architecture, solution design, data flow, integration patterns, and system specifications."),
            new SkillDefinition("Agent Management & Orchestration",
                    "Understanding of all AI agents: Research, Planner, Script, Blog, Image, Thumbnail, Voice, Video, Review, Publishing. "
                    + "Knows how agents are configured, deployed, and orchestrated in flows."),
            new SkillDefinition("Provider & LLM Configuration",
                    "Knowledge of all provider types: OpenAI, Anthropic, Gemini, OpenRouter, OpenCode. "
                    + "Knows how to configure API keys, endpoints, models, and capabilities."),
            new SkillDefinition("Flow Design & Workflow Automation",
                    "Expertise in visual workflow designer, multi-step pipelines, agent chaining, conditional logic, and execution ordering."),
            new SkillDefinition("Mission Control & Execution",
                    "Understanding of missions, executions, steps, progress tracking, error handling, and the full execution lifecycle."),
            new SkillDefinition("Content Generation Pipeline",
                    "Knowledge of the end-to-end content pipeline: research → planning → script → image → voice → video → review → publish."),
            new SkillDefinition("Voice & Speech Systems",
                    "Understanding of speech recognition (STT), text-to-speech (TTS), voice commands, and the browser Web Speech API integration."),
            new SkillDefinition("Knowledge Management & RAG",
                    "Knowledge of grounding documents, knowledge packs, retrieval-augmented generation, and how agents use external data."),
            new SkillDefinition("UI/UX & User Experience",
                    "Knowledge of the Angular frontend, dashboard layout, panel system, sidebar navigation, and all user-facing features."),
            new SkillDefinition("Deployment & Operations",
                    "Knowledge of Docker, PostgreSQL, Spring Boot build/run, environment configuration, and production operations."),
            new SkillDefinition("API & Integration",
                    "Knowledge of all REST API endpoints, WebSocket events, authentication flow, and external service integration patterns."),
            new SkillDefinition("Security & Compliance",
                    "Knowledge of authentication, authorization, encryption, API key management, and security best practices in KARMA OS.")
        );

        for (SkillDefinition def : skills) {
            Optional<Skill> existingSkill = skillRepository.findAll().stream()
                    .filter(s -> s.getName().equals(def.name) && !"DELETED".equals(s.getStatus()))
                    .findFirst();

            Skill skill;
            if (existingSkill.isPresent()) {
                skill = existingSkill.get();
            } else {
                skill = new Skill();
                skill.setName(def.name);
                skill.setDescription(def.description);
                skill.setCategory("karma-ai");
                skill.setStatus("ACTIVE");
                skill = skillRepository.save(skill);
            }

            AgentSkillId linkId = new AgentSkillId();
            linkId.setAgentId(agentId);
            linkId.setSkillId(skill.getId());

            if (agentSkillRepository.findById(linkId).isEmpty()) {
                AgentSkill link = new AgentSkill();
                link.setId(linkId);
                agentSkillRepository.save(link);
            }
        }

        log.info("Linked {} skills to Karma AI agent", skills.size());
    }

    private record SkillDefinition(String name, String description) {}
}
