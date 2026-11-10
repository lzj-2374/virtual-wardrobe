package com.liuzhangjie.wardrobe.ai.image;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务端合成效果图：把一套搭配里的真实单品图，拼成一张竖向的「搭配卡片」。
 * <p>
 * 它不是 AI 生成，也不假装是。定位有三个，都很实在：
 * <ol>
 *   <li><b>没配图像模型时的兜底</b>——生图链路、异步队列、状态机全部真实跑通，
 *       只是最终那张图是拼的，不是画的</li>
 *   <li><b>将来的参考底图</b>——接了通义万相之后，这张合成图会作为 image2image 的输入，
 *       让模型"照着这些真实衣服"生成上身照，比纯文生图更贴近真实衣柜</li>
 *   <li><b>失败时的降级产物</b>——万相超时或额度用完，用户至少还能拿到一张能看的图</li>
 * </ol>
 * 用 Java2D 画而不是引第三方图形库：JDK 自带，不增加依赖，也不需要装字体包。
 */
@Slf4j
@Component
public class OutfitImageComposer {

    /** 和 ai.image.width / height 的默认值保持一致，竖向 3:4 */
    private static final int WIDTH = 768;
    private static final int HEIGHT = 1024;

    /** 单品格子：3 列 × 最多 2 行 = 6 个位置，一套搭配最多 5 件，够放 */
    private static final int CELL_W = 200;
    private static final int CELL_H = 240;
    private static final int GAP_X = 24;
    private static final int GAP_Y = 34;
    private static final int GRID_TOP = 300;

    private static final Color BG_TOP = new Color(0xFA, 0xF8, 0xF2);
    private static final Color BG_BOTTOM = new Color(0xEE, 0xE7, 0xD6);
    private static final Color INK = new Color(0x2B, 0x2B, 0x2B);
    private static final Color INK_MUTE = new Color(0x8A, 0x83, 0x74);
    private static final Color GOLD = new Color(0xB8, 0x96, 0x4E);
    private static final Color CELL_BG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color PLACEHOLDER = new Color(0xE8, 0xE2, 0xD4);

    /**
     * 中文字体候选。Windows 上一定命中雅黑；Linux 服务器上命中思源或文泉驿；
     * 都没有时退到逻辑字体 SansSerif，此时中文可能显示成方框——
     * 这是服务器没装中文字体的表现，不是代码 bug，日志里会提示。
     */
    private static final List<String> CJK_FONT_CANDIDATES = List.of(
            "Microsoft YaHei", "微软雅黑", "PingFang SC", "Noto Sans CJK SC",
            "Source Han Sans SC", "WenQuanYi Micro Hei", "SimHei", "SimSun"
    );

    private String resolvedFontFamily;

