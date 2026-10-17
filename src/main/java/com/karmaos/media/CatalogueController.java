package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiException;
import com.karmaos.common.ApiResponse;
import com.karmaos.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Read-only proxy for the renderer's frame/widget/icon catalogue (src/catalogue/ in
 * karma-renderer). The catalogue is the single source of truth for what a scene can
 * ask for; this exists purely so the frontend has something to read it from — the
 * render pipeline itself never goes through HTTP for this, it imports the catalogue
 * in-process.
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/catalogue")
public class CatalogueController {

    private final String rendererUrl;
    private final ObjectMapper objectMapper;

    public CatalogueController(@Value("${RENDERER_URL:http://localhost:3000}") String rendererUrl,
                                ObjectMapper objectMapper) {
        this.rendererUrl = rendererUrl;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<JsonNode>> get() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URI(rendererUrl.replaceAll("/+$", "") + "/catalogue").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw ErrorCode.PROVIDER_ERROR.exception("Renderer returned HTTP " + code);
            }
            try (InputStream is = conn.getInputStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(body);
                return ResponseEntity.ok(ApiResponse.ok(node));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ErrorCode.PROVIDER_ERROR.exception("Renderer unreachable: " + e.getMessage());
        }
    }

    /**
     * Dry-run slot assignment: given a proposed frame id + scene components per scene,
     * asks the renderer whether the frame's slot contract is actually satisfiable
     * (including its internal fallback walk), without rendering anything. Used by the
     * Director Agent to confirm its choices before they're accepted into the script.
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<JsonNode>> validate(@RequestBody JsonNode body) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URI(rendererUrl.replaceAll("/+$", "") + "/catalogue/validate").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(8000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(body));
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String responseBody;
            try (InputStream is = stream) {
                responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (code < 200 || code >= 300) {
                throw ErrorCode.PROVIDER_ERROR.exception("Renderer returned HTTP " + code + ": " + responseBody);
            }
            JsonNode node = objectMapper.readTree(responseBody);
            return ResponseEntity.ok(ApiResponse.ok(node));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ErrorCode.PROVIDER_ERROR.exception("Renderer unreachable: " + e.getMessage());
        }
    }
}
