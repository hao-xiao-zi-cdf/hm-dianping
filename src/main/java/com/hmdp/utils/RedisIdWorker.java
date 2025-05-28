package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: 34255
 * Date: 2025-05-28
 * Time: 16:26
 */
@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long generateSituationID(String business){
        //1.生成时间戳
        long nowTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long beginTimestamp = LocalDateTime.of(2025, 4, 15, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowTimestamp - beginTimestamp;

        //2.根据自增生成序号id
        //2.1获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //2.2定义格式并格式化当前日期
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        long serialNum = stringRedisTemplate.opsForValue().increment("inc:" + business + ":" + date);

        //3.拼接时间戳和序号id返回
        return timestamp << 32 | serialNum;
    }
}
