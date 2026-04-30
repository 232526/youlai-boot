package com.youlai.boot.interfaces.openapi.interceptor;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.boot.common.result.Result;
import com.youlai.boot.common.result.ResultCode;
import com.youlai.boot.framework.security.model.SysUserDetails;
import com.youlai.boot.system.mapper.UserMapper;
import com.youlai.boot.system.model.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 开放API签名验证拦截器
 * <p>
 * 签名生成方式：MD5(apiKey + apiSecret + timestamp)，生成32位字符串（不区分大小写）
 * 请求头参数：
 * - X-Api-Key: API密钥
 * - X-Timestamp: 当前系统时间戳（秒）
 * - X-Sign: 签名字符串
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiSignatureInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    /**
     * 签名有效期（秒），默认5分钟
     */
    private static final long SIGN_EXPIRE_SECONDS = 300;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = request.getHeader("X-Api-Key");
        String timestamp = request.getHeader("X-Timestamp");
        String sign = request.getHeader("X-Sign");

        // 1. 参数校验
        if (StrUtil.hasBlank(apiKey, timestamp, sign)) {
            writeError(response, ResultCode.ACCESS_UNAUTHORIZED, "缺少签名参数（X-Api-Key、X-Timestamp、X-Sign）");
            return false;
        }

        // 2. 时间戳有效期校验
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            writeError(response, ResultCode.USER_REQUEST_PARAMETER_ERROR, "时间戳格式错误");
            return false;
        }

        long currentTimestamp = System.currentTimeMillis() / 1000;
        if (Math.abs(currentTimestamp - ts) > SIGN_EXPIRE_SECONDS) {
            writeError(response, ResultCode.ACCESS_UNAUTHORIZED, "签名已过期");
            return false;
        }

        // 3. 根据apiKey查找用户
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getApiKey, apiKey)
                .eq(SysUser::getIsDeleted, 0)
        );

        if (user == null) {
            writeError(response, ResultCode.ACCESS_UNAUTHORIZED, "无效的API Key");
            return false;
        }

        // 4. 验证签名：MD5(apiKey + apiSecret + timestamp)
        String expectedSign = md5(apiKey + user.getApiSecret() + timestamp);
        if (!sign.equalsIgnoreCase(expectedSign)) {
            writeError(response, ResultCode.ACCESS_UNAUTHORIZED, "签名验证失败");
            return false;
        }

        if (user.getStatus() != 1) {
            writeError(response, ResultCode.ACCOUNT_FROZEN, "账户已被禁用");
            return false;
        }

        if (user.getPrice() < 0) {
            writeError(response, ResultCode.ACCOUNT_FROZEN, "账户余额不足");
            return false;
        }

        // 5. 鉴权通过，将用户信息设置到SecurityContext中，以便后续业务使用SecurityUtils.getUserId()
        SysUserDetails userDetails = new SysUserDetails();
        userDetails.setUserId(user.getId());
        userDetails.setUsername(user.getUsername());
        userDetails.setEnabled(true);
        userDetails.setDeptId(user.getDeptId());

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除SecurityContext，避免线程复用导致的安全问题
        SecurityContextHolder.clearContext();
    }

    /**
     * 计算MD5摘要，返回32位小写十六进制字符串
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    /**
     * 向响应写入错误信息
     */
    private void writeError(HttpServletResponse response, ResultCode resultCode, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.failed(resultCode, msg);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(result));
    }
}
