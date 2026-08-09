package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a draw.io-style whiteboard diagram from a structured "diagram_spec"
 * JSON payload. Pure Java2D, deterministic (same spec -> same PNG), no external
 * provider. Output is a local {@code file:} URI understood by FFmpegVideoProvider.
 *
 * Expected prompt format (raw JSON):
 * <pre>
 * {
 *   "title": "...",
 *   "shapes": [ {"id":"a","type":"box","label":"HEAP","fill":"#DAE8FC","line":"#6C8EBF"}, ... ],
 *   "arrows": [ {"from":"a","to":"b","label":"pointers"}, ... ],
 *   "instructions": [ "Step 1 ...", ... ],
 *   "code": {"lang":"java","text":"String s = ..."}
 * }
 * </pre>
 * Supported shape {@code type} values:
 * <ul>
 *   <li>{@code box} / {@code rect} — rounded rectangle (default)</li>
 *   <li>{@code diamond} / {@code decision} — decision diamond</li>
 *   <li>{@code oval} / {@code ellipse} / {@code circle} / {@code start} / {@code end} — ellipse</li>
 *   <li>{@code parallelogram} / {@code input} / {@code output} — parallelogram</li>
 *   <li>{@code cylinder} / {@code database} — database cylinder</li>
 *   <li>{@code hexagon} / {@code process} — hexagon</li>
 * </ul>
 */
