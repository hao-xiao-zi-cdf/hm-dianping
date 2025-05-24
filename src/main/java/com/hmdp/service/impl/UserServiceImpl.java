package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        log.info("session信息1{}",session);
        //1.判断实际号码格式是否正确
        if(!RegexUtils.isPhoneInvalid(phone)){
            //不正确，返回错误信息
            return Result.fail("手机号码格式错误");
        }

        //2.正确，随机生成6位数字的验证码
        String code = RandomUtil.randomNumbers(6);

        Map<String,Object> map = new HashMap();
        map.put("phone",phone);
        map.put("code",code);

        //3.将生成的验证码保存到session域当中
        session.setAttribute(VERIFY_INFO,map);

        //4.记录日志
        log.info("生成登录验证码：{}",map.get("code"));

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
        log.info("session信息2{}",session);
        //1.校验手机号和验证码是否正确
        Map<String,Object> map = (Map<String,Object>)session.getAttribute(VERIFY_INFO);
        if(!map.get("phone").equals(loginForm.getPhone()) || !map.get("code").equals(loginForm.getCode())){
            //不正确，返回错误信息
            return Result.fail("手机号码或验证码错误");
        }
        //2.正确，根据手机号查询用户信息 select * from tb_user where phone = phone
        User user = query().eq("phone", loginForm.getPhone()).one();

        //3.判断是否为新用户，即是否查询得到
        if(user == null){
            //没查询到，创建并初始化user对象
            user = User.builder().phone(loginForm.getPhone())
                    .nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10))
                    .password(DEFAULT_PASSWORD)
                    .build();
            //插入user
            save(user);
        }
        //4.将user对象储存到session域中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        session.setAttribute("user", userDTO);
        return Result.ok();
    }
}
