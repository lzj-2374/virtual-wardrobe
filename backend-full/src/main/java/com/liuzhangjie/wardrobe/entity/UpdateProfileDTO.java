package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改个人资料的入参。
 * <p>
 * 故意只放这 7 个字段，<b>没有 id、username、password、role、status</b>。
 * 这几个字段都由服务端决定：
 * id 从 token 里取，username 是登录凭证不允许改，
 * password 要走单独的修改密码接口（改完还得清掉所有登录态），
 * role / status 只有管理员能碰。
 * <p>
 * 如果图省事直接用 User 当入参，前端在 body 里塞一行 "role": 1
 * 就能把自己提成管理员、塞一行 "password" 就能把密码覆盖成任意值，
 * 这类漏洞叫批量赋值（Mass Assignment）。
 */
@Data
public class UpdateProfileDTO {

    private String nickname;

    /** 0 未知 / 1 男 / 2 女 */
    private Integer gender;

    /** 身高 cm */
    private Integer height;

    /** 体重 kg，数据库是 DECIMAL(5,1)，所以用 BigDecimal 接，别用 Double（会丢精度） */
    private BigDecimal weight;

    /** 常用尺码，如 M / L / XL */
    private String size;

    /** 风格偏好，逗号分隔，如 休闲,通勤 */
    private String stylePref;

    private String city;

}
