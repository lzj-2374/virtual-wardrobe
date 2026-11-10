package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

/**
 * 提交生图任务的入参，对应 {@code POST /api/ai/image/generate}。
 * <p>
 * 只需要一个推荐 id：要画什么完全由那条推荐已经存下来的单品决定，
 * 前端不需要也不应该自己拼 prompt —— 提示词是服务端的事，
 * 放开给前端等于让别人能拿你的额度去画任意内容。
 */
@Data
public class AiImageRequestDTO {

    /** 推荐结果 id，必须属于当前用户 */
    private Long recommendationId;

}
