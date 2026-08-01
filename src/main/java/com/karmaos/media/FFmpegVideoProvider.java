package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
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

        if (request.getImageUrls() == null || request.getImageUrls().isEmpty()) {
            return VideoResponse.error("No images provided for video generation");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("karma-video-");
            Path output = tempDir.resolve("output.mp4");

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

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String processOutput = readStream(process.getInputStream());
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return VideoResponse.error("FFmpeg timed out after 5 minutes");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg failed with exit code {}: {}", exitCode, processOutput);
                return VideoResponse.error("FFmpeg failed (exit " + exitCode + "): " + truncate(processOutput, 500));
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

    @Override
    public boolean testConnection() {
        return findFFmpeg() != null;
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
