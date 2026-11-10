package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话表。
 * <p>
 * 没有任何自定义 SQL：会话的增删查都是单表 + 简单条件，
 * MyBatis-Plus 的 {@code selectPage / selectById / insert / updateById} 已经够用。
 * 真需要复杂查询再往这里加 {@code @Select}，不要为了"看起来完整"先写一堆空方法。
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
