package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FFmpegVideoProvider implements VideoProvider {

    private static final Logger log = LoggerFactory.getLogger(FFmpegVideoProvider.class);
    private static final String[] FFMPEG_PATHS = {
        "ffmpeg",
        System.getProperty("user.home") + "\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg.Essentials_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-8.1.1-essentials_build\\bin\\ffmpeg.exe",
        "C:\\tools\\ffmpeg\\ffmpeg.exe",
        "C:\\ffmpeg\\bin\\ffmpeg.exe",
        System.getProperty("user.home") + "\\scoop\\apps\\ffmpeg\\current\\bin\\ffmpeg.exe"
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    @Override
    public String getName() { return "FFmpeg"; }

    @Override
    public String getDefaultModel() { return "ffmpeg-local"; }

    @Override
    public List<String> getAvailableModels() { return List.of("ffmpeg-local"); }

    @Override
    public VideoResponse generate(VideoRequest request) {
        String ffmpegPath = findFFmpeg();
        if (ffmpegPath == null) {
            return VideoResponse.error("FFmpeg not found. Install FFmpeg and add to PATH or C:\\tools\\ffmpeg\\");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("karma-video-");
            Path output = tempDir.resolve("output.mp4");

            if (request.getScenes() != null && !request.getScenes().isEmpty()) {
                return generateFromScenes(ffmpegPath, request, tempDir, output);
            }

            if (request.getImageUrls() == null || request.getImageUrls().isEmpty()) {
                return VideoResponse.error("No images or scenes provided for video generation");
            }

            List<Path> localImages = downloadImages(request.getImageUrls(), tempDir);
            if (localImages.isEmpty()) {
                return VideoResponse.error("No usable images could be downloaded for video generation");
            }
            Path localAudio = null;
            if (request.getAudioUrl() != null) {
                try {
                    localAudio = downloadFile(request.getAudioUrl(), tempDir, "audio");
                } catch (Exception e) {
                    log.warn("Audio download failed, continuing without audio: {}", e.getMessage());
                }
            }

            int imageDuration = calculateImageDuration(localImages.size(), request.getFps(), request.getTransitionDuration());

            Path concatFile = createConcatFile(localImages, imageDuration, tempDir);

            List<String> cmd = buildFFmpegCommand(ffmpegPath, concatFile, localAudio, output, request);
            log.info("FFmpeg command: {}", String.join(" ", cmd));

            if (!runFFmpeg(cmd)) {
                return VideoResponse.error("FFmpeg failed");
            }

            if (!Files.exists(output) || Files.size(output) == 0) {
                return VideoResponse.error("FFmpeg produced no output file");
            }

            int duration = getVideoDuration(ffmpegPath, output);
            String videoUrl = output.toUri().toString();

            log.info("Video generated: {} ({} seconds)", videoUrl, duration);
            return VideoResponse.ok(videoUrl, duration);

        } catch (Exception e) {
            log.error("Video generation failed", e);
            return VideoResponse.error("Video generation failed: " + e.getMessage());
        }
    }

    private VideoResponse generateFromScenes(String ffmpegPath, VideoRequest request,
                                             Path tempDir, Path output) {
        try {
            int fps = request.getFps() > 0 ? request.getFps() : 30;
            int width = parseWidth(request.getResolution());
            int height = parseHeight(request.getResolution());

            List<Path> segmentFiles = new ArrayList<>();
            List<Path> allAudios = new ArrayList<>();
            int sceneIdx = 0;
            for (VideoScene scene : request.getScenes()) {
                sceneIdx++;
                if (scene.getText() == null || scene.getText().isBlank()) continue;

                String title = request.getTitle() != null ? request.getTitle() : "";
                Path frame = renderSceneFrame(scene, title, width, height, tempDir, sceneIdx);
                int duration = scene.getDurationSeconds() > 0
                    ? scene.getDurationSeconds()
                    : estimateDurationSeconds(scene.getText());

                Path audio = null;
                if (scene.getAudioUrl() != null && !scene.getAudioUrl().isBlank()) {
                    try {
                        audio = downloadFile(scene.getAudioUrl(), tempDir, "scene" + sceneIdx + "_audio");
                    } catch (Exception e) {
                        log.warn("Scene {} audio download failed, rendering silently: {}", sceneIdx, e.getMessage());
                    }
                }
                if (audio != null && Files.exists(audio)) {
                    allAudios.add(audio);
                }

                Path segment = tempDir.resolve("seg_" + sceneIdx + ".mp4");
                if (!buildSceneSegment(ffmpegPath, frame, audio, duration, fps, segment)) {
                    log.error("Failed to build scene {} segment", sceneIdx);
                    continue;
                }
                segmentFiles.add(segment);
            }

            if (segmentFiles.isEmpty()) {
                return VideoResponse.error("No scenes could be rendered");
            }

            Path concatFile = createSegmentConcatFile(segmentFiles, tempDir);
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegPath);
            cmd.add("-y");
            cmd.add("-f");
            cmd.add("concat");
            cmd.add("-safe");
            cmd.add("0");
            cmd.add("-i");
            cmd.add(concatFile.toAbsolutePath().toString());

            Path mixedAudio = null;
            if (!allAudios.isEmpty()) {
                try {
                    mixedAudio = mixAudios(ffmpegPath, allAudios, tempDir.resolve("mixed.mp3"));
                    if (mixedAudio != null && Files.exists(mixedAudio)) {
                        cmd.add("-i");
                        cmd.add(mixedAudio.toAbsolutePath().toString());
                    }
                } catch (Exception e) {
                    log.warn("Audio mix failed, producing silent video: {}", e.getMessage());
                }
            }

            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("medium");
            cmd.add("-crf");
            cmd.add("23");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");

            if (mixedAudio != null && Files.exists(mixedAudio)) {
                cmd.add("-c:a");
                cmd.add("aac");
                cmd.add("-b:a");
                cmd.add("128k");
                cmd.add("-shortest");
            }

            cmd.add(output.toAbsolutePath().toString());
            log.info("Scene concat command: {}", String.join(" ", cmd));

            if (!runFFmpeg(cmd)) {
                return VideoResponse.error("FFmpeg scene assembly failed");
            }
            if (!Files.exists(output) || Files.size(output) == 0) {
                return VideoResponse.error("FFmpeg produced no output file");
            }

            int duration = getVideoDuration(ffmpegPath, output);
            String videoUrl = output.toUri().toString();
            log.info("Scene-based video generated: {} ({} seconds)", videoUrl, duration);
            return VideoResponse.ok(videoUrl, duration);
        } catch (Exception e) {
            log.error("Scene-based video generation failed", e);
            return VideoResponse.error("Scene-based video generation failed: " + e.getMessage());
        }
    }

    private Path renderSceneFrame(VideoScene scene, String title, int width, int height,
                                  Path tempDir, int idx) throws IOException {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (scene.getImageUrl() != null && !scene.getImageUrl().isBlank()) {
            try {
                Path bg = tempDir.resolve("bg_" + idx + ".png");
                downloadFile(scene.getImageUrl(), bg);
                BufferedImage bgImg = ImageIO.read(bg.toFile());
                if (bgImg != null) {
                    g.drawImage(bgImg, 0, 0, width, height, null);
                    g.setColor(new Color(0, 0, 0, 150));
                    g.fillRect(0, 0, width, height);
                }
            } catch (Exception e) {
                log.warn("Scene {} background failed, using gradient: {}", idx, e.getMessage());
                paintGradient(g, width, height, idx);
            }
        } else {
            paintGradient(g, width, height, idx);
        }

        if (title != null && !title.isBlank()) {
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(28, width / 42)));
            g.setColor(new Color(255, 255, 255, 220));
            drawCenteredText(g, title, width, (int) (height * 0.10), width - 200);
        }

        String text = scene.getText();
        if (text != null && !text.isBlank()) {
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(32, width / 32)));
            g.setColor(Color.WHITE);
            drawWrappedCenteredText(g, text, width, (int) (height * 0.42), (int) (height * 0.5), width - 240);
        }

        g.dispose();
        Path out = tempDir.resolve("frame_" + idx + ".png");
        ImageIO.write(canvas, "png", out.toFile());
        return out;
    }

    private void paintGradient(Graphics2D g, int width, int height, int idx) {
        int base = (idx * 60) % 360;
        Color c1 = Color.getHSBColor(base / 360f, 0.45f, 0.55f);
        Color c2 = Color.getHSBColor(((base + 60) % 360) / 360f, 0.55f, 0.30f);
        g.setPaint(new GradientPaint(0, 0, c1, width, height, c2));
        g.fillRect(0, 0, width, height);
    }

    private void drawCenteredText(Graphics2D g, String text, int width, int y, int maxWidth) {
        Font font = g.getFont();
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout layout = new TextLayout(text, font, frc);
        Rectangle2D bounds = layout.getBounds();
        float x = (float) ((width - bounds.getWidth()) / 2 - bounds.getX());
        layout.draw(g, x, y);
    }

    private void drawWrappedCenteredText(Graphics2D g, String text, int width, int centerY,
                                         int maxHeight, int maxWidth) {
        List<String> lines = wrapText(text, maxWidth, g.getFontMetrics());
        int lineHeight = g.getFontMetrics().getHeight();
        int totalHeight = lines.size() * lineHeight;
        if (totalHeight > maxHeight) {
            while (totalHeight > maxHeight && lines.size() > 1) {
                lines.remove(lines.size() - 1);
                totalHeight = lines.size() * lineHeight;
            }
        }
        int y = centerY - totalHeight / 2 + g.getFontMetrics().getAscent();
        for (String line : lines) {
            drawCenteredText(g, line, width, y, maxWidth);
            y += lineHeight;
        }
    }

    private List<String> wrapText(String text, int maxWidth, FontMetrics fm) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            if (paragraph.isBlank()) continue;
            String[] words = paragraph.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (line.length() > 0) result.add(line.toString());
        }
        return result;
    }

    private boolean buildSceneSegment(String ffmpegPath, Path frame, Path audio,
                                      int duration, int fps, Path segment) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegPath);
            cmd.add("-y");
            cmd.add("-loop");
            cmd.add("1");
            cmd.add("-framerate");
            cmd.add(String.valueOf(fps));
            cmd.add("-t");
            cmd.add(String.valueOf(Math.max(1, duration)));
            cmd.add("-i");
            cmd.add(frame.toAbsolutePath().toString());

            boolean hasAudio = audio != null && Files.exists(audio) && Files.size(audio) > 0;
            if (hasAudio) {
                cmd.add("-i");
                cmd.add(audio.toAbsolutePath().toString());
            }

            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("medium");
            cmd.add("-crf");
            cmd.add("23");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-r");
            cmd.add(String.valueOf(fps));
            if (hasAudio) {
                cmd.add("-c:a");
                cmd.add("aac");
                cmd.add("-b:a");
                cmd.add("128k");
                cmd.add("-shortest");
            }
            cmd.add(segment.toAbsolutePath().toString());

            log.info("Scene segment command: {}", String.join(" ", cmd));
            return runFFmpeg(cmd);
        } catch (Exception e) {
            log.error("Scene segment build failed", e);
            return false;
        }
    }

    private Path mixAudios(String ffmpegPath, List<Path> audios, Path output) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegPath);
            cmd.add("-y");
            for (Path a : audios) {
                cmd.add("-i");
                cmd.add(a.toAbsolutePath().toString());
            }
            cmd.add("-filter_complex");
            StringBuilder concat = new StringBuilder();
            for (int i = 0; i < audios.size(); i++) {
                concat.append("[").append(i).append(":0]");
            }
            concat.append("concat=n=").append(audios.size()).append(":v=0:a=1[out]");
            cmd.add(concat.toString());
            cmd.add("-map");
            cmd.add("[out]");
            cmd.add("-c:a");
            cmd.add("libmp3lame");
            cmd.add("-q:a");
            cmd.add("2");
            cmd.add(output.toAbsolutePath().toString());
            log.info("Audio mix command: {}", String.join(" ", cmd));
            return runFFmpeg(cmd) ? output : null;
        } catch (Exception e) {
            log.error("Audio mix failed", e);
            return null;
        }
    }

    private int estimateDurationSeconds(String text) {
        if (text == null || text.isBlank()) return 5;
        int words = text.split("\\s+").length;
        return Math.max(5, (int) Math.ceil(words / 2.3));
    }

    @Override
    public boolean testConnection() {
        return findFFmpeg() != null;
    }

    private boolean runFFmpeg(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String processOutput = readStream(process.getInputStream());
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg timed out after 5 minutes");
                return false;
            }
            if (process.exitValue() != 0) {
                log.error("FFmpeg failed with exit code {}: {}", process.exitValue(), truncate(processOutput, 800));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("FFmpeg execution failed", e);
            return false;
        }
    }

    private String findFFmpeg() {
        for (String path : FFMPEG_PATHS) {
            try {
                ProcessBuilder pb = new ProcessBuilder(path, "-version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean ok = p.waitFor(5, TimeUnit.SECONDS);
                if (ok && p.exitValue() == 0) {
                    return path;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<Path> downloadImages(List<String> urls, Path tempDir) {
        List<Path> images = new ArrayList<>();
        int idx = 0;
        for (String url : urls) {
            String ext = guessExtension(url, ".png");
            Path target = tempDir.resolve("img_" + idx + ext);
            try {
                downloadFile(url, target);
                images.add(target);
                idx++;
            } catch (Exception e) {
                log.warn("Skipping undownloadable image {}: {}", url, e.getMessage());
            }
        }
        return images;
    }

    private Path downloadFile(String url, Path tempDir, String prefix) throws Exception {
        String ext = guessExtension(url, ".bin");
        Path target = tempDir.resolve(prefix + ext);
        downloadFile(url, target);
        return target;
    }

    private void downloadFile(String url, Path target) throws Exception {
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) throw new IOException("Malformed data URI");
            String base64 = url.substring(comma + 1);
            byte[] bytes = Base64.getDecoder().decode(base64);
            Files.write(target, bytes);
            return;
        }
        if (url.startsWith("file:")) {
            Path source = Path.of(URI.create(url));
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to download " + url + ": HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String concatPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace("'", "'\\''");
    }

    private String guessExtension(String url, String defaultExt) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String ext = path.substring(dot).toLowerCase();
            if (ext.length() <= 5) return ext;
        }
        return defaultExt;
    }

    private int calculateImageDuration(int imageCount, int fps, double transitionDuration) {
        if (imageCount <= 1) return 5;
        return Math.max(2, (int) Math.ceil(5.0 / imageCount));
    }

    private Path createConcatFile(List<Path> images, int durationPerImage, Path tempDir) throws IOException {
        Path concatFile = tempDir.resolve("concat.txt");
        StringBuilder sb = new StringBuilder();
        for (Path img : images) {
            sb.append("file '").append(concatPath(img)).append("'\n");
            sb.append("duration ").append(durationPerImage).append("\n");
        }
        if (!images.isEmpty()) {
            sb.append("file '").append(concatPath(images.get(images.size() - 1))).append("'\n");
        }
        Files.writeString(concatFile, sb.toString());
        return concatFile;
    }

    private Path createSegmentConcatFile(List<Path> segments, Path tempDir) throws IOException {
        Path concatFile = tempDir.resolve("concat_segments.txt");
        StringBuilder sb = new StringBuilder();
        for (Path seg : segments) {
            sb.append("file '").append(concatPath(seg)).append("'\n");
        }
        Files.writeString(concatFile, sb.toString());
        return concatFile;
    }

    private List<String> buildFFmpegCommand(String ffmpegPath, Path concatFile, Path audio,
                                             Path output, VideoRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-f");
        cmd.add("concat");
        cmd.add("-safe");
        cmd.add("0");
        cmd.add("-i");
        cmd.add(concatFile.toAbsolutePath().toString());

        if (audio != null && Files.exists(audio)) {
            cmd.add("-i");
            cmd.add(audio.toAbsolutePath().toString());
        }

        cmd.add("-vf");
        cmd.add("scale=" + request.getResolution().replace("x", ":") + ":force_original_aspect_ratio=decrease,pad=" + request.getResolution().replace("x", ":") + ":(ow-iw)/2:(oh-ih)/2");

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("medium");
        cmd.add("-crf");
        cmd.add("23");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-r");
        cmd.add(String.valueOf(request.getFps()));

        if (audio != null && Files.exists(audio)) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
            cmd.add("-shortest");
        }

        cmd.add(output.toAbsolutePath().toString());
        return cmd;
    }

    private int getVideoDuration(String ffmpegPath, Path videoFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-i", videoFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = readStream(p.getInputStream());
            p.waitFor(10, TimeUnit.SECONDS);

            for (String line : output.split("\n")) {
                if (line.contains("Duration:")) {
                    String dur = line.substring(line.indexOf("Duration:") + 10).trim();
                    String[] parts = dur.split(",")[0].trim().split(":");
                    if (parts.length == 3) {
                        int h = Integer.parseInt(parts[0]);
                        int m = Integer.parseInt(parts[1]);
                        double s = Double.parseDouble(parts[2]);
                        return h * 3600 + m * 60 + (int) Math.ceil(s);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine video duration", e);
        }
        return 0;
    }

    private int parseWidth(String resolution) {
        if (resolution != null && resolution.matches("\\d+x\\d+")) {
            return Integer.parseInt(resolution.split("x")[0]);
        }
        return 1920;
    }

    private int parseHeight(String resolution) {
        if (resolution != null && resolution.matches("\\d+x\\d+")) {
            return Integer.parseInt(resolution.split("x")[1]);
        }
        return 1080;
    }

    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
