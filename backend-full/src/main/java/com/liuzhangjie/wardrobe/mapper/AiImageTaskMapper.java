package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AiImageTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 生图任务表。
 * <p>
 * 自定义 SQL 只有一个，而且是这张表最要紧的一行：**带条件的更新**。
 * {@code UPDATE ... WHERE task_id = ? AND status <> 2} 是幂等的核心——
 * 消费者重复收到同一条消息时，第二次执行影响行数为 0，直接跳过，
 * 不会把已经生成好的图覆盖掉，也不会重复扣一次生图额度。
 */
@Mapper
public interface AiImageTaskMapper extends BaseMapper<AiImageTask> {

    /**
     * 抢占任务：只有当前不是「已成功」才允许把状态推进到 targetStatus。
     *
     * @return 影响行数。0 表示任务已经成功过（或 task_id 不存在），调用方应直接放弃本次处理
     */
    @Update("""
            UPDATE ai_image_task
            SET status = #{targetStatus},
                image_url = #{imageUrl},
                error_msg = #{errorMsg}
            WHERE task_id = #{taskId}
              AND status <> 2
            """)
    int updateStatusIfNotSuccess(@Param("taskId") String taskId,
                                 @Param("targetStatus") Integer targetStatus,
                                 @Param("imageUrl") String imageUrl,
                                 @Param("errorMsg") String errorMsg);

    /** 重试次数 +1，用于识别"这条消息已经被处理过几次" */
    @Update("UPDATE ai_image_task SET retry_count = retry_count + 1 WHERE task_id = #{taskId}")
    int incrementRetry(@Param("taskId") String taskId);

}
