package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询博客信息
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //获取用户id
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        //判断当前用户是否点赞,到redis的set集合中判断是否存在当前用户
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, UserHolder.getUser().getId().toString());
        if(score != null){
            blog.setIsLike(true);
        }
        //设置博客用户信息
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            //判断当前用户是否点赞,到redis的set集合中判断是否存在当前用户
            String key = BLOG_LIKED_KEY + current;
            Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
            if(score != null){
                blog.setIsLike(true);
            }
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();

        //2.到redis的set集合中判断是否存在当前用户
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            //2.1 不存在，用户加入set集合，数据库点赞数+1
            stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            update().setSql("liked = liked + 1").eq("id",id).update();
        }else{
            //2.2 存在，用户移出set集合，数据库点赞数-1
            stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            update().setSql("liked = liked - 1").eq("id",id).update();
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.根据blogId到redis中查询排名前五的用户id
        Set<String> set = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if(set == null || set.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>();
        //2.根据id获取用户信息
        for(String element : set){
            ids.add(Long.parseLong(element));
        }

        //3.处理敏感信息，使用DTO对象传输
        List<UserDTO> userDTOList = new ArrayList<>();
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        userDTOList = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //4.返回集合
        return Result.ok(userDTOList);
    }
}
