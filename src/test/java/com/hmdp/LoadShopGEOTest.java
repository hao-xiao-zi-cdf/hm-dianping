package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.*;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: 34255
 * Date: 2025-06-06
 * Time: 17:09
 */
@SpringBootTest
public class LoadShopGEOTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    /**
     * 往redis中导入商户GEO数据
     */
    @Test
    public void testLoadShopGEO(){
        //1.数据库中查询所有商户数据
        List<Shop> shopList = shopService.query().list();
        if(shopList == null || shopList.isEmpty()){
            return;
        }


        //2.根据商户类型进行分组
        Map<Long,List<Shop>> shopMap = new HashMap<>();
        for(Shop shop : shopList){
            //2.1获取商户类型
            Long typeId = shop.getTypeId();
            //2.2按商户类型分组
            List<Shop> shops = shopMap.get(typeId);
            if(shops == null){
                shopMap.put(typeId,new ArrayList<>());
            }
            shopMap.get(typeId).add(shop);
        }

        //3.根据组别依次插入redis中
        Set<Map.Entry<Long, List<Shop>>> entries = shopMap.entrySet();
        for(Map.Entry<Long, List<Shop>> entry : entries){
            //3.1获取店铺类型
            Long typeId = entry.getKey();
            //3.2获取同类型店铺集合
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> geoLocationList = new ArrayList<>(shops.size());
            for(Shop shop : shops){
                RedisGeoCommands.GeoLocation<String> geoLocation = new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY()));
                geoLocationList.add(geoLocation);
            }
            //3.3根据类型分批插入到redis
            stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY + typeId,geoLocationList);
        }
    }
}
