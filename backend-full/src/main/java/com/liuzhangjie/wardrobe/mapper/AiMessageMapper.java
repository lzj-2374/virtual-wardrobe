package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息表。单表增查，直接用 BaseMapper。
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
