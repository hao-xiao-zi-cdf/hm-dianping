package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtils;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheUtils cacheUtils;

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result selectShopById(Long id) {

        //缓存穿透
        //Shop shop = queryWithPenetration(id);

        //缓存击穿
        //Shop shop = queryWithMutex(id);


        //逻辑过期
        //Shop shop = queryWithLogicExpired(id);

        //工具类解决缓存穿透
        //Shop shop = cacheUtils
                //.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //工具类解决缓存击穿
        Shop shop = cacheUtils.queryWithLogicExpired(CACHE_SHOP_KEY, id, Shop.class, 20L, TimeUnit.SECONDS, this::getById);

        if(shop == null){
            return Result.fail("商铺信息错误");
        }

        //返回查询到的商铺信息
        return Result.ok(shop);
    }

    /**
     * 更新商铺信息
     * @param shop
     */
    @Override
    public Result modify(Shop shop) {
        if(shop.getId() == null){
            return Result.fail("商铺不存在");
        }

        //修改数据库商铺信息
        updateById(shop);

        //删除redis缓存信息
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    /**
     * 尝试获取互斥锁
     * @param id
     * @return
     */
    private boolean tryLock(Long id){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 还锁
     * @param id
     */
    private void unLock(Long id){
        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }

    /**
     * 缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        //1.根据id先去redis缓存中查询
        String shopString = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.存在，直接返回该商铺
        if (!StrUtil.isBlank(shopString)) {
            //将字符串转化为shop对象
            Shop shop = JSONUtil.toBean(shopString, Shop.class);
            return shop;
        }

        //3.判断是否为空值""，避免缓存穿透
        if(shopString != null){
            return null;
        }

        //4.不存在，加锁重建缓存
        //4.1尝试获取互斥锁
        boolean flag = tryLock(id);

        Shop shop = null;

        try {
            //4.2获取失败，休眠一段时间后重新获取商铺信息
            if(!flag) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.3获取成功，查询数据库，重建缓存
            //根据id去数据库中查询
            shop = getById(id);
            //模拟复杂查询，耗时较长
            Thread.sleep(200);

            if(shop == null){
                //将空值""写入redis中,并设置过期时间
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //不存在，返回错误信息
                return null;
            }

            //存在，把存到redis缓存中，并设置缓存时间
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //4.4还锁
            unLock(id);
        }

        return shop;
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPenetration(Long id){
        //根据id先去redis缓存中查询
        String shopString = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //存在，直接返回该商铺
        if (!StrUtil.isBlank(shopString)) {
            //将字符串转化为shop对象
            Shop shop = JSONUtil.toBean(shopString, Shop.class);
            return shop;
        }

        //判断是否为空值""，避免缓存穿透
        if(shopString != null){
            return null;
        }

        //不存在，根据id去数据库中查询
        Shop shop = getById(id);

        if(shop == null){
            //将空值""写入redis中,并设置过期时间
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //不存在，返回错误信息
            return null;
        }

        //存在，把存到redis缓存中，并设置缓存时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期
     * @return
     */
    public Shop queryWithLogicExpired(Long id){
        //1.根据id先去redis缓存中查询
        String shopString = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.未命中，直接返回
        if (StrUtil.isBlank(shopString)) {
            return null;
        }

        //3.命中，将JSON字符串反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(shopString, RedisData.class);

        //4.判断逻辑过期时间是否超出当前时间
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject object = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(object, Shop.class);

        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1否，直接返回商铺信息
            return shop;
        }

        //4.2是，尝试获取互斥锁
        boolean flag = tryLock(id);
        //获取成功
        if(flag){
            //创建新线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try{
                    //完成缓存重建
                    saveShop2Redis(1L,10L);
                }catch (InterruptedException e){
                    throw new RuntimeException();
                }finally {
                    //释放锁
                    unLock(id);
                }
            });
        }
        //无论获取锁是否成功，都需返回商户信息
        return shop;
    }

    /**
     * 缓存预热，加载热点缓存
     */
    public void saveShop2Redis(Long id,Long expireTime) throws InterruptedException {
        //根据id到数据库查询数据
        Shop shop = getById(id);
        Thread.sleep(200);

        //封装数据，设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));

        //保存到缓存中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
}
