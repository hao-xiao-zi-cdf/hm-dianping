package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheUtils cacheUtils;

    @Test
    public void testQueryWithLogicExpired() throws InterruptedException {
        //数据库查询数据
        Shop shop = shopService.getById(1L);

        //热点key提前加载
        cacheUtils.setLogicExpired(CACHE_SHOP_KEY + shop.getId(),shop,10L, TimeUnit.SECONDS);
    }


}
