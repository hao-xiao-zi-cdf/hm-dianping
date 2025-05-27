package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

    /**
     * 根据id查询商品
     * @param id
     * @return
     */
    @Override
    public Result selectShopById(Long id) {
        //根据id先去redis缓存中查询
        String shopString = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //存在，直接返回该商品
        if (shopString != null) {
            //将字符串转化为shop对象
            Shop shop = JSONUtil.toBean(shopString, Shop.class);
            return Result.ok(shop);
        }

        //不存在，根据id去数据库中查询
        Shop shop = getById(id);

        if(shop == null){
            //不存在，返回错误信息
            return Result.fail("商品信息不存在");
        }

        //存在，把存到redis缓存中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));

        //返回查询到的商品信息
        return Result.ok(shop);
    }
}
