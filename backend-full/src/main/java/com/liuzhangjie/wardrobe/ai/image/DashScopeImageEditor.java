package com.liuzhangjie.wardrobe.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuzhangjie.wardrobe.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通义万相「指令编辑」（image2image）客户端。
 * <p>
 * 这里的用法有点特别，值得说明：普通的图生图是"给一张模特图 + 一堆参考图"，
 * 但万相的 image2image 接口只接受**一张**底图。所以流程是这样的：
 * <ol>
 *   <li>先把这套搭配在服务端拼成一张"平铺图"（{@link OutfitImageComposer} 干的活）</li>
 *   <li>把这张平铺图当作底图传上去，指令写"把图中的衣服穿在一位模特身上，全身照"</li>
 *   <li>模型在保留衣物款式和颜色的前提下，把它变成一张上身效果图</li>
 * </ol>
 * 这样既绕开了"一张底图"的限制，也保住了项目"推荐基于真实衣物"的卖点——
 * 模型是照着真实单品改的，不是凭空画的。
 * <p>
 * <b>接口是异步的</b>：提交后只拿到 task_id，要轮询到 SUCCEEDED 才有图。
 * 但注意这是"消费者线程内部的异步"，对前端而言整个生图依然是后台任务，
 * 两条异步链路不要混为一谈。
 * <p>
 * <b>已用真实 key 实测</b>（wan2.5-i2i-preview）：单件衣物、多件拼图两种情况都能出上身图，
 * 且能读平铺图上的中文标签去还原对应单品。任何一步失败都抛异常，
 * 由 {@link OutfitImageService} 接住并降级为平铺图，所以模型抖动时用户不会空手而归。
 */
@Slf4j
@Component
public class DashScopeImageEditor {

    private static final String SUBMIT_PATH = "/api/v1/services/aigc/image2image/image-synthesis";
    private static final String TASK_PATH = "/api/v1/tasks/";

    /**
     * 轮询间隔与上限。
     * 实测 wan2.5-i2i-preview 单张要 60s 上下（任务 19 落库耗时正好 60s），不是 10~30s，
     * 所以这里给到 120s 只是勉强够；前端轮询的上限必须比这个更宽，
     * 否则后端还在跑、页面先报超时，用户会以为图没出来。
     */
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final long POLL_TIMEOUT_MS = 120_000L;

    /**
     * 指令模板。要点是明确"保留"什么、"改变"什么：
     * <p>
     * 这套措辞是拿真实 key 反复调出来的，别随手改：
     * <ul>
     *   <li>必须写明"全部单品（上衣、下装、外套、鞋）"，只说"穿在身上"模型会漏掉下装</li>
     *   <li>必须写明"不要重新设计"，否则模型会按自己的审美重画版型</li>
     *   <li>必须写明"不要出现文字"，平铺图里带中文标签，模型会顺手把标签画进成图</li>
     *   <li>模特是谁要说清楚。不写性别时模型自己挑，同一套搭配两次出的图可能一男一女，
     *       演示时很难解释，所以由调用方按用户资料/对话把性别定好传进来</li>
     * </ul>
     * 占位符用 {@code {xxx}} 而不是 {@code %s}：搭配名是模型生成的，
     * 万一里面混进一个百分号，String.format 会直接抛异常把整张图搞失败。
     */
    private static final String INSTRUCTION_TEMPLATE =
            "让{subject}穿上图中这套搭配的全部单品（上衣、下装、外套、鞋），生成正面全身照。"
                    + "严格保留每件衣服的款式、颜色、材质和细节，不要重新设计。"
                    + "纯浅灰背景，影棚柔光，正面站姿。"
                    + "画面中不要出现任何文字、字母、水印、标签。"
                    + "搭配主题：{theme}。";

