package com.liuzhangjie.wardrobe.utils;

/**
 * 让 userId 沿着请求传递
 */
public class UserContext {

    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    //设置userId
    public static void set(Long userId){
        THREAD_LOCAL.set(userId);
    }

    //获取userId
    public static Long getUserId(){
        return THREAD_LOCAL.get();
    }

    //清除userId
    public static void remove(){
        THREAD_LOCAL.remove();
    }
}
