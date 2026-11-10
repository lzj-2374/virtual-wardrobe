package com.liuzhangjie.wardrobe.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用的「启用 / 停用」状态。
 * <p>
 * 项目里有两张表都用 status 表示业务状态，而且语义完全一致，
 * 所以统一成同一套值，只定义这一个枚举：
 * <ul>
 *   <li>user 表：1 正常 / 0 停用</li>
 *   <li>clothing 表：1 正常 / 0 已归档</li>
 * </ul>
 * 都是「1 可用、0 不可用」，只是叫法不同。
 * <p>
 * <b>为什么不给每张表各建一个枚举：</b>两张表的取值如果反着来（比如一个 0 正常、
 * 一个 1 正常），很容易在复制代码时搞混，而且混了不会报错，只是业务判断全反。
 * 统一语义之后共用一份，从根上避免这个问题。
 * <p>
 * 注意和 {@code deleted} 区分：deleted 是逻辑删除（记录被用户删掉了），
 * status 是业务状态（记录还在，只是暂时不用）。
 */
@Getter
@AllArgsConstructor
public enum EnableStatus {

    /** 正常：账号可登录、衣物在衣柜里正常显示 */
    ENABLED(1, "正常"),

    /** 停用：user 表示账号被禁用，clothing 表示衣物已归档 */
    DISABLED(0, "停用");

    private final Integer code;

    private final String desc;

}
