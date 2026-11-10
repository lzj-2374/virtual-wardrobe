package com.liuzhangjie.wardrobe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.AdminOverviewVO;
import com.liuzhangjie.wardrobe.entity.User;
import com.liuzhangjie.wardrobe.entity.UserStatusDTO;
import com.liuzhangjie.wardrobe.service.StatisticsService;
import com.liuzhangjie.wardrobe.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name="管理员相关接口")
public class AdminController {

    private final UserService userService;
    private final StatisticsService statisticsService;

    public AdminController(UserService userService, StatisticsService statisticsService) {
        this.userService = userService;
        this.statisticsService = statisticsService;
    }

    /**
     * 分页查询
     * @param page
     * @param size
     * @param keyword
     * @param status
     * @return
     */
    @GetMapping("/user/page")
    public Result<Page<User>> page(@RequestParam(defaultValue = "1")Integer page,
                                   @RequestParam(defaultValue = "10")Integer size,
                                   @RequestParam(required = false)String keyword,
                                   @RequestParam(required = false)Integer status){
        return Result.ok(userService.page(page,size,keyword,status));
    }

    /**
     * 启用/禁用账号
     * @param id
     * @param userStatusDTO
     * @return
     */
    @PutMapping("/user/{id}/status")
    public Result userStatus(@PathVariable Long id,@RequestBody UserStatusDTO userStatusDTO){
        userService.userStatus(id,userStatusDTO);
        return Result.ok();
    }

    /**
     * 重置密码
     * @param id
     * @return
     */
    @PostMapping("/user/{id}/reset-password")
    public Result rePassword(@PathVariable Long id){
        userService.rePassword(id);
        return Result.ok();
    }

    /**
     * 管理端--数据报表
     * @return
     */
    @GetMapping("/statistics/overview")
    public Result<AdminOverviewVO> getAdminOverview(){
        return Result.ok(statisticsService.getAdminOverview());
    }

    /**
     * 管理端--数据导出。
     * <p>
     * 返回的是"行数据数组"，CSV 由前端转换。这样做的原因：
     * 文件名的日期后缀、列顺序、以及"导出成功 / 当前没有数据"的提示都在前端，
     * 后端只负责吐出干净的数据，职责更清楚；也避免了在这边处理
     * UTF-8 BOM、Excel 中文乱码这类纯前端问题。
     *
     * @param type users / category-stats / scene-stats
     */
    @GetMapping("/export/{type}")
    public Result<List<Map<String, Object>>> export(@PathVariable String type){
        return Result.ok(statisticsService.exportRows(type));
    }
}
