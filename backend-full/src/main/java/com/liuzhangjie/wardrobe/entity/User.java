package com.liuzhangjie.wardrobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户表。
 * 注意 password 上的 @JsonIgnore：任何接口返回该对象时都不会带出密码。
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文，绝不出现在任何响应里 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    /** 0 普通用户 / 1 管理员 */
    private Integer role;

    /** 1 正常 / 0 停用 */
    private Integer status;

    /** 0 未知 / 1 男 / 2 女 */
    private Integer gender;

    private Integer height;

    private BigDecimal weight;

    private String size;

    /** 风格偏好，逗号分隔 */
    private String stylePref;

    private String city;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
