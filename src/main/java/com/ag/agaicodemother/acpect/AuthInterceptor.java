package com.ag.agaicodemother.acpect;

import com.ag.agaicodemother.annotation.AuthCheck;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.enums.UserRoleEnum;
import com.ag.agaicodemother.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        // 1. 获取当前请求的请求属性对象。
        //    RequestContextHolder 是 Spring 提供的请求上下文持有者，
        //    它内部通过 ThreadLocal 保存当前线程绑定的 RequestAttributes，
        //    因此这里可以安全地获取到当前请求对应的属性信息。
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        // 2. 将通用的 RequestAttributes 强转为 ServletRequestAttributes。
        //    在 Spring MVC 环境中，当前请求属性通常是 ServletRequestAttributes 实现，
        //    通过调用它的 getRequest() 方法即可获得原生 HttpServletRequest 对象。
        //    后续 userService.getLoginUser(request) 需要依赖该 request 来解析当前登录用户。
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 以下为：必须有该权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 没有权限，拒绝
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}
