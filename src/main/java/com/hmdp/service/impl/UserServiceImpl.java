package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.判断实际号码格式是否正确
        if(!RegexUtils.isPhoneInvalid(phone)){
            //不正确，返回错误信息
            return Result.fail("手机号码格式错误");
        }

        //2.正确，随机生成6位数字的验证码
        String code = RandomUtil.randomNumbers(6);

        //3.将生成的验证码保存到Redis当中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.记录日志
        log.info("生成登录验证码：{}",code);

        //5.返回
        return Result.ok();
    }

    /**
     * 用户登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        //1.校验手机号是否合规
        if(!RegexUtils.isPhoneInvalid(phone)){
            //不正确，返回错误信息
            return Result.fail("手机号码格式错误");
        }

        //2.从Redis中获取验证码，检验是否相等
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(!code.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }

        //3.正确，根据手机号查询用户信息 select * from tb_user where phone = phone
        User user = query().eq("phone", loginForm.getPhone()).one();

        //4.判断是否为新用户，即是否查询得到
        if(user == null){
            //没查询到，创建并初始化user对象
            user = User.builder().phone(loginForm.getPhone())
                    .nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10))
                    .password(DEFAULT_PASSWORD)
                    .build();

            //插入user
            save(user);
        }
        //5.将user对象储存到Redis中
        //5.1 生成登陆凭证token
        String token = UUID.randomUUID(false).toString();

        //5.2.将对象转化为map集合
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //5.3.存储Redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,map);

        //5.4设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //返回token
        return Result.ok(token);
    }

    /**
     * 用户签到
     * @return
     */
    @Override
    public Result sign() {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();

        //2.获取今天日期
        LocalDateTime now = LocalDateTime.now();

        //3.拼接字符串
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        //4.计算今天为本月第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.往redis中指定偏移量位置设值
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }

    /**
     * 统计包括今天在内的连续签到天数
     * @return
     */
    @Override
    public Result signCount() {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();

        //2.获取今天日期
        LocalDateTime now = LocalDateTime.now();

        //3.拼接字符串
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        //4.计算今天为本月第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.获取本月第1天到今日的bit位
        List<Long> list = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(list == null || list.isEmpty()){
            return Result.ok(0);
        }
        Long num = list.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }

        //6.将获取的十进制数与1进行与运算，计算连续天数
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 用户退出功能
     * @return
     */
    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }
}
