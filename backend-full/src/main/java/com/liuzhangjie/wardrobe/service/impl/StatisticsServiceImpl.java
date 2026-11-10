package com.liuzhangjie.wardrobe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liuzhangjie.wardrobe.common.BusinessException;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.entity.*;
import com.liuzhangjie.wardrobe.mapper.ClothingMapper;
import com.liuzhangjie.wardrobe.mapper.OutfitMapper;
import com.liuzhangjie.wardrobe.mapper.UserMapper;
import com.liuzhangjie.wardrobe.service.StatisticsService;
import com.liuzhangjie.wardrobe.utils.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final ClothingMapper clothingMapper;
    private final OutfitMapper outfitMapper;
    private final UserMapper userMapper;

    public StatisticsServiceImpl(ClothingMapper clothingMapper, OutfitMapper outfitMapper, UserMapper userMapper) {
        this.clothingMapper = clothingMapper;
        this.outfitMapper = outfitMapper;
        this.userMapper = userMapper;
    }

    @Override
    public OverviewVO getOverview() {
        Long userId = UserContext.getUserId();

        // selectCount 会自动拼上 deleted = 0（实体上有 @TableLogic），
        // 所以已删除的衣物不会算进去，这点不用自己写。
        // 但要自己带上 user_id 条件，否则会把别人的衣物也数进来。
        Long totalClothing = clothingMapper.selectCount(
                new LambdaQueryWrapper<Clothing>().eq(Clothing::getUserId, userId));

        Long totalOutfit = outfitMapper.selectCount(
                new LambdaQueryWrapper<Outfit>().eq(Outfit::getUserId, userId));

        // 两张图的数据。两条 SQL 都带 user_id，是「我的数据」不是全站数据。
        List<AdminOverviewVO.NameValueVO> byCategory = clothingMapper.countCategoryByUser(userId);
        List<AdminOverviewVO.NameValueVO> byStyle = clothingMapper.countStyleByUser(userId);

        // 最近 6 件：返回完整 Clothing 对象，ClothingCard 要读 imageUrl / category / color 等字段，
        // 只给 id 和 name 的话卡片全是占位图。
        // last() 只拼常量，绝不把外部参数拼进去（它不做 SQL 转义）。
        List<Clothing> recent = clothingMapper.selectList(
                new LambdaQueryWrapper<Clothing>()
                        .eq(Clothing::getUserId, userId)
                        .orderByDesc(Clothing::getCreateTime)
                        .last("LIMIT 6"));

        OverviewVO vo = new OverviewVO();
        vo.setTotalClothing(totalClothing);
        vo.setTotalOutfit(totalOutfit);
        vo.setByCategory(byCategory);
        vo.setByStyle(byStyle);
        vo.setRecent(recent);
        // 品类数是「实际用到几个」，字典表里那 5 个不一定都有衣服，所以只数 value > 0 的。
        vo.setCategoryCount(byCategory.stream().filter(c -> c.getValue() > 0).count());
        // byStyle 已经按 tag_value 分组，一行就是一个风格，行数即风格数。
        vo.setStyleCount((long) byStyle.size());
        return vo;
    }

    /**
     * 管理端--数据报表
     * @return
     */
    @Override
    public AdminOverviewVO getAdminOverview() {
        // 三个总数：不带任何条件，全表计数
        Long totalUser = userMapper.selectCount(null);
        Long totalClothing = clothingMapper.selectCount(null);
        Long totalOutfit = outfitMapper.selectCount(null);

        // 今日新增：create_time >= 今天 0 点
        Long todayNewUser = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, LocalDate.now().atStartOfDay()));

        AdminOverviewVO vo = new AdminOverviewVO();
        vo.setTotalUser(totalUser);
        vo.setTotalClothing(totalClothing);
        vo.setTotalOutfit(totalOutfit);
        vo.setTodayNewUser(todayNewUser);

        //饼图
        vo.setCategoryDistribution(clothingMapper.countByCategory());
        vo.setSourceDistribution(outfitMapper.countBySource());

        //折线图
        vo.setUserTrend(buildUserTrend());
        return vo;
    }

    /**
     * 最近 7 天（含今天）的新增用户趋势。
     * <p>
     * 核心问题是「补零」：SQL 的 GROUP BY 只能对已存在的行分组，
     * 某天没人注册，结果里就根本没有那一天。直接拿结果当日期数组，
     * x 轴会变成间隔不均匀的几个日期，折线形状是错的，而且不报错。
     * <p>
     * 所以反过来做：先由 Java 生成完整的 7 天日历，再把查询结果做成
     * 「日期 → 数量」的 Map，逐天去捞，捞不到就填 0。
     */
    private AdminOverviewVO.TrendVO buildUserTrend() {
        LocalDate startDate = LocalDate.now().minusDays(6);   // 含今天共 7 天

        // 查询结果只有「有注册」的日期，可能不足 7 行
        List<AdminOverviewVO.TrendPointVO> rows =
                userMapper.countDaily(startDate.atStartOfDay());

        // 转成 Map，方便按日期取值；dt 是 String，与下方 LocalDate.toString() 格式一致（yyyy-MM-dd）
        Map<String, Long> countMap = rows.stream()
                .collect(Collectors.toMap(AdminOverviewVO.TrendPointVO::getDt,
                        AdminOverviewVO.TrendPointVO::getCnt));

        List<String> dates = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            // 必须用 LocalDate（只有日期没有时分秒），用 LocalDateTime 会得到 2026-11-04T14:51，
            // 与 SQL 里的 yyyy-MM-dd 永远匹配不上，结果是全 0 的直线
            String day = startDate.plusDays(i).toString();
            dates.add(day);
            values.add(countMap.getOrDefault(day, 0L));
        }

        AdminOverviewVO.TrendVO trend = new AdminOverviewVO.TrendVO();
        trend.setDates(dates);
        trend.setValues(values);
        return trend;
    }

    // ===================== 管理端数据导出 =====================

    /** 导出里的时间统一成这个格式，Excel 打开不会变成数字 */
    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出上限。为什么要有：不加限制时，一个几万用户的库点一次导出就是几万行 JSON，
     * 前端转 CSV 会直接卡死。分页导出是更正规的做法，但毕设体量下加个上限更划算。
     */
    private static final int EXPORT_LIMIT = 5000;

    @Override
    public List<Map<String, Object>> exportRows(String type) {
        // switch 里直接列全三种 type，其他值抛 400。
        // 不做"未知 type 返回空列表"：那样前端会以为导出成功但文件是空的，比报错更难查
        return switch (type == null ? "" : type) {
            case "users" -> exportUsers();
            case "category-stats" -> exportNameValue(clothingMapper.countByCategory());
            case "scene-stats" -> exportNameValue(outfitMapper.countByScene());
            default -> throw new BusinessException(ResultCode.PARAM_ERROR);
        };
    }

    /**
     * 账号字段白名单。
     * <p>
     * 注意这里是一个个 {@code put} 出来的，不是把 User 对象整个序列化出去——
     * password / avatar / deleted 这些字段因此天然不在结果里。
     * 这不是"少写几个字段"，是安全边界：导出文件会被下载、转发、存到别人电脑上。
     */
    private List<Map<String, Object>> exportUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByAsc(User::getId)
                .last("LIMIT " + EXPORT_LIMIT));

        List<Map<String, Object>> rows = new ArrayList<>(users.size());
        for (User user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("nickname", user.getNickname());
            row.put("role", user.getRole());
            row.put("status", user.getStatus());
            row.put("createTime", user.getCreateTime() == null
                    ? "" : user.getCreateTime().format(EXPORT_TIME_FORMAT));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 把「名称 + 数量」的聚合结果转成「名称 + 数量 + 占比」。
     * <p>
     * 占比在 Java 里算而不是写进 SQL：SQL 里做除法要处理除零、精度和 NULL，
     * 而在内存里算只有几行数据，代价可以忽略。
     */
    private List<Map<String, Object>> exportNameValue(List<AdminOverviewVO.NameValueVO> source) {
        long total = source.stream()
                .mapToLong(v -> v.getValue() == null ? 0L : v.getValue())
                .sum();

        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (AdminOverviewVO.NameValueVO item : source) {
            long count = item.getValue() == null ? 0L : item.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.getName());
            row.put("value", count);
            // 保留一位小数：先乘 1000 取整再除 10，避免 12.300000000000001 这种浮点尾巴
            row.put("ratio", total == 0 ? 0.0 : Math.round(count * 1000.0 / total) / 10.0);
            rows.add(row);
        }
        return rows;
    }

}
