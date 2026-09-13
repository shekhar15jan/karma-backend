package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Injects the Karma brand logo (bottom-right) into scene specs so that the
 * {@code /render} stills and the {@code /video} frames are pixel-identical.
 * The logo is embedded as a base64 data URI so the remote renderer container
 * never needs access to the backend filesystem.
 */
public final class BrandingSupport {

    private static final Logger log = LoggerFactory.getLogger(BrandingSupport.class);

    private static final String[] LOGO_CANDIDATES = {
        "/app/logo.png",
        "D:/CodeAtCloudeAI/Video-automation/logo.png",
    };

    private static volatile String cachedLogoDataUri;

    private BrandingSupport() {}

    /** Returns a base64 data URI of the brand logo, or null when unavailable. */
    public static String logoDataUri() {
        if (cachedLogoDataUri != null) return cachedLogoDataUri;
        synchronized (BrandingSupport.class) {
            if (cachedLogoDataUri != null) return cachedLogoDataUri;
            for (String candidate : LOGO_CANDIDATES) {
                File f = new File(candidate);
                if (f.exists() && f.isFile()) {
                    try {
                        byte[] bytes = Files.readAllBytes(f.toPath());
                        String b64 = Base64.getEncoder().encodeToString(bytes);
                        cachedLogoDataUri = "data:image/png;base64," + b64;
                        return cachedLogoDataUri;
                    } catch (Exception e) {
                        log.warn("Could not read logo {}: {}", candidate, e.getMessage());
                    }
                }
            }
            return null;
        }
    }

    /**
     * Adds {@code branding.logo} (bottom-right) to a scene spec object when no
     * branding is already present. Mutates the given node in place.
     */
    public static void applyBranding(ObjectNode spec, ObjectMapper objectMapper) {
        if (spec == null) return;
        ObjectNode branding = spec.has("branding") && spec.get("branding").isObject()
                ? (ObjectNode) spec.get("branding")
                : objectMapper.createObjectNode();
        String logo = logoDataUri();
        if (logo != null && !branding.has("logo")) {
            branding.put("logo", logo);
        }
        if (!branding.has("logoPosition")) {
            branding.put("logoPosition", "bottom-right");
        }
        if (logo != null || spec.has("branding")) {
            spec.set("branding", branding);
        }
    }
}
