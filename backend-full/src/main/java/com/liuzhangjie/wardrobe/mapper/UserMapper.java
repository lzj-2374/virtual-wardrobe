package com.liuzhangjie.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liuzhangjie.wardrobe.entity.AdminOverviewVO;
import com.liuzhangjie.wardrobe.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 继承 BaseMapper 就白拿了单表增删改查，不用写一行 SQL。
 * 复杂查询（多表联查、统计）再另外在 resources/mapper/*.xml 里写，
 * 或者在方法上打 @Select 注解。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 管理端报表：按天统计新增用户数，只返回「有数据」的日期。
     * <p>
     * 注意两点：
     * 1. @Select 是手写原生 SQL，MyBatis-Plus 不会帮你补 deleted = 0，必须自己写；
     * 2. GROUP BY 只会对「存在的行」分组，没有注册记录的日期不会出现在结果里，
     * 所以「最近 7 天连续日期」这一步得在 Java 里补，不能指望 SQL。
     *
     * @param start 统计起点（含当天 0 点）
     * @return 每行是 { dt: "2026-11-04", cnt: 5 }，可能少于传入的天数
     */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS dt, COUNT(*) AS cnt
            FROM `user`
            WHERE deleted = 0 AND create_time >= #{start}
            GROUP BY dt
            ORDER BY dt
            """)
    List<AdminOverviewVO.TrendPointVO> countDaily(@Param("start") LocalDateTime start);
}