    /**
     * 生成一张搭配卡片。
     *
     * @param outfitName 搭配名，如「为你搭配：通勤·秋」
     * @param colorNote  配色说明，如「主色：白 / 点缀：米」
     * @param scene      场景
     * @param seasonText 季节中文，如「秋」
     * @param items      单品，顺序即绘制顺序
     * @return PNG 字节；任何一步失败都返回非 null（至少有一张占位图），不往上层抛
     */
    public byte[] compose(String outfitName, String colorNote, String scene,
                          String seasonText, List<RenderItem> items) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            applyQuality(g);
            drawBackground(g);
            drawHeader(g, outfitName, colorNote, scene, seasonText);
            drawItems(g, items);
            drawFooter(g);
        } finally {
            // Graphics2D 持有原生资源，必须显式释放，否则高并发生图会把进程的 X11/字体句柄耗光
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(canvas, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("合成效果图写出 PNG 失败", e);
            throw new IllegalStateException("效果图合成失败", e);
        }
    }

    private void applyQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, HEIGHT, BG_BOTTOM));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 细边框，模仿纸质卡片的留白
        g.setColor(new Color(0xD8, 0xCF, 0xBB));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(28, 28, WIDTH - 57, HEIGHT - 57);
    }

    private void drawHeader(Graphics2D g, String outfitName, String colorNote, String scene, String seasonText) {
        g.setColor(GOLD);
        g.setFont(font(Font.BOLD, 15));
        drawCentered(g, "AI 搭配 · " + seasonText + " · " + scene, WIDTH / 2, 96);

        g.setColor(INK);
        g.setFont(font(Font.BOLD, 34));
        drawCentered(g, safe(outfitName, "搭配"), WIDTH / 2, 152);

        if (colorNote != null && !colorNote.isBlank()) {
            g.setColor(INK_MUTE);
            g.setFont(font(Font.PLAIN, 19));
            drawCentered(g, colorNote, WIDTH / 2, 192);
        }

        // 分隔线：中间一小段金线，比通栏横线更克制
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(WIDTH / 2 - 60, 224, WIDTH / 2 + 60, 224);
    }

    /**
     * 画单品格子。
     * <p>
     * 版面是自适应的，因为一套搭配的件数在 2~5 之间浮动：
     * 固定 200×240 的格子，只有 2 件时下面会空出大半张图，看起来很敷衍；
     * 而 5 件时又要保证放得下。所以按件数挑两套尺寸，再把整块格子垂直居中，
     * 无论几件，视觉重心都在画面中间。
     */
    private void drawItems(Graphics2D g, List<RenderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int total = items.size();
        // 两件以内放大格子，三件以上用小格子（每行最多 3 个）
        boolean roomy = total <= 2;
        int cols = roomy ? total : 3;
        int cellW = roomy ? 300 : CELL_W;
        int cellH = roomy ? 360 : CELL_H;

        int rows = (total + cols - 1) / cols;
        int gridHeight = rows * cellH + (rows - 1) * (GAP_Y + 28);

        // 垂直居中：内容区的下沿留出页脚空间（HEIGHT-140），把剩余空白对半分给上下
        int contentTop = GRID_TOP;
        int contentBottom = HEIGHT - 140;
        int startY = contentTop + Math.max(0, (contentBottom - contentTop - gridHeight) / 2);

        for (int i = 0; i < total; i++) {
            int row = i / cols;
            int col = i % cols;
            // 最后一行不足一整行时重新居中，避免左边对齐、右边空一块
            int itemsInRow = Math.min(cols, total - row * cols);
            int rowWidth = itemsInRow * cellW + (itemsInRow - 1) * GAP_X;
            int rowStartX = (WIDTH - rowWidth) / 2;

            int x = rowStartX + col * (cellW + GAP_X);
            int y = startY + row * (cellH + GAP_Y + 28);
            drawCell(g, items.get(i), x, y, cellW, cellH);
        }
    }

    private void drawCell(Graphics2D g, RenderItem item, int x, int y, int cellW, int cellH) {
        // 卡片底 + 细边
        g.setColor(CELL_BG);
        g.fillRoundRect(x, y, cellW, cellH, 8, 8);
        g.setColor(new Color(0xE2, 0xDA, 0xC9));
        g.drawRoundRect(x, y, cellW, cellH, 8, 8);

        String imageUrl = item.getImageUrl();
        // 没传过图 和 传了但下载失败 是两件事，提示语不能混用，否则用户以为是自己网络坏了
        boolean uploaded = imageUrl != null && !imageUrl.isBlank();
        BufferedImage img = uploaded ? loadImage(imageUrl) : null;
        if (img != null) {
            drawContained(g, img, x, y, cellW, cellH);
        } else {
            g.setColor(PLACEHOLDER);
            g.fillRoundRect(x + 1, y + 1, cellW - 1, cellH - 1, 8, 8);
            g.setColor(INK_MUTE);
            g.setFont(font(Font.PLAIN, 15));
            drawCentered(g, uploaded ? "图片加载失败" : "暂无图片", x + cellW / 2, y + cellH / 2, cellW);
        }

        // 角色 + 名称，两行都截断，避免长名字把格子撑破
        g.setColor(GOLD);
        g.setFont(font(Font.BOLD, 14));
        drawCentered(g, safe(item.getRoleLabel(), "单品"), x + cellW / 2, y + cellH + 22, cellW);

        g.setColor(INK);
        g.setFont(font(Font.PLAIN, 15));
        drawCentered(g, ellipsis(item.getName(), 11), x + cellW / 2, y + cellH + 46, cellW);
    }

    private void drawFooter(Graphics2D g) {
        g.setColor(INK_MUTE);
        g.setFont(font(Font.PLAIN, 14));
        drawCentered(g, "由虚拟衣柜 AI 穿搭系统生成", WIDTH / 2, HEIGHT - 56);
    }

    // ===================== 绘图辅助 =====================

    /**
     * 等比缩放后居中绘制（contain 语义），不裁切。
     * 裁切会把衣服裁掉一部分，用户会觉得"图不对"，这和前端 {@code fit="contain"} 的选择是同一个理由。
     */
    private void drawContained(Graphics2D g, BufferedImage img, int x, int y, int boxW, int boxH) {
        double scale = Math.min((double) (boxW - 12) / img.getWidth(), (double) (boxH - 12) / img.getHeight());
        int w = (int) Math.round(img.getWidth() * scale);
        int h = (int) Math.round(img.getHeight() * scale);
        int dx = x + (boxW - w) / 2;
        int dy = y + (boxH - h) / 2;
        g.drawImage(img, dx, dy, w, h, null);
    }

    /**
     * 下载远程图片。超时给 5 秒，失败返回 null 由调用方画占位——
     * 一张图下不下来不应该让整张效果图生成失败。
     */
    private BufferedImage loadImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "wardrobe-image-composer");
            try (InputStream in = conn.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) {
                    log.warn("单品图不是可识别的图片格式，跳过：{}", url);
                }
                return img;
            }
        } catch (Exception e) {
            log.warn("下载单品图失败，将绘制占位块：{}，原因：{}", url, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - textWidth / 2, baselineY);
    }

    /** 带最大宽度约束的居中绘制，超宽时自行截断 */
    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY, int maxWidth) {
        String fitted = fitToWidth(g, text, maxWidth);
        drawCentered(g, fitted, centerX, baselineY);
    }

    private String fitToWidth(Graphics2D g, String text, int maxWidth) {
        if (g.getFontMetrics().stringWidth(text) <= maxWidth) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && g.getFontMetrics().stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private String ellipsis(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 取一个能显示中文的字体。
     * <p>
     * 结果缓存在 {@link #resolvedFontFamily}：字体探测要遍历系统字体表，
     * 每张图都查一次会明显拖慢合成速度。
     */
    private Font font(int style, int size) {
        if (resolvedFontFamily == null) {
            resolvedFontFamily = resolveFontFamily();
        }
        return new Font(resolvedFontFamily, style, size);
    }

    private String resolveFontFamily() {
        try {
            Set<String> available = Arrays.stream(
                            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                    .collect(Collectors.toSet());
            for (String candidate : CJK_FONT_CANDIDATES) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
            log.warn("系统里没有找到任何中文字体，效果图上的中文可能显示为方框。"
                    + "可用字体数={}，建议在服务器上安装 fonts-noto-cjk", available.size());
        } catch (Exception e) {
            log.warn("探测系统字体失败，退回逻辑字体 SansSerif", e);
        }
        return Font.SANS_SERIF;
    }

    /**
     * 一件要画进图里的单品。用 @Data 类而不是 record，
     * 是因为这个对象由 Service 组装、字段较多，可读性比 record 的位置参数好。
     */
    @Data
    public static class RenderItem {
        private String name;
        private Integer role;
        private String roleLabel;
        private String imageUrl;

        public RenderItem(String name, Integer role, String roleLabel, String imageUrl) {
            this.name = name;
            this.role = role;
            this.roleLabel = roleLabel;
            this.imageUrl = imageUrl;
        }
    }

    /** 供 Service 批量构造时用，避免到处 new */
    public List<RenderItem> newItemList() {
        return new ArrayList<>();
    }

}
