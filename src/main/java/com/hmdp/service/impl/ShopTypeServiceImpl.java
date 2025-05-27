package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商户类型
     * @return
     */
    @Override
    public Result selectShopType() {
        //先去查询缓存
        List<String> shopTypeStringList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        List<ShopType> shopTypeList = new ArrayList<>();

        //存在，直接返回
        if(shopTypeStringList != null && !shopTypeStringList.isEmpty()){
            shopTypeList = getShopTypeList(shopTypeStringList);
            return Result.ok(shopTypeList);
        }

        //不存在，去数据库中查询
        shopTypeList = query().orderByAsc("sort").list();

        //不存在，返回错误信息
        if(shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail("查询商户类型错误");
        }

        //存在，保存到redis缓存中
        for(ShopType shopType : shopTypeList){
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopType));
        }

        //返回查询到的数据
        return Result.ok(shopTypeList);
    }

    private List<ShopType> getShopTypeList(List<String> list){
        List<ShopType> shopTypeList = new ArrayList<>();
        for(String shopTypeString : list){
            ShopType shopType = JSONUtil.toBean(shopTypeString, ShopType.class);
            shopTypeList.add(shopType);
        }
        return shopTypeList;
    }
}
