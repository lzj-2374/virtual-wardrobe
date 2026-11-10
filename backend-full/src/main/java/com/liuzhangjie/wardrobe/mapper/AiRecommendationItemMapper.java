package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AiRecommendationItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 推荐单品表。单表增删查，走 BaseMapper。
 */
@Mapper
public interface AiRecommendationItemMapper extends BaseMapper<AiRecommendationItem> {
}
