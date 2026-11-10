package com.liuzhangjie.wardrobe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.AiChatDTO;
import com.liuzhangjie.wardrobe.entity.AiChatVO;
import com.liuzhangjie.wardrobe.entity.AiConversation;
import com.liuzhangjie.wardrobe.entity.AiImageRequestDTO;
import com.liuzhangjie.wardrobe.entity.AiImageTaskVO;
import com.liuzhangjie.wardrobe.entity.AiMessage;
import com.liuzhangjie.wardrobe.entity.AiRecommendationVO;
import com.liuzhangjie.wardrobe.entity.AiToOutfitDTO;
import com.liuzhangjie.wardrobe.entity.AiToOutfitVO;
import com.liuzhangjie.wardrobe.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 穿搭模块的接口层。
 * <p>
 * 六个端点分成三组：对话推荐两个（chat + 会话/消息），
 * 异步生图两个（提交 + 轮询），推荐沉淀一个（放入我的搭配）。
 * <p>
 * 这一层刻意写得很薄：只做参数接力和返回包装，所有业务判断都在
 * {@link AiService} 里。理由是 AI 这块的状态流转（未生成→生成中→成功/失败）
 * 一旦散在 Controller 和 Service 两处，很容易出现"接口返回了成功但库里还是生成中"。
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 穿搭相关接口")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 发起一轮对话，返回推荐卡片。
     */
    @PostMapping("/chat")
    @Operation(summary = "发起对话并返回搭配推荐")
    public Result<AiChatVO> chat(@RequestBody AiChatDTO dto) {
        return Result.ok(aiService.chat(dto));
    }

    /**
     * 会话列表。前端"历史对话"用，按最近活跃倒序。
     */
    @GetMapping("/conversation/page")
    @Operation(summary = "AI 会话分页")
    public Result<Page<AiConversation>> conversationPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(aiService.conversationPage(page, size));
    }

    /**
     * 某个会话下的消息列表。按时间正序，方便前端直接从上往下渲染。
     */
    @GetMapping("/conversation/{id}/messages")
    @Operation(summary = "查询会话消息")
    public Result<Page<AiMessage>> messages(@PathVariable("id") Long conversationId,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(aiService.messages(conversationId, page, size));
    }

    /**
     * 提交生图任务。立即返回 taskId，真实生成由 RabbitMQ 消费者异步执行。
     */
    @PostMapping("/image/generate")
    @Operation(summary = "提交生图任务（异步）")
    public Result<AiImageTaskVO> generateImage(@RequestBody AiImageRequestDTO dto) {
        return Result.ok(aiService.generateImage(dto));
    }

    /**
     * 轮询生图状态。前端每秒一次，直到 status 变成 2（成功）或 3（失败）。
     */
    @GetMapping("/image/status/{taskId}")
    @Operation(summary = "查询生图任务状态")
    public Result<AiImageTaskVO> imageStatus(@PathVariable String taskId) {
        return Result.ok(aiService.imageStatus(taskId));
    }

    /**
     * 历史效果图。只返回已经出过图的推荐，按最近更新倒序，给 AI 页的「历史效果图」抽屉用。
     */
    @GetMapping("/image/history")
    @Operation(summary = "历史效果图列表")
    public Result<List<AiRecommendationVO>> imageHistory(@RequestParam(defaultValue = "24") Integer limit) {
        return Result.ok(aiService.imageHistory(limit));
    }

    /**
     * 把推荐结果放入「我的搭配」。幂等，重复调用返回同一个 outfitId。
     */
    @PostMapping("/recommendation/{id}/to-outfit")
    @Operation(summary = "推荐结果放入我的搭配")
    public Result<AiToOutfitVO> toOutfit(@PathVariable("id") Long recommendationId,
                                         @RequestBody(required = false) AiToOutfitDTO dto) {
        return Result.ok(aiService.toOutfit(recommendationId, dto));
    }

}
