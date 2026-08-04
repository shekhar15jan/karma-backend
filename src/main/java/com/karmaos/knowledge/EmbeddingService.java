package com.karmaos.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.knowledgedocument.KnowledgeDocument;
import com.karmaos.knowledgedocument.KnowledgeDocumentRepository;
import com.karmaos.llm.GeminiProvider;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Freeware RAG: chunks text, embeds it via the free Google Gemini
 * text-embedding-004 endpoint using the user's Google/Gemini provider key,
 * stores vectors as JSONB, and answers semantic searches with in-Java cosine
 * similarity (no pgvector extension required).
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final List<String> EMBED_MODELS = List.of("text-embedding-004", "text-embedding-001");
    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_CHUNKS_PER_SOURCE = 64;
    private static volatile String resolvedEmbedModel = null;

    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final SemanticEmbeddingRepository embeddingRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public EmbeddingService(ProviderRepository providerRepository,
                            ProviderKeyCipher providerKeyCipher,
                            SemanticEmbeddingRepository embeddingRepository,
                            KnowledgeDocumentRepository documentRepository,
                            ObjectMapper objectMapper,
                            WebClient.Builder webClientBuilder) {
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.embeddingRepository = embeddingRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    public boolean isAvailable() {
        return resolveGoogleApiKey() != null;
    }

    public void indexPack(UUID packId) {
        try {
            deleteBySource("KNOWLEDGE_PACK", packId);
            List<KnowledgeDocument> docs = documentRepository.findByKnowledgePackId(packId);
            if (docs.isEmpty()) {
                return;
            }
            String key = resolveGoogleApiKey();
            if (key == null) {
                log.info("No Google/Gemini provider key; knowledge pack {} not embedded", packId);
                return;
            }
            GeminiProvider provider = new GeminiProvider(key, webClientBuilder, objectMapper);
            String embedModel = resolveEmbedModel(provider);
            if (embedModel == null) {
                log.warn("No Google embedding model available; knowledge pack {} not embedded", packId);
                return;
            }
            int embedded = 0;
            for (KnowledgeDocument doc : docs) {
                embedded += embedChunks("KNOWLEDGE_PACK", packId, provider, embedModel, doc.getContent());
            }
            log.info("Indexed {} chunks for knowledge pack {} with model {}", embedded, packId, embedModel);
        } catch (Exception e) {
            log.warn("Failed to index knowledge pack {}: {}", packId, e.getMessage());
        }
    }

    public void indexArtifact(UUID artifactId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            deleteBySource("ARTIFACT", artifactId);
            String key = resolveGoogleApiKey();
            if (key == null) {
                return;
            }
            GeminiProvider provider = new GeminiProvider(key, webClientBuilder, objectMapper);
            String embedModel = resolveEmbedModel(provider);
            if (embedModel == null) {
                log.warn("No Google embedding model available; artifact {} not embedded", artifactId);
                return;
            }
            int embedded = embedChunks("ARTIFACT", artifactId, provider, embedModel, content);
            log.info("Indexed {} chunks for artifact {} with model {}", embedded, artifactId, embedModel);
        } catch (Exception e) {
            log.warn("Failed to index artifact {}: {}", artifactId, e.getMessage());
        }
    }

    public void deleteBySource(String sourceType, UUID sourceId) {
        embeddingRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .forEach(embeddingRepository::delete);
    }

    public List<SemanticHit> search(String query, int topK) {
        List<Double> queryVector = embedText(query);
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        int k = Math.max(1, Math.min(topK > 0 ? topK : 5, 10));
        List<SemanticHit> results = new ArrayList<>();
        try {
            for (SemanticEmbedding row : embeddingRepository.findAll()) {
                List<Double> vector = objectMapper.readValue(
                        row.getEmbedding(), new TypeReference<List<Double>>() {});
                double score = cosine(queryVector, vector);
                results.add(new SemanticHit(row.getSourceType(), row.getSourceId(), row.getContent(), score));
            }
        } catch (Exception e) {
            log.warn("Semantic search failed: {}", e.getMessage());
            return List.of();
        }
        results.sort(Comparator.comparingDouble(SemanticHit::score).reversed());
        return results.stream().limit(k).toList();
    }

    public List<Double> embedText(String text) {
        String key = resolveGoogleApiKey();
        if (key == null) {
            return List.of();
        }
        try {
            GeminiProvider provider = new GeminiProvider(key, webClientBuilder, objectMapper);
            String embedModel = resolveEmbedModel(provider);
            if (embedModel == null) {
                return List.of();
            }
            return provider.embed(text, embedModel);
        } catch (Exception e) {
            log.warn("Embedding failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String resolveEmbedModel(GeminiProvider provider) {
        String cached = resolvedEmbedModel;
        if (cached != null) {
            return cached;
        }
        List<String> candidates = new ArrayList<>(EMBED_MODELS);
        for (String available : provider.listEmbedModels()) {
            if (!candidates.contains(available)) {
                candidates.add(available);
            }
        }
        for (String model : candidates) {
            List<Double> probe = provider.embed("probe", model);
            if (probe != null && !probe.isEmpty()) {
                resolvedEmbedModel = model;
                log.info("Google embedding model {} is available", model);
                return model;
            }
        }
        return null;
    }

    private int embedChunks(String sourceType, UUID sourceId, GeminiProvider provider, String embedModel, String content) {
        int embedded = 0;
        List<String> chunks = chunkText(content);
        int count = Math.min(chunks.size(), MAX_CHUNKS_PER_SOURCE);
        for (int i = 0; i < count; i++) {
            try {
                List<Double> vector = provider.embed(chunks.get(i), embedModel);
                if (vector == null || vector.isEmpty()) {
                    continue;
                }
                SemanticEmbedding row = new SemanticEmbedding();
                row.setSourceType(sourceType);
                row.setSourceId(sourceId);
                row.setContent(chunks.get(i));
                row.setEmbedding(objectMapper.writeValueAsString(vector));
                row.setModel(embedModel);
                embeddingRepository.save(row);
                embedded++;
            } catch (Exception e) {
                log.warn("Failed to embed chunk for {} {}: {}", sourceType, sourceId, e.getMessage());
            }
        }
        return embedded;
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n");
        int length = normalized.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            if (end < length) {
                int boundary = normalized.lastIndexOf('\n', end);
                if (boundary > start + CHUNK_SIZE / 2) {
                    end = boundary;
                } else {
                    int space = normalized.lastIndexOf(' ', end);
                    if (space > start + CHUNK_SIZE / 2) {
                        end = space;
                    }
                }
            }
            String chunk = normalized.substring(start, end).strip();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String resolveGoogleApiKey() {
        for (Provider p : providerRepository.findByStatus("CONNECTED")) {
            if (p.getDeletedAt() != null) {
                continue;
            }
            String type = p.getProviderType();
            if ("google".equalsIgnoreCase(type) || "gemini".equalsIgnoreCase(type)) {
                try {
                    String key = providerKeyCipher.decrypt(p.getEncryptedApiKey());
                    if (key != null && !key.isBlank()) {
                        return key;
                    }
                } catch (Exception e) {
                    log.warn("Failed to decrypt Google/Gemini provider key: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    public record SemanticHit(String sourceType, UUID sourceId, String content, double score) {}
}
