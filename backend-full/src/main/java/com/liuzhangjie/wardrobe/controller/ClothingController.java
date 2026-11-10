package com.liuzhangjie.wardrobe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.BatchDeleteDTO;
import com.liuzhangjie.wardrobe.entity.Clothing;
import com.liuzhangjie.wardrobe.entity.ClothingDTO;
import com.liuzhangjie.wardrobe.entity.RecognizeDTO;
import com.liuzhangjie.wardrobe.service.ClothingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.ibatis.annotations.Delete;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clothing")
@Tag(name="衣柜相关接口")
public class ClothingController {

    private final ClothingService clothingService ;

    public ClothingController(ClothingService clothingService) {
        this.clothingService = clothingService;
    }

    /**
     * 分页查询
     * @param page
     * @param size
     * @param category
     * @param keyword
     * @param season
     * @return
     */
    @GetMapping("/page")
    public Result<Page<Clothing>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "6") Integer size,
            @RequestParam(required = false) Integer category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer season) {

        return Result.ok(clothingService.pageQuery(page, size, category, keyword, season));
    }

    /**
     * 详情
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<Clothing> detail(@PathVariable Long id){
        return Result.ok(clothingService.detail(id));
    }

    /**
     * 新增衣物
     * @param dto
     * @return
     */
    @PostMapping
    public Result add(@RequestBody ClothingDTO dto){
        clothingService.add(dto);
        return Result.ok();
    }

    /**
     * 修改
     * @param id
     * @param dto
     * @return
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id,@RequestBody ClothingDTO dto){
       clothingService.update(id,dto);
       return Result.ok();
    }

    /**
     * 删除单件
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id){
        clothingService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     * @param dto 里面装着要删的 id 列表
     * @return
     */
    @DeleteMapping("/batch")
    public Result deleteByIds(@RequestBody BatchDeleteDTO dto){
        clothingService.deleteByIds(dto.getIds());
        return Result.ok();
    }

    /**
     * AI 识别衣物属性，返回建议值供前端填表。
     * <p>
     * 用 POST 而不是 GET：识别本身是一次"计算请求"，可能触发外部模型调用，
     * 不应该被浏览器/代理缓存，也不该出现在 URL 里。
     * <p>
     * 这个接口不写库，用户确认表单后仍然走 {@code POST /clothing}。
     */
    @PostMapping("/recognize")
    public Result<ClothingDTO> recognize(@RequestBody RecognizeDTO dto){
        return Result.ok(clothingService.recognize(dto));
    }

}
