package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: 34255
 * Date: 2025-05-24
 * Time: 16:06
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session
        HttpSession session = request.getSession();
        //获取用户信息
        Object user = session.getAttribute("user");
        //判断用户信息是否为null
        if(user == null){
            //设置状态码
            response.setStatus(401);
            //拦截
            return false;
        }

        //使用ThreadLocal存储用户信息，方便后续直接使用
        UserHolder.saveUser((UserDTO)user);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户信息
        UserHolder.removeUser();
    }
}
