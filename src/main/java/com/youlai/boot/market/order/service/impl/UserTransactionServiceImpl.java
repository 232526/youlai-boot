package com.youlai.boot.market.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.market.order.mapper.UserTransactionMapper;
import com.youlai.boot.market.order.model.entity.UserTransaction;
import com.youlai.boot.market.order.model.query.UserTransactionQuery;
import com.youlai.boot.market.order.model.vo.UserTransactionPageVO;
import com.youlai.boot.market.order.service.UserTransactionService;
import com.youlai.boot.system.mapper.UserMapper;
import com.youlai.boot.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户交易流水业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
@Service
@RequiredArgsConstructor
public class UserTransactionServiceImpl extends ServiceImpl<UserTransactionMapper, UserTransaction> implements UserTransactionService {

    private final UserTransactionMapper userTransactionMapper;
    private final UserMapper userMapper;

    @Override
    public Page<UserTransactionPageVO> getUserTransactionPage(UserTransactionQuery queryParams) {
        // 数据权限：非管理员只能查看自己的记录
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        // 如果是管理员且指定了用户名，先查询用户ID列表
        if (isRoot && StrUtil.isNotBlank(queryParams.getUsername())) {
            List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                    .like(SysUser::getUsername, queryParams.getUsername())
                    .select(SysUser::getId)
            );
            
            if (CollUtil.isEmpty(users)) {
                // 如果没有找到匹配的用户，返回空分页
                return new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
            }
            
            // 将用户ID列表设置到查询参数中
            List<Long> userIds = users.stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());
            queryParams.setUserIds(userIds);
        }

        Page<UserTransactionPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return userTransactionMapper.getUserTransactionPage(page, queryParams, currentUserId, isRoot);
    }

}
