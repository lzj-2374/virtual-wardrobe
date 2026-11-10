package com.liuzhangjie.wardrobe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.entity.*;

public interface UserService {

    /**
     * 用户注册
     * @param dto
     */
    void register(RegisterDTO dto);

    /**
     * 登录
     * @param dto
     * @return
     */
    LoginVO login(LoginDTO dto);

    /**
     * 回显
     * @param userId
     * @return
     */
    User getById(Long userId);


    /**
     * 修改个人中心数据
     * @return
     */
    void updateById(Long userId, UpdateProfileDTO dto);

    /**
     * 管理端--分页查询
     * @param page
     * @param size
     * @param keyword
     * @param status
     * @return
     */
    Page<User> page(Integer page, Integer size, String keyword, Integer status);

    /**
     * 启用/禁用账号
     * @param id
     * @param userStatusDTO
     */
    void userStatus(Long id, UserStatusDTO userStatusDTO);

    /**
     * 重置密码
     * @param id
     */
    void rePassword(Long id);
}
