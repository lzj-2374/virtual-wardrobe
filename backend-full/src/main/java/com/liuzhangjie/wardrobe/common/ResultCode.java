package com.liuzhangjie.wardrobe.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码。
 * 分段规划：1xxx 用户、2xxx 衣柜、3xxx 搭配、4xxx AI，
 * 这样看到 code 就知道是哪个模块出的问题。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),

    // ===== 通用 =====
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    SYSTEM_ERROR(500, "服务器异常"),

    // ===== 用户模块 10xx =====
    USERNAMEANDPASSWORD_EMPTY(1001, "用户名或密码不能为空"),
    //PASSWORD_EMPTY(1002, "密码不能为空"),
    PASSWORD_FORMAT_ERROR(1003, "密码长度必须为 6~12 位"),
    USERNAME_EXISTS(1004, "用户名已存在"),
    USER_NOT_FOUND(1005, "用户不存在"),
    PASSWORD_ERROR(1006, "用户名或密码错误"),
    ACCOUNT_DISABLED(1007, "账号已被停用"),

    // ===== 衣柜模块 20xx =====
    CLOTHING_NOT_FOUND(2001, "衣物不存在"),
    CLOTHING_NO_PERMISSION(2002, "无权操作该衣物"),
    CLOTHING_IN_USE(2003, "该衣物正在被搭配使用，请先从搭配中移除"),

    // ===== 搭配模块 30xx =====
    OUTFIT_NOT_FOUND(3001, "搭配不存在"),
    OUTFIT_ITEM_EMPTY(3002, "请至少选择一件单品"),
    OUTFIT_ITEM_INVALID(3003, "搭配中包含不存在或不属于你的衣物"),
    OUTFIT_ROLE_DUPLICATE(3004, "同一个位置只能放一件单品"),

    // ===== AI 模块 40xx =====
    AI_MESSAGE_EMPTY(4001, "先说说你想穿去什么场合吧"),
    AI_RECOMMENDATION_NOT_FOUND(4002, "推荐结果不存在或已失效"),
    AI_RECOMMENDATION_EMPTY(4003, "这套推荐里没有可用单品"),
    AI_TASK_NOT_FOUND(4004, "生图任务不存在"),
    AI_TASK_RUNNING(4005, "这套搭配正在生成中，请稍候"),
    AI_WARDROBE_EMPTY(4006, "衣柜还是空的，先录入几件衣服再来问"),

    // ===== 文件模块 50xx =====
    FILE_EMPTY(5001, "上传文件不能为空"),
    FILE_TYPE_ERROR(5002, "只支持 jpg / jpeg / png / gif / webp / bmp 格式"),
    FILE_UPLOAD_ERROR(5003, "文件上传失败，请稍后重试");

    private final Integer code;
    private final String message;
}
