package com.liuzhangjie.wardrobe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.Outfit;
import com.liuzhangjie.wardrobe.entity.OutfitDTO;
import com.liuzhangjie.wardrobe.service.OutfitService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/outfit")
@Tag(name = "搭配相关接口")
public class OutfitController {

    private final OutfitService outfitService;

    public OutfitController(OutfitService outfitService) {
        this.outfitService = outfitService;
    }

    /**
     * 搭配分页列表，「我的搭配」页面用。
     * 前端 Outfits.vue 传的是 { page: 1, size: 60 }，取返回的 records 渲染卡片。
     * <p>
     * 两个参数都给默认值：即使前端一个参数都不传，也不会因为 Integer 拆箱报 NPE。
     */
    @GetMapping("/page")
    public Result<Page<Outfit>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false)String keyword,
            @RequestParam(required = false)String  scene) {
        return Result.ok(outfitService.page(page, size,keyword,scene));
    }

    /**
     * 新增搭配
     * @param dto
     * @return
     */
    @PostMapping
    public Result<Long> add(@RequestBody OutfitDTO dto) {
        return Result.ok(outfitService.add(dto));
    }

    /**
     * 详情
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<Outfit> detail(@PathVariable Long id){
        return Result.ok(outfitService.detail(id));
    }

    /**
     * 修改搭配
     * @param id
     * @param dto
     * @return
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody OutfitDTO dto){
        outfitService.update(id,dto);
        return Result.ok();
    }

    /**
     * 删除
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id){
        outfitService.delete(id);
        return Result.ok();
    }

}
