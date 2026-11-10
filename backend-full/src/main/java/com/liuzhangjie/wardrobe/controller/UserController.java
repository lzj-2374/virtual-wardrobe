package com.liuzhangjie.wardrobe.controller;

import com.github.xingfudeshi.knife4j.annotations.ApiOperationSupport;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.entity.*;
import com.liuzhangjie.wardrobe.service.UserService;
import com.liuzhangjie.wardrobe.utils.UserContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * @param dto
     * @return
     */
    @PostMapping("/register")
    public Result register(@RequestBody RegisterDTO dto){
        log.info("用户注册：{}",dto);
        userService.register(dto);
        return Result.ok();
    }

    /**
     * 登录
     * @param dto
     * @return
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto){

        return Result.ok(userService.login(dto));
    }

    /**
     * 回显
     * @return
     */
    @GetMapping("/profile")
    public Result<User> getUserInfo(){
        Long userId= UserContext.getUserId();
        return Result.ok(userService.getById(userId));
    }

    /**
     * 修改个人中心数据
     * @return
     */
    @PutMapping("/profile")
    public Result updateUserInfo(@RequestBody UpdateProfileDTO dto){
        Long userId= UserContext.getUserId();
        userService.updateById(userId,dto);
        return Result.ok();
    }
}
