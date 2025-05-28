package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: 34255
 * Date: 2025-05-27
 * Time: 21:28
 */
@Slf4j
@Component
public class CacheUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setLogicExpired(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(unit.toSeconds(time)));
        redisData.setData(value);

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        //根据id先去redis缓存中查询
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);

        //存在，直接返回
        if (!StrUtil.isBlank(json)) {
            //将字符串转化为shop对象
            R r = JSONUtil.toBean(json, type);
            return r;
        }

        //判断是否为空值""，避免缓存穿透
        if(json != null){
            return null;
        }

        //不存在，根据id去数据库中查询
        R r = dbFallback.apply(id);

        if(r == null){
            //将空值""写入redis中,并设置过期时间
            stringRedisTemplate.opsForValue().set(keyPrefix + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //不存在，返回错误信息
            return null;
        }

        //存在，把存到redis缓存中，并设置缓存时间
        this.set(keyPrefix + id,r,time,unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期
     * @return
     */
    public <R, ID> R queryWithLogicExpired(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit unit, Function<ID,R> dbFallback){
        //1.根据id先去redis缓存中查询
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);

        //2.未命中，直接返回
        if (StrUtil.isBlank(json)) {
            return null;
        }

        //3.命中，将JSON字符串反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        //4.判断逻辑过期时间是否超出当前时间
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject object = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(object, type);

        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1否，直接返回
            return r;
        }

        //4.2是，尝试获取互斥锁
        boolean flag = tryLock(id);
        //获取成功
        if(flag){
            //创建新线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try{
                    //查询数据库
                    R result = dbFallback.apply(id);
                    //添加入缓存
                    this.setLogicExpired(keyPrefix + id,result,time,unit);
                }catch (Exception e){
                    throw new RuntimeException();
                }finally {
                    //释放锁
                    unLock(id);
                }
            });
        }
        //无论获取锁是否成功，都需返回商户信息
        return r;
    }

    /**
     * 尝试获取互斥锁
     * @param id
     * @return
     */
    private <ID> boolean tryLock(ID id){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 还锁
     * @param id
     */
    private <ID> void unLock(ID id){
        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }
}
