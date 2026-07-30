package com.karmaos.knowledge;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KarmaDocService {

    private static final Logger log = LoggerFactory.getLogger(KarmaDocService.class);
    private static final String DOCS_DIR = "../docs";

    private final Map<String, DocEntry> documents = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        loadDocuments();
    }

    public void loadDocuments() {
        documents.clear();
        Path docsPath = Paths.get(DOCS_DIR).toAbsolutePath().normalize();
        if (!Files.exists(docsPath)) {
            docsPath = Paths.get("docs").toAbsolutePath().normalize();
        }
        if (!Files.exists(docsPath)) {
            log.warn("Docs directory not found at {} or ../docs", docsPath);
            return;
        }
        try {
            Files.walk(docsPath)
                .filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .forEach(this::loadFile);
            log.info("Loaded {} documentation files from {}", documents.size(), docsPath);
        } catch (IOException e) {
            log.error("Failed to walk docs directory", e);
        }
    }

    private void loadFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String fileName = filePath.getFileName().toString();
            String title = deriveTitle(fileName, content);
            String category = deriveCategory(fileName);
            documents.put(fileName, new DocEntry(fileName, title, category, content));
        } catch (IOException e) {
            log.warn("Failed to read {}", filePath, e);
        }
    }

    private String deriveTitle(String fileName, String content) {
        String firstLine = content.lines().findFirst().orElse("").trim();
        if (firstLine.startsWith("# ")) {
            return firstLine.substring(2).trim();
        }
        String name = fileName.replaceAll("(?i)\\.md$", "").trim();
        if (name.startsWith("DOC-")) {
            name = name.replaceFirst("^DOC-\\d+\\s*-*\\s*", "");
        }
        return name.replaceAll("[-_]", " ").trim();
    }

    private String deriveCategory(String fileName) {
        String upper = fileName.toUpperCase();
        if (upper.contains("WHITEPAPER") || upper.contains("VISION") || upper.contains("CONSTITUTION"))
            return "VISION";
        if (upper.contains("BRD") || upper.contains("PRD") || upper.contains("SRS"))
            return "REQUIREMENTS";
        if (upper.contains("ARCHITECTURE") || upper.contains("SAD") || upper.contains("DDS") || upper.contains("DESIGN"))
            return "ARCHITECTURE";
        if (upper.contains("API") || upper.contains("INTEGRATION") || upper.contains("DATA"))
            return "INTEGRATION";
        if (upper.contains("UI") || upper.contains("UX"))
            return "UI_UX";
        if (upper.contains("SECURITY") || upper.contains("DEVOPS") || upper.contains("INFRASTRUCTURE"))
            return "OPERATIONS";
        if (upper.contains("VERIFICATION") || upper.contains("VALIDATION") || upper.contains("TEST"))
            return "QUALITY";
        if (upper.contains("DEPLOYMENT") || upper.contains("OPERATIONS") || upper.contains("GUIDE"))
            return "OPERATIONS";
        if (upper.contains("ENGINEERING") || upper.contains("REFERENCE") || upper.contains("MANUAL"))
            return "REFERENCE";
        if (upper.contains("TECHNOLOGY") || upper.contains("DECISIONS"))
            return "ARCHITECTURE";
        if (upper.contains("BUILD") || upper.contains("RUN") || upper.contains("CONFIGURATION"))
            return "OPERATIONS";
        if (upper.contains("GOOGLE") || upper.contains("STICK") || upper.contains("PROMPT"))
            return "PROMPTS";
        if (upper.contains("VIDEO") || upper.contains("FLOW"))
            return "FLOWS";
        return "GENERAL";
    }

    public String getCombinedContent() {
        StringBuilder sb = new StringBuilder();
        for (DocEntry entry : documents.values()) {
            sb.append("=== ").append(entry.title).append(" ===\n");
            sb.append(entry.content.strip()).append("\n\n");
        }
        return sb.toString();
    }

    public String getSummarizedContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("KARMA OS Documentation:\n\n");
        for (DocEntry entry : documents.values()) {
            String summary = summarize(entry.content, 200);
            sb.append("- ").append(entry.title).append(" (").append(entry.category).append("): ");
            sb.append(summary).append("\n");
        }
        return sb.toString();
    }

    public List<DocEntry> getAllDocuments() {
        return List.copyOf(documents.values());
    }

    public DocEntry getDocument(String fileName) {
        return documents.get(fileName);
    }

    public int getDocumentCount() {
        return documents.size();
    }

    private String summarize(String content, int maxChars) {
        String clean = content.replaceAll("#+\\s*", "")
                .replaceAll("\\*\\*|\\*|`", "")
                .replaceAll("\n+", " ")
                .trim();
        if (clean.length() <= maxChars) return clean;
        return clean.substring(0, maxChars).replaceAll("\\s+\\S*$", "") + "...";
    }

    public record DocEntry(String fileName, String title, String category, String content) {}
}
