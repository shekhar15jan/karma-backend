package com.karmaos.artifact;

import com.karmaos.common.ErrorCode;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.Project;
import com.karmaos.project.ProjectRepository;
import com.karmaos.workspace.Workspace;
import com.karmaos.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Persists artifact bytes to a configurable output directory on disk.
 * <p>
 * The storage root is a server-side directory (default {@code /data/artifacts},
 * overridable via STORAGE_LOCATION). Users may pick a per-mission or per-workspace
 * folder, either as a relative path ({@code clientA/video}), a full Windows path under
 * the mounted host root ({@code D:\...\auto-gen\clientA\video}) or a container path under
 * the storage root ({@code /data/artifacts/clientA/video}). Everything outside the mounted
 * root is rejected.
 */
@Service
public class ArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStorageService.class);

    private static final long MAX_ARTIFACT_BYTES = 512L * 1024 * 1024;

    private final MissionRepository missionRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final String storageRoot;
    private final String hostRoot;

    public ArtifactStorageService(MissionRepository missionRepository,
                                  ProjectRepository projectRepository,
                                  WorkspaceRepository workspaceRepository,
                                  @Value("${app.artifact.storage-root:${STORAGE_LOCATION:/data/artifacts}}") String storageRoot,
                                  @Value("${ARTIFACT_HOST_ROOT:}") String hostRoot) {
        this.missionRepository = missionRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.storageRoot = trimTrailingSlash(storageRoot);
        this.hostRoot = trimTrailingSlash(hostRoot);
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public String getHostRoot() {
        return hostRoot;
    }

    /**
     * Normalizes + validates a user-supplied output folder into a relative folder under the
     * storage root. Returns null for a blank input; throws for anything outside the root.
     */
    public String normalizeFolder(String input) {
        if (input == null) return null;
        String f = input.trim().replace('\\', '/');
        while (f.startsWith("./")) {
            f = f.substring(2);
        }
        if (f.isBlank()) return null;

        String relative = stripPrefix(f, storageRoot);
        if (relative != null) return validateRelative(relative);

        if (!hostRoot.isBlank()) {
            relative = stripPrefix(f, hostRoot);
            if (relative != null) return validateRelative(relative);
        }

        if (f.startsWith("/") || f.matches("^[A-Za-z]:.*")) {
            throw ErrorCode.BAD_REQUEST.exception(
                    "Output folder must be under the mounted output root (" + displayRoot() + ")");
        }
        return validateRelative(f);
    }

    /**
     * Resolves the effective relative folder for a mission: mission override -> workspace
     * default -> fallback to the mission id.
     */
    public String resolveFolder(Mission mission) {
        if (mission != null) {
            String folder = normalizeFolder(mission.getOutputDirectory());
            if (folder != null) return folder;
            folder = normalizeFolder(resolveWorkspaceDefault(mission.getProjectId()));
            if (folder != null) return folder;
            if (mission.getId() != null) return mission.getId().toString();
        }
        return "artifacts";
    }

    /**
     * Writes the artifact's bytes to disk when possible and points storageLocation at the file.
     * No-op when the source cannot be resolved (keeps current DB/URL behavior).
     */
    public void persist(Artifact artifact, UUID missionId) {
        if (artifact == null) {
            return;
        }
        String source = artifact.getStorageLocation();
        if (source == null || source.isBlank()) {
            source = artifact.getContentText();
        }
        if (source == null || source.isBlank()) {
            return;
        }
        byte[] bytes = toBytes(source, artifact.getContentType());
        if (bytes == null || bytes.length == 0) {
            return;
        }
        if (bytes.length > MAX_ARTIFACT_BYTES) {
            log.warn("Skipping disk write for artifact {} ({} bytes exceeds {} limit)",
                    artifact.getName(), bytes.length, MAX_ARTIFACT_BYTES);
            return;
        }

        Mission mission = missionId == null ? null : missionRepository.findById(missionId).orElse(null);
        String folder = resolveFolder(mission);
        Path dir = Path.of(storageRoot, folder);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw ErrorCode.INTERNAL_ERROR.exception("Cannot create artifact output directory: " + e.getMessage());
        }

        String fileName = sanitizeName(artifact.getName())
                + "-" + UUID.randomUUID().toString().substring(0, 8)
                + extensionFor(artifact.getContentType());
        Path file = dir.resolve(fileName);
        try {
            Files.write(file, bytes);
        } catch (IOException e) {
            throw ErrorCode.INTERNAL_ERROR.exception("Failed to write artifact to disk: " + e.getMessage());
        }

        artifact.setStorageLocation(file.toUri().toString());
        artifact.setSize(bytes.length);
        artifact.setChecksum(sha256Hex(bytes));
    }

    private String resolveWorkspaceDefault(UUID projectId) {
        if (projectId == null) return null;
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return null;
        Workspace workspace = workspaceRepository.findById(project.getWorkspaceId()).orElse(null);
        return workspace != null ? workspace.getDefaultOutputDirectory() : null;
    }

    private String stripPrefix(String value, String prefix) {
        if (prefix == null || prefix.isBlank()) return null;
        String v = value;
        String p = trimTrailingSlash(prefix);
        if (v.equalsIgnoreCase(p)) return "";
        if (v.regionMatches(true, 0, p, 0, p.length()) && v.length() > p.length() && v.charAt(p.length()) == '/') {
            return v.substring(p.length() + 1);
        }
        return null;
    }

    private String validateRelative(String relative) {
        String r = (relative == null ? "" : relative.trim()).replace('\\', '/');
        while (r.startsWith("./")) {
            r = r.substring(2);
        }
        r = r.replaceAll("//+", "/");
        if (r.isBlank()) return null;
        if (r.startsWith("/") || r.matches("^[A-Za-z]:.*")) {
            throw ErrorCode.BAD_REQUEST.exception("Output folder must be under the mounted output root (" + displayRoot() + ")");
        }
        for (String part : r.split("/")) {
            if (part.equals("..") || part.equals(".") || part.isBlank()) {
                throw ErrorCode.BAD_REQUEST.exception("Output folder contains invalid segments: " + relative);
            }
        }
        return r;
    }

    private byte[] toBytes(String source, String contentType) {
        if (source == null || source.isBlank()) return null;
        String s = source.trim();
        if (s.startsWith("data:")) {
            int comma = s.indexOf(',');
            if (comma < 0) return null;
            try {
                return Base64.getDecoder().decode(s.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (s.startsWith("file:")) {
            try {
                return Files.readAllBytes(Path.of(URI.create(s)));
            } catch (Exception e) {
                return null;
            }
        }
        if (s.startsWith("http://") || s.startsWith("https://")) {
            try (InputStream in = URI.create(s).toURL().openStream()) {
                return in.readAllBytes();
            } catch (Exception e) {
                return null;
            }
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private String extensionFor(String contentType) {
        if (contentType == null) return ".md";
        String ct = contentType.toLowerCase();
        if (ct.contains("video/mp4")) return ".mp4";
        if (ct.contains("video/webm")) return ".webm";
        if (ct.contains("audio/mpeg")) return ".mp3";
        if (ct.contains("audio/wav")) return ".wav";
        if (ct.contains("audio/ogg")) return ".ogg";
        if (ct.contains("image/png")) return ".png";
        if (ct.contains("image/jpeg")) return ".jpg";
        if (ct.contains("image/webp")) return ".webp";
        if (ct.contains("application/json")) return ".json";
        if (ct.contains("text/html")) return ".html";
        if (ct.contains("text/csv")) return ".csv";
        if (ct.contains("text/plain")) return ".txt";
        return ".md";
    }

    private String sanitizeName(String name) {
        String safe = (name == null ? "artifact" : name)
                .trim()
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-");
        if (safe.isBlank()) safe = "artifact";
        return safe;
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String displayRoot() {
        return hostRoot.isBlank() ? storageRoot : hostRoot;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return "";
        String v = value.trim().replace('\\', '/');
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}