public class DiagramImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(DiagramImageProvider.class);
    private static final Path DEFAULT_DIR = Paths.get(System.getProperty("user.home"), ".karma", "media");

    static final int W = 1920;
    static final int H = 1080;
    static final Color CANVAS = Color.WHITE;
    static final Color TEXT = new Color(0x333333);
    static final Color HALO = new Color(0xF5F5F5);
    static final Color ARROW = new Color(0x555555);

    // draw.io palette defaults
    static final Color DEF_FILL = new Color(0xDAE8FC);
    static final Color DEF_LINE = new Color(0x6C8EBF);

    private final Path outputDir;
    private final ObjectMapper objectMapper;

    public DiagramImageProvider() {
        this(DEFAULT_DIR, new ObjectMapper());
    }

    public DiagramImageProvider(Path outputDir, ObjectMapper objectMapper) {
        this.outputDir = outputDir;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() { return "diagram-image"; }

    @Override
    public String getDefaultModel() { return "local-diagram"; }

    @Override
    public List<String> getAvailableModels() { return List.of("local-diagram"); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        try {
            Files.createDirectories(outputDir);
            String prompt = request.getPrompt() != null ? request.getPrompt().trim() : "";
            JsonNode spec = prompt.isBlank() ? null : parseSpec(prompt);
            if (spec == null) {
                return ImageResponse.error("Diagram provider requires a diagram_spec JSON prompt");
            }
            BufferedImage img = render(spec);
            Path file = outputDir.resolve("diagram-" + System.currentTimeMillis() + "-" + Math.abs(spec.hashCode()) + ".png");
            ImageIO.write(img, "png", file.toFile());
            log.info("Rendered diagram to {}", file);
            return ImageResponse.ok(List.of(file.toUri().toString()), prompt, getDefaultModel(), 20);
        } catch (Exception e) {
            log.error("Diagram rendering failed", e);
            return ImageResponse.error("Diagram rendering failed: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        try {
            Files.createDirectories(outputDir);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode parseSpec(String prompt) {
        try {
            return objectMapper.readTree(prompt);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- render

    public BufferedImage render(JsonNode spec) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(CANVAS);
        g.fillRect(0, 0, W, H);

        List<ShapeBox> boxes = parseShapes(spec);
        List<ArrowSpec> arrows = parseArrows(spec);
        String title = textOf(spec.get("title"));
        List<String> instructions = stringArray(spec.get("instructions"));
        JsonNode code = spec.get("code");
        String codeText = code != null && code.isObject() ? textOf(code.get("text")) : null;

        int codePanelH = codeText != null ? 140 : 0;
        int instrH = instructions.isEmpty() ? 0 : measureInstructions(g, instructions);
        int bottomReserve = codePanelH + instrH + ((codeText != null || !instructions.isEmpty()) ? 24 : 0);
        int instrTop = H - instrH;
        int codeTop = codeText != null ? instrTop - 20 - codePanelH : H;

        layout(boxes, codeText != null ? codeTop : instrTop);

        // arrows first so boxes overlay endpoints
        for (ArrowSpec a : arrows) {
            ShapeBox from = findBox(boxes, a.from);
            ShapeBox to = findBox(boxes, a.to);
            if (from == null || to == null) continue;
            drawConnector(g, from, to, a.label);
        }
        for (ShapeBox b : boxes) {
            drawBox(g, b);
        }

        drawTitle(g, title);

        if (codeText != null) {
            drawCodePanel(g, codeText, codeTop);
        }
        if (!instructions.isEmpty()) {
            drawInstructions(g, instructions, instrTop);
        }

        drawLogo(g);
        g.dispose();
        return img;
    }

    private int measureInstructions(Graphics2D g, List<String> lines) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        int count = 0;
        for (String l : lines) {
            count += wrapLines(l, g.getFontMetrics(), 1500).split("\n").length;
        }
        return 40 + count * 38;
    }

    // ---------------------------------------------------------------- layout

    static class ShapeBox {
        String id;
        String type;
        String label;
        Color fill;
        Color line;
        Rectangle2D.Double r = new Rectangle2D.Double(0, 0, 0, 0);
    }

    static class ArrowSpec {
        String from, to, label;
    }

    private List<ShapeBox> parseShapes(JsonNode spec) {
        List<ShapeBox> result = new ArrayList<>();
        JsonNode arr = spec.get("shapes");
        if (arr == null || !arr.isArray()) return result;
        for (JsonNode s : arr) {
            ShapeBox b = new ShapeBox();
            b.id = textOf(s.get("id"));
            b.type = textOf(s.get("type"));
            b.label = textOf(s.get("label"));
            b.fill = colorOf(s.get("fill"), DEF_FILL);
            b.line = colorOf(s.get("line"), DEF_LINE);
            if (b.id == null || b.label == null) continue;
            result.add(b);
        }
        return result;
    }

    private List<ArrowSpec> parseArrows(JsonNode spec) {
        List<ArrowSpec> result = new ArrayList<>();
        JsonNode arr = spec.get("arrows");
        if (arr == null || !arr.isArray()) return result;
        for (JsonNode a : arr) {
            ArrowSpec as = new ArrowSpec();
            as.from = textOf(a.get("from"));
            as.to = textOf(a.get("to"));
            as.label = textOf(a.get("label"));
            if (as.from != null && as.to != null) result.add(as);
        }
        return result;
    }

    /** Lays boxes out in a grid (cols based on count) that never crosses the reserved
     *  bottom band (code panel + instructions). Box size shrinks when many boxes
     *  need to fit, and box height grows when a label wraps beyond one line. */
    private void layout(List<ShapeBox> boxes, int bottomReserveTop) {
        int n = boxes.size();
        if (n == 0) return;
        int cols = n <= 3 ? n : (n <= 6 ? 3 : 4);
        int rows = (int) Math.ceil((double) n / cols);

        int marginLeft = 120, marginRight = 120;
        int marginTop = 210;
        int usableW = W - marginLeft - marginRight;
        int usableH = Math.max(120, bottomReserveTop - marginTop - 20);

        int gapX = Math.min(120, usableW / (cols * 2));
        int gapY = Math.min(110, usableH / (rows * 2));

        int boxW = Math.min(380, (usableW - (cols - 1) * gapX) / cols);
        int boxH = Math.min(170, (usableH - (rows - 1) * gapY) / rows);
        boxW = Math.max(160, boxW);
        boxH = Math.max(60, boxH);

        int totalW = cols * boxW + (cols - 1) * gapX;
        int totalH = rows * boxH + (rows - 1) * gapY;
        int startX = marginLeft + (usableW - totalW) / 2;
        int startY = marginTop + (usableH - totalH) / 2;

        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            ShapeBox b = boxes.get(i);
            b.r.x = startX + col * (boxW + gapX);
            b.r.y = startY + row * (boxH + gapY);
            b.r.width = boxW;
            b.r.height = boxH;
        }
    }

    private ShapeBox findBox(List<ShapeBox> boxes, String id) {
        if (id == null) return null;
        for (ShapeBox b : boxes) if (id.equals(b.id)) return b;
        return null;
    }

    // ---------------------------------------------------------------- paint

    private void drawBox(Graphics2D g, ShapeBox b) {
        int x = (int) b.r.x, y = (int) b.r.y, w = (int) b.r.width, h = (int) b.r.height;
        String type = b.type == null ? "box" : b.type.toLowerCase();
        g.setColor(b.fill);
        g.setStroke(new BasicStroke(2f));
        switch (type) {
            case "diamond", "decision" -> {
                Polygon diamond = new Polygon();
                diamond.addPoint(x + w / 2, y);
                diamond.addPoint(x + w, y + h / 2);
                diamond.addPoint(x + w / 2, y + h);
                diamond.addPoint(x, y + h / 2);
                g.fillPolygon(diamond);
                g.setColor(b.line);
                g.drawPolygon(diamond);
            }
            case "oval", "ellipse", "circle", "start", "end" -> {
                g.fillOval(x, y, w, h);
                g.setColor(b.line);
                g.drawOval(x, y, w, h);
            }
            case "parallelogram", "input", "output" -> {
                int skew = Math.max(20, w / 5);
                Path2D.Float par = new Path2D.Float();
                par.moveTo(x + skew, y);
                par.lineTo(x + w, y);
                par.lineTo(x + w - skew, y + h);
                par.lineTo(x, y + h);
                par.closePath();
                g.fill(par);
                g.setColor(b.line);
                g.draw(par);
            }
            case "cylinder", "database" -> drawCylinder(g, x, y, w, h, b.fill, b.line);
            case "hexagon", "process" -> {
                Path2D.Float hex = new Path2D.Float();
                hex.moveTo(x + w * 0.25, y);
                hex.lineTo(x + w * 0.75, y);
                hex.lineTo(x + w, y + h / 2);
                hex.lineTo(x + w * 0.75, y + h);
                hex.lineTo(x + w * 0.25, y + h);
                hex.lineTo(x, y + h / 2);
                hex.closePath();
                g.fill(hex);
                g.setColor(b.line);
                g.draw(hex);
            }
            default -> {
                g.fillRoundRect(x, y, w, h, 12, 12);
                g.setColor(b.line);
                g.drawRoundRect(x, y, w, h, 12, 12);
            }
        }

        g.setColor(TEXT);
        boolean diamond = "diamond".equals(type) || "decision".equals(type);
        int labelW = diamond ? (int) (w * 0.72) : w - 40;
        int labelMaxH = diamond ? (int) (h * 0.6) : h - 30;
        drawBoxLabel(g, b.label, x, y, w, h, labelW, labelMaxH);
    }

    private void drawBoxLabel(Graphics2D g, String label, int x, int y, int w, int h,
                              int maxW, int maxTextH) {
        int size = 32;
        while (size > 20) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            FontMetrics fm = g.getFontMetrics();
            String wrapped = wrap(label, fm, maxW);
            int lines = wrapped.isEmpty() ? 1 : wrapped.split("\n").length;
            if (lines * fm.getHeight() <= maxTextH && fm.stringWidth(longestLine(wrapped)) <= maxW) {
                break;
            }
            size -= 2;
        }
        FontMetrics fm = g.getFontMetrics();
        String wrapped = wrap(label, fm, maxW);
        int lineH = fm.getHeight();
        int ty = y + (h - lineH * wrapped.split("\n").length) / 2 + fm.getAscent();
        for (String line : wrapped.split("\n")) {
            g.drawString(line, x + (w - fm.stringWidth(line)) / 2, ty);
            ty += lineH;
        }
    }

    private String longestLine(String s) {
        String longest = "";
        for (String l : s.split("\n")) if (l.length() > longest.length()) longest = l;
        return longest;
    }

    private void drawCylinder(Graphics2D g, int x, int y, int w, int h,
                              Color fill, Color line) {
        int arcH = Math.min(40, h / 3);
        Path2D.Float cyl = new Path2D.Float();
        cyl.moveTo(x, y + arcH);
        cyl.lineTo(x, y + h - arcH);
        cyl.quadTo(x, y + h, x + w / 2, y + h);
        cyl.quadTo(x + w, y + h, x + w, y + h - arcH);
        cyl.lineTo(x + w, y + arcH);
        cyl.closePath();
        g.fill(cyl);
        g.setColor(line);
        g.draw(cyl);
        g.setColor(fill);
        g.fillOval(x, y, w, arcH * 2);
        g.setColor(line);
        g.drawOval(x, y, w, arcH * 2);
    }

    private void drawTitle(Graphics2D g, String title) {
        if (title == null || title.isBlank()) return;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        g.setColor(TEXT);
        g.drawString(title, 80, 90);
    }

    private void drawInstructions(Graphics2D g, List<String> lines, int top) {
        int x = 80, y = top + 40;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.setColor(TEXT);
        g.drawString("Instructions:", x, top + 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        for (String l : lines) {
            for (String wrapped : wrapLines(l, g.getFontMetrics(), 1500).split("\n")) {
                g.drawString(wrapped, x + 16, y);
                y += 38;
            }
        }
    }

    private void drawCodePanel(Graphics2D g, String code, int top) {
        int x = 80, y = top;
        g.setColor(HALO);
        g.fillRoundRect(x, y, 900, 110, 12, 12);
        g.setColor(new Color(0xCCCCCC));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, 900, 110, 12, 12);
        g.setColor(new Color(0x1B1F27));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 24));
        int ty = y + 68;
        for (String line : wrapLines(code, g.getFontMetrics(), 850).split("\n")) {
            g.drawString(line, x + 24, ty);
            ty += 34;
        }
    }

    private File logoFile() {
        String[] candidates = {
            "/app/logo.png",
            "D:/CodeAtCloudeAI/Video-automation/logo.png"
        };
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.exists()) return f;
        }
        return new File(candidates[0]);
    }

    private void drawLogo(Graphics2D g) {
        File logo = logoFile();
        if (!logo.exists()) return;
        try {
            BufferedImage img = ImageIO.read(logo);
            int lw = 160, lh = (int) (160.0 * img.getHeight() / img.getWidth());
            g.drawImage(img, W - lw - 40, H - lh - 40, lw, lh, null);
        } catch (Exception e) {
            log.warn("Could not composite logo: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- arrows

    private void drawConnector(Graphics2D g, ShapeBox from, ShapeBox to, String label) {
        int scx = (int) (from.r.x + from.r.width / 2), scy = (int) (from.r.y + from.r.height / 2);
        int tcx = (int) (to.r.x + to.r.width / 2), tcy = (int) (to.r.y + to.r.height / 2);
        int dx = tcx - scx, dy = tcy - scy;

        g.setColor(ARROW);
        g.setStroke(new BasicStroke(3f));

        boolean horizontal = Math.abs(dx) >= Math.abs(dy);
        Path2D.Float p = new Path2D.Float();
        int entryX, entryY, exitX, exitY;
        double headAngle;

        if (horizontal) {
            exitX = dx > 0 ? (int) (from.r.x + from.r.width) : (int) from.r.x;
            entryX = dx > 0 ? (int) to.r.x : (int) (to.r.x + to.r.width);
            exitY = scy;
            entryY = tcy;
            int midX = (exitX + entryX) / 2;
            p.moveTo(exitX, exitY);
            p.lineTo(midX, exitY);
            if (exitY != entryY) p.lineTo(midX, entryY);
            p.lineTo(entryX, entryY);
            headAngle = dx > 0 ? 0 : 180;
            int lx = (exitX + entryX) / 2, ly = (exitY + entryY) / 2 - 18;
            if (label != null) drawHaloLabel(g, label, lx, ly);
        } else {
            exitY = dy > 0 ? (int) (from.r.y + from.r.height) : (int) from.r.y;
            entryY = dy > 0 ? (int) to.r.y : (int) (to.r.y + to.r.height);
            exitX = scx;
            entryX = tcx;
            int midY = (exitY + entryY) / 2;
            p.moveTo(exitX, exitY);
            p.lineTo(exitX, midY);
            if (exitX != entryX) p.lineTo(entryX, midY);
            p.lineTo(entryX, entryY);
            headAngle = dy > 0 ? 90 : -90;
            int lx = (exitX + entryX) / 2 + 12, ly = midY;
            if (label != null) drawHaloLabel(g, label, lx, ly);
        }
        g.draw(p);
        drawArrowHead(g, entryX, entryY, headAngle);
    }

    private void drawArrowHead(Graphics2D g, int x, int y, double angleDeg) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(angleDeg));
        Polygon head = new Polygon();
        head.addPoint(0, 0);
        head.addPoint(-18, -9);
        head.addPoint(-18, 9);
        g.fill(head);
        g.setTransform(old);
    }

    private void drawHaloLabel(Graphics2D g, String text, int cx, int cy) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text), th = fm.getHeight();
        int px = cx - tw / 2 - 12, py = cy - th / 2 - 6;
        g.setColor(HALO);
        g.fillRoundRect(px, py, tw + 24, th + 12, 8, 8);
        g.setColor(TEXT);
        g.drawString(text, px + 12, py + th - 8);
    }

    // ---------------------------------------------------------------- utils

    private static String textOf(JsonNode n) {
        return n != null && n.isValueNode() ? n.asText() : null;
    }

    private static List<String> stringArray(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode v : n) if (v.isTextual()) out.add(v.asText());
        }
        return out;
    }

    private static Color colorOf(JsonNode n, Color def) {
        if (n == null || !n.isTextual()) return def;
        String hex = n.asText().replace("#", "");
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (Exception e) {
            return def;
        }
    }

    private static String wrap(String s, FontMetrics fm, int maxWidth) {
        if (s == null || s.isBlank()) return "";
        return wrapLines(s, fm, maxWidth);
    }

    private static String wrapLines(String s, FontMetrics fm, int maxWidth) {
        if (s == null || s.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        String[] words = s.split(" ");
        String line = "";
        for (String w : words) {
            String test = line.isEmpty() ? w : line + " " + w;
            if (fm.stringWidth(test) <= maxWidth) {
                line = test;
            } else {
                if (!out.isEmpty()) out.append("\n");
                out.append(line);
                line = w;
            }
        }
        if (!out.isEmpty()) out.append("\n");
        out.append(line);
        return out.toString();
    }
}
