package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    @Resource
    public IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();

        //2.判断是关注还是取关
        if(isFollow){
            //2.1关注，向表,redis中插入记录
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            save(follow);
            stringRedisTemplate.opsForSet().add("follow:" + id,userId.toString());
        }else {
            //2.2取关，移出表,redis中记录
            remove(new QueryWrapper<Follow>().eq("user_id",userId).eq("follow_user_id",id));
            stringRedisTemplate.opsForSet().remove("follow:" + id,userId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();

        //2.查询数据库判断是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        if(count > 0){
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result common(Long id) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();

        //2.求两个用户的set交集
        String key1 = "follow:" + id;
        String key2 = "follow:" + userId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok();
        }

        //3.解析出交集ids
        List<Long> ids = new ArrayList<>();
        for(String i : intersect){
            ids.add(Long.parseLong(i));
        }

        //4.根据ids集合获取用户信息
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //4.返回交集
        return Result.ok(users);
    }
}