    /** 模特主体。性别未知时用中性说法，不硬塞一个"女性"进去 */
    private static final String SUBJECT_MALE = "一位年轻的男性真人模特";
    private static final String SUBJECT_FEMALE = "一位年轻的女性真人模特";
    private static final String SUBJECT_UNKNOWN = "一位真人模特";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public DashScopeImageEditor(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(aiProperties.getImage().getBaseUrl());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 基于一张底图做指令编辑。
     *
     * @param baseImageUrl 底图地址，必须是公网可访问的（这里就是刚上传到 OSS 的平铺图）
     * @param theme        搭配主题，会被拼进指令
     * @param gender       模特性别：1 男 / 2 女 / null 未指定（交给模型自己挑）
     * @return 生成图的字节
     * @throws Exception 任何环节失败。调用方必须能接住并降级
     */
    public byte[] edit(String baseImageUrl, String theme, Integer gender) throws Exception {
        String taskId = submit(baseImageUrl, theme, gender);
        String resultUrl = poll(taskId);
        return download(resultUrl);
    }

    // ===================== 提交 =====================

    /** 性别 → 提示词里的模特主体 */
    private String subjectOf(Integer gender) {
        if (gender == null) {
            return SUBJECT_UNKNOWN;
        }
        if (gender == 1) {
            return SUBJECT_MALE;
        }
        if (gender == 2) {
            return SUBJECT_FEMALE;
        }
        return SUBJECT_UNKNOWN;
    }

    private String submit(String baseImageUrl, String theme, Integer gender) throws Exception {
        AiProperties.Image cfg = aiProperties.getImage();

        Map<String, Object> input = new LinkedHashMap<>();
        // wan2.5+ 的 input 是 {prompt, images[]}：参考图放数组里，最多 3 张。
        // 老版 wanx2.1 用的是 {function, prompt, base_image_url}，两者不通用，换模型必须同步改这里。
        String instruction = INSTRUCTION_TEMPLATE
                .replace("{subject}", subjectOf(gender))
                .replace("{theme}", theme == null ? "日常" : theme);
        log.debug("万相指令：{}", instruction);
        input.put("prompt", instruction);
        input.put("images", List.of(baseImageUrl));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("n", 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("input", input);
        body.put("parameters", parameters);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + SUBMIT_PATH))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("Content-Type", "application/json")
                // 声明异步：不加这个头，服务端会认为你要同步等待，长任务直接超时
                .header("X-DashScope-Async", "enable")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());

        String taskId = root.path("output").path("task_id").asText(null);
        if (taskId == null) {
            throw new IllegalStateException("万相提交失败，HTTP " + response.statusCode()
                    + "，响应：" + preview(response.body()));
        }
        log.info("万相生图任务已提交，taskId={}", taskId);
        return taskId;
    }

    // ===================== 轮询 =====================

    private String poll(String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        String apiKey = aiProperties.getImage().getApiKey();

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + TASK_PATH + taskId))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode output = objectMapper.readTree(response.body()).path("output");
            String status = output.path("task_status").asText("");

            switch (status) {
                case "SUCCEEDED" -> {
                    String url = output.path("results").path(0).path("url").asText(null);
                    if (url == null) {
                        throw new IllegalStateException("万相任务成功但没有返回图片地址");
                    }
                    return url;
                }
                case "FAILED", "CANCELED", "UNKNOWN" ->
                        throw new IllegalStateException("万相任务失败，状态=" + status
                                + "，code=" + output.path("code").asText("")
                                + "，message=" + output.path("message").asText(""));
                default -> {
                    // PENDING / RUNNING，继续等
                }
            }
        }
        throw new IllegalStateException("万相生图超时（" + POLL_TIMEOUT_MS / 1000 + " 秒）");
    }

    // ===================== 下载 =====================

    /**
     * 结果图在 OSS 的临时地址上，有有效期，必须立刻下载并转存到自己的桶里，
     * 否则过期之后用户看到的是 403。
     */
    private byte[] download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            return in.readAllBytes();
        }
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String preview(String body) {
        if (body == null) {
            return "null";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }

}
