package com.karmaos.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GeminiProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeminiProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "gemini"; }

    @Override
    public String getDefaultModel() { return "gemini-3.5-flash"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of("gemini-3.5-flash", "gemini-3.6-flash", "gemini-3.1-pro");
    }

    @Override
    public List<String> fetchAvailableModels() {
        return listChatModels();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        long start = System.currentTimeMillis();
        try {
            String model = request.getModel() != null ? request.getModel() : getDefaultModel();
            String url = API_URL + "/" + model + ":generateContent?key=" + apiKey;

            List<Map<String, Object>> contents = new ArrayList<>();

            String userMsg = request.getUserMessage() != null ? request.getUserMessage() : "";
            if (request.getMessages() != null) {
                for (Map<String, String> msg : request.getMessages()) {
                    contents.add(Map.of(
                        "role", "user".equals(msg.get("role")) ? "user" : "model",
                        "parts", List.of(Map.of("text", msg.get("content")))
                    ));
                }
            }
            contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMsg))
            ));

            boolean grounding = request.getAdditionalParams() != null
                    && Boolean.TRUE.equals(request.getAdditionalParams().get("googleSearchGrounding"));

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
            if (!grounding) {
                generationConfig.put("temperature", request.getTemperature());
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("contents", contents);
            requestBody.put("generationConfig", generationConfig);
            requestBody.put("systemInstruction", request.getSystemPrompt() != null
                    ? Map.of("parts", List.of(Map.of("text", request.getSystemPrompt())))
                    : null);
            if (grounding) {
                requestBody.put("tools", List.of(Map.of("googleSearchRetrieval", Map.of())));
            }

            JsonNode response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                return LLMResponse.error("Empty response from Gemini");
            }

            JsonNode candidate = response.get("candidates").get(0);
            String content = candidate.get("content").get("parts").get(0).get("text").asText();
            JsonNode usageMetadata = response.get("usageMetadata");
            int promptTokens = usageMetadata != null ? usageMetadata.get("promptTokenCount").asInt() : 0;
            int completionTokens = usageMetadata != null ? usageMetadata.get("candidatesTokenCount").asInt() : 0;

            if (grounding) {
                content = appendGroundingSources(content, response);
            }

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            return LLMResponse.error("Gemini error: " + e.getMessage());
        }
    }

    @Override
    public LLMResponse chatWithStream(LLMRequest request, Consumer<String> onChunk) {
        LLMResponse response = chat(request);
        if (onChunk != null && response != null && response.isSuccess() && response.getContent() != null) {
            onChunk.accept(response.getContent());
        }
        return response;
    }

    public List<Double> embed(String text) {
        return embed(text, "text-embedding-004");
    }

    public List<Double> embed(String text, String model) {
        try {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            String url = API_URL + "/" + model + ":embedContent?key=" + apiKey;            Map<String, Object> requestBody = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text)))
            );

            JsonNode response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response == null || !response.has("embedding")) {
                log.warn("Gemini embed returned no embedding");
                return List.of();
            }
            List<Double> values = new ArrayList<>();
            for (JsonNode v : response.path("embedding").path("values")) {
                values.add(v.asDouble());
            }
            return values;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.warn("Gemini embed failed: {} — body: {}", e.getMessage(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("Gemini embed failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String appendGroundingSources(String content, JsonNode response) {
        JsonNode groundingChunks = response.path("groundingMetadata").path("groundingChunks");
        if (!groundingChunks.isArray() || groundingChunks.isEmpty()) {
            return content;
        }
        List<String> sources = new ArrayList<>();
        for (JsonNode chunk : groundingChunks) {
            JsonNode web = chunk.path("web");
            String uri = web.path("uri").asText();
            String title = web.path("title").asText();
            if (!uri.isBlank()) {
                sources.add("- " + (title.isBlank() ? uri : title + " (" + uri + ")"));
            }
        }
        if (sources.isEmpty()) {
            return content;
        }
        return content + "\n\nSources:\n" + String.join("\n", sources);
    }

    @Override
    public boolean testConnection() {
        return testConnection(null);
    }

    @Override
    public boolean testConnection(String model) {
        try {
            List<String> available = listChatModels();
            if (available.isEmpty()) {
                log.warn("Gemini connection test failed: no generateContent-capable model available for this key");
                return false;
            }
            if (model != null && !model.isBlank() && probe(model)) {
                return true;
            }
            for (String candidate : available) {
                if (probe(candidate)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Gemini connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean probe(String model) {
        LLMRequest request = new LLMRequest("You are a test bot.", "Reply with just: OK");
        request.setModel(model);
        LLMResponse resp = chat(request);
        return resp != null && resp.isSuccess();
    }

    private List<String> listChatModels() {
        List<String> result = new ArrayList<>();
        try {
            JsonNode response = webClient.get()
                .uri("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            if (response != null && response.hasNonNull("models")) {
                for (JsonNode m : response.get("models")) {
                    String id = m.path("name").asText().replace("models/", "");
                    if (id.startsWith("gemini-") && supportsGenerateContent(m)) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini model discovery failed: {}", e.getMessage());
        }
        return result;
    }

    public List<String> listEmbedModels() {
        List<String> result = new ArrayList<>();
        try {
            JsonNode response = webClient.get()
                .uri("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            if (response != null && response.hasNonNull("models")) {
                for (JsonNode m : response.get("models")) {
                    String id = m.path("name").asText().replace("models/", "");
                    if (supportsMethod(m, "embedContent")) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini embedding model discovery failed: {}", e.getMessage());
        }
        return result;
    }

    private boolean supportsGenerateContent(JsonNode model) {
        return supportsMethod(model, "generateContent");
    }

    private boolean supportsMethod(JsonNode model, String methodName) {
        JsonNode methods = model.get("supportedGenerationMethods");
        if (methods != null) {
            for (JsonNode method : methods) {
                if (method.asText().contains(methodName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
