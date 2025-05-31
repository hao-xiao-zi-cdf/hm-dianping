package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: 34255
 * Date: 2025-05-31
 * Time: 9:50
 */
@Slf4j
@SpringBootTest
public class ChainLockTest {

    @Resource
    @Qualifier("RedissonClient")
    private RedissonClient redissonClient;

    @Resource
    @Qualifier("RedissonClient2")
    private RedissonClient redissonClient1;

    @Resource
    @Qualifier("RedissonClient3")
    private RedissonClient redissonClient2;

    private RLock lock;

    @BeforeEach
    void setUp(){
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient1.getLock("order");
        RLock lock3 = redissonClient2.getLock("order");

        //获取连锁
        lock = redissonClient.getMultiLock(lock1,lock2,lock3);
    }

    @Test
    public void method1() throws InterruptedException {
        boolean flag = lock.tryLock(1L, TimeUnit.HOURS);
        if(!flag){
            log.info("第1次获取锁失败");
        }
        log.info("第1次获取锁成功");
        method2();
        lock.unlock();
    }

    @Test
    public void method2() throws InterruptedException {
        boolean flag = lock.tryLock(1L, TimeUnit.SECONDS);
        if(!flag){
            log.info("第2次获取锁失败");
        }
        log.info("第2次获取锁成功");
        lock.unlock();
    }
}
