package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * Description: 定义锁的接口
 * User: 34255
 * Date: 2025-05-29
 * Time: 17:53
 */
public interface ILock {

    /**
     * 获取锁
     * @param business
     * @return
     */
    boolean tryLock(String business, Long time);

    /**
     * 释放锁
     * @param business
     */
    void unLock(String business);
}
