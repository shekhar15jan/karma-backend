package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Fallback image generator that produces a real PNG locally via Java2D, so the
 * video pipeline can assemble a playable MP4 without external provider API keys.
 * The returned image URL is a local {@code file:} URI handled by FFmpegVideoProvider.
 */
public class LocalImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalImageProvider.class);
    private static final Path DEFAULT_DIR = Paths.get(System.getProperty("user.home"), ".karma", "media");

    private final Path outputDir;

    public LocalImageProvider() {
        this(DEFAULT_DIR);
    }

    public LocalImageProvider(Path outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public String getName() { return "local-image"; }

    @Override
    public String getDefaultModel() { return "local-procedural"; }

    @Override
    public List<String> getAvailableModels() { return List.of("local-procedural"); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        try {
            Files.createDirectories(outputDir);
            int[] dims = parseSize(request.getSize());
            int width = dims[0];
            int height = dims[1];
            String prompt = request.getPrompt() != null && !request.getPrompt().isBlank()
                ? request.getPrompt().trim()
                : "Karma OS";

            int seed = prompt.hashCode();
            List<BufferedImage> images = new ArrayList<>();
            int variants = Math.max(1, Math.min(request.getN(), 4));
            for (int i = 0; i < variants; i++) {
                images.add(render(width, height, prompt, seed + i));
            }

            List<String> urls = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                Path file = outputDir.resolve("karma-" + System.currentTimeMillis() + "-" + Math.abs(seed) + "-" + i + ".png");
                ImageIO.write(images.get(i), "png", file.toFile());
                urls.add(file.toUri().toString());
            }

            log.info("Generated {} local image(s) to {}", urls.size(), outputDir);
            return ImageResponse.ok(urls, prompt, getDefaultModel(), 5);
        } catch (IOException e) {
            log.error("Local image generation failed", e);
            return ImageResponse.error("Local image generation failed: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        try {
            Files.createDirectories(outputDir);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private BufferedImage render(int width, int height, String prompt, int seed) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color c1 = colorFrom(seed, 60, 120, 180);
        Color c2 = colorFrom(seed + 9973, 60, 120, 180);
        g.setPaint(new GradientPaint(0, 0, c1, width, height, c2));
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(255, 255, 255, 18));
        for (int x = 0; x < width; x += 96) {
            g.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 96) {
            g.drawLine(0, y, width, y);
        }

        int cx = width / 2;
        int cy = height / 2;
        int ring = Math.min(width, height) / 4;
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(6f));
        for (int r = ring; r >= ring - 40; r -= 20) {
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
        }

        g.setColor(new Color(0, 0, 0, 120));
        int labelY = height - 40;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
        String label = shorten(prompt, 48);
        int tw = g.getFontMetrics().stringWidth(label);
        g.fillRoundRect(cx - tw / 2 - 16, labelY - 24, tw + 32, 40, 12, 12);
        g.setColor(Color.WHITE);
        g.drawString(label, cx - tw / 2, labelY + 2);

        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        String sub = "KARMA OS · " + getDefaultModel();
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(255, 255, 255, 200));
        g.drawString(sub, cx - sw / 2, 32);

        g.dispose();
        return img;
    }

    private int[] parseSize(String size) {
        if (size != null && size.matches("\\d+x\\d+")) {
            String[] parts = size.split("x");
            try {
                return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
            } catch (NumberFormatException ignored) {}
        }
        return new int[] { 1280, 720 };
    }

    private Color colorFrom(int seed, int low, int high, int offset) {
        int r = low + Math.abs((seed + offset * 3)) % (high - low);
        int gg = low + Math.abs((seed + offset * 5)) % (high - low);
        int b = low + Math.abs((seed + offset * 7)) % (high - low);
        return new Color(Math.min(255, r), Math.min(255, gg), Math.min(255, b));
    }

    private String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
