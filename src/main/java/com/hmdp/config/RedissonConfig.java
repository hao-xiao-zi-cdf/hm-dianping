package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: 34255
 * Date: 2025-05-30
 * Time: 9:56
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient RedissonClient(){
        //添加配置
        Config config = new Config();
        //添加redis地址，这里添加了单点的地址，也可以使用config.useclusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://192.168.88.130:6379").setPassword("123321");
        //创建redisson客户端
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient RedissonClient2(){
        //添加配置
        Config config = new Config();
        //添加redis地址，这里添加了单点的地址，也可以使用config.useclusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        //创建redisson客户端
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient RedissonClient3(){
        //添加配置
        Config config = new Config();
        //添加redis地址，这里添加了单点的地址，也可以使用config.useclusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://127.0.0.1:6380");
        //创建redisson客户端
        return Redisson.create(config);
    }
}
