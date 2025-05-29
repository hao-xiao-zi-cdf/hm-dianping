package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * Description: 分布式锁
 * User: 34255
 * Date: 2025-05-29
 * Time: 17:53
 */
@Component
public class DistributedLock implements ILock{

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final static String LOCK_PREFIX = "lock:";

    private final static String ID_PREFIX = UUID.randomUUID(true).toString() + "-";

    private static DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 获取锁
     * @param business
     * @return
     */
    @Override
    public boolean tryLock(String business, Long time) {
        //获取当前线程id
        String threadID = Long.toString(Thread.currentThread().getId());
        //存值
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + business, ID_PREFIX + threadID, time, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param business
     */
    @Override
    public void unLock(String business) {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + business),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

    /**
     * 释放锁
     * @param business
     */
//    @Override
//    public void unLock(String business) {
//        //从缓存中获取绑定的标识
//        String identification = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + business);
//        String threadID = Long.toString(Thread.currentThread().getId());
//
//        //判断是否等于当前的标识
//        if(identification.equals(ID_PREFIX + threadID)){
//            //根据key删除缓存
//            stringRedisTemplate.delete(LOCK_PREFIX + business);
//        }
//    }
}
