package com.youlai.boot.market.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.common.exception.BusinessException;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.market.order.enums.OrderStatusEnum;
import com.youlai.boot.market.order.mapper.SmsMessageContentMapper;
import com.youlai.boot.market.order.mapper.SmsOrderMapper;
import com.youlai.boot.market.order.mapper.SmsPhoneRecordMapper;
import com.youlai.boot.market.order.model.entity.SmsMessageContent;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.model.form.SmsOrderForm;
import com.youlai.boot.market.order.model.query.SmsOrderQuery;
import com.youlai.boot.market.order.model.query.SmsOrderStatisticsQuery;
import com.youlai.boot.market.order.model.vo.SmsOrderDetailVO;
import com.youlai.boot.market.order.model.vo.SmsOrderPageVO;
import com.youlai.boot.market.order.model.vo.SmsOrderStatisticsVO;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.system.mapper.CountryMapper;
import com.youlai.boot.system.mapper.UserMapper;
import com.youlai.boot.system.model.entity.Country;
import com.youlai.boot.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 短信订单业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsOrderServiceImpl extends ServiceImpl<SmsOrderMapper, SmsOrder> implements SmsOrderService {

    private final SmsOrderMapper smsOrderMapper;
    private final CountryMapper countryMapper;
    private final SmsMessageContentMapper smsMessageContentMapper;
    private final SmsPhoneRecordMapper smsPhoneRecordMapper;
    private final UserMapper userMapper;

    /**
     * 手机号记录保存线程池
     */
    private static final ThreadPoolExecutor PHONE_RECORD_EXECUTOR = new ThreadPoolExecutor(
            4,
            8,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(32),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 每批插入的记录数
     */
    private static final int BATCH_SIZE = 1000;

    @Override
    public Page<SmsOrderPageVO> getSmsOrderPage(SmsOrderQuery queryParams) {
        // 数据权限：非管理员只能查看自己的订单
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<SmsOrderPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        Page<SmsOrderPageVO> result = smsOrderMapper.getSmsOrderPage(page, queryParams, currentUserId, isRoot);

        // 处理关联字段：用户名、短信数量、文本数量
        if (CollUtil.isNotEmpty(result.getRecords())) {
            List<SmsOrderPageVO> records = result.getRecords();

            // 1. 收集所有用户ID（从createBy字段的字符串转换为Long）
            Set<Long> userIds = records.stream().map(record -> {
                try {
                    return Long.parseLong(record.getCreateBy());
                } catch (NumberFormatException e) {
                    return null;
                }
            }).filter(id -> id != null).collect(Collectors.toSet());

            // 批量查询用户名
            Map<Long, String> userNameMap = Map.of();
            if (CollUtil.isNotEmpty(userIds)) {
                List<SysUser> users = userMapper.selectBatchIds(userIds);
                userNameMap = users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));
            }

            // 2. 收集所有订单ID，批量查询短信数量和文本数量
            List<String> orderNos = records.stream().map(SmsOrderPageVO::getOrderNo).collect(Collectors.toList());

            // 查询每个订单的短信内容数量
            LambdaQueryWrapper<SmsMessageContent> contentWrapper = new LambdaQueryWrapper<>();
            contentWrapper.in(SmsMessageContent::getOrderNo, orderNos);
            List<SmsMessageContent> allContents = smsMessageContentMapper.selectList(contentWrapper);
            Map<String, Long> contentCountMap = allContents.stream().collect(Collectors.groupingBy(SmsMessageContent::getOrderNo, Collectors.counting()));

            // 查询每个订单的不重复手机号数量
            LambdaQueryWrapper<SmsPhoneRecord> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.in(SmsPhoneRecord::getOrderNo, orderNos);
            List<SmsPhoneRecord> allPhones = smsPhoneRecordMapper.selectList(phoneWrapper);
            Map<String, Long> phoneCountMap = allPhones.stream().collect(Collectors.groupingBy(SmsPhoneRecord::getOrderNo, Collectors.mapping(SmsPhoneRecord::getPhoneNumber, Collectors.toSet()))).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));

            // 3. 填充数据
            final Map<Long, String> finalUserNameMap = userNameMap;
            records.forEach(record -> {
                // 填充用户名（将Long类型的用户ID转换为String类型的用户名）
                try {
                    Long userId = Long.parseLong(record.getCreateBy());
                    record.setCreateBy(finalUserNameMap.getOrDefault(userId, ""));
                } catch (NumberFormatException e) {
                    record.setCreateBy("");
                }

                // 填充文本数量
                Long contentCount = contentCountMap.getOrDefault(record.getOrderNo(), 0L);
                record.setContentCount(contentCount.intValue());

                // 填充短信数量（不重复手机号数量 × 文本数量）
                Long phoneCount = phoneCountMap.getOrDefault(record.getOrderNo(), 0L);
                record.setSmsCount(Math.toIntExact(phoneCount));
            });
        }

        return result;
    }

    @Override
    public Page<SmsOrderStatisticsVO> getSmsOrderStatisticsPage(SmsOrderStatisticsQuery queryParams) {
        // 数据权限：非管理员只能查看自己的订单
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<SmsOrderStatisticsVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        Page<SmsOrderStatisticsVO> smsOrderStatisticsPage = smsOrderMapper.getSmsOrderStatisticsPage(page, queryParams, currentUserId, isRoot);
        List<Long> userIds = smsOrderStatisticsPage.getRecords().stream().map(SmsOrderStatisticsVO::getUserId).toList();
        // 批量查询用户名
        Map<Long, String> userNameMap = Map.of();
        if (CollUtil.isNotEmpty(userIds)) {
            List<SysUser> users = userMapper.selectBatchIds(userIds);
            userNameMap = users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));
        }

        for (SmsOrderStatisticsVO record : smsOrderStatisticsPage.getRecords()) {
            record.setUserName(userNameMap.getOrDefault(record.getUserId(), ""));
        }
        return smsOrderStatisticsPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(SmsOrderForm formData) {
        // 校验国家是否存在
        Country country = countryMapper.selectById(formData.getCountryId());
        if (country == null) {
            throw new BusinessException("国家不存在");
        }
        if (country.getIsEnabled() == 0) {
            throw new BusinessException("该国家已停用");
        }

        // 将时间戳转换为 LocalDateTime
        LocalDateTime scheduledTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(formData.getScheduledTime()), java.time.ZoneId.systemDefault());

        // 校验预约时间
        if (scheduledTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间不能早于当前时间");
        }

        // 创建订单
        SmsOrder order = new SmsOrder();
        String orderId = generateOrderNo();

        order.setOrderNo(orderId);
        order.setCountryId(formData.getCountryId());
        order.setCountryName(country.getName());
        order.setHasAreaCode(formData.getHasAreaCode());
        order.setScheduledTime(scheduledTime);
        order.setStatus(OrderStatusEnum.PENDING.getValue());
        order.setSuccessCount(0);
        order.setFailCount(0);
        order.setTotalCount(formData.getPhoneNumberList().size());
        order.setRemark(formData.getRemark());

        this.save(order);

        // 保存短信内容
        List<String> messageContentList = formData.getMessageContentList();
        List<Long> contentIds = new java.util.ArrayList<>();
        for (int i = 0; i < messageContentList.size(); i++) {
            SmsMessageContent content = new SmsMessageContent();
            content.setOrderNo(orderId);
            content.setContent(messageContentList.get(i));
            content.setContentSort(i);
            smsMessageContentMapper.insert(content);
            contentIds.add(content.getContentId());
        }

        // 保存手机号记录（异步多线程）
        // 规则：只有1条文案时，所有号码发同一条；多条文案时，每个号码随机分配一条文案
        List<String> phoneNumberList = formData.getPhoneNumberList();
        Random random = new Random();

        // 先在主线程构建所有记录对象
        List<SmsPhoneRecord> allRecords = new ArrayList<>(phoneNumberList.size());
        if (contentIds.size() == 1) {
            Long contentId = contentIds.get(0);
            for (String phoneNumber : phoneNumberList) {
                SmsPhoneRecord record = new SmsPhoneRecord();
                record.setOrderNo(orderId);
                record.setContentId(contentId);
                record.setPhoneNumber(phoneNumber);
                record.setSendStatus(0);
                allRecords.add(record);
            }
        } else {
            for (String phoneNumber : phoneNumberList) {
                Long contentId = contentIds.get(random.nextInt(contentIds.size()));
                SmsPhoneRecord record = new SmsPhoneRecord();
                record.setOrderNo(orderId);
                record.setContentId(contentId);
                record.setPhoneNumber(phoneNumber);
                record.setSendStatus(0);
                allRecords.add(record);
            }
        }

        // 分批多线程异步插入
        asyncBatchInsertPhoneRecords(allRecords);

        return orderId;
    }

    @Override
    public SmsOrderDetailVO getOrderDetail(Long id) {
        SmsOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 查询短信内容列表
        LambdaQueryWrapper<SmsMessageContent> contentWrapper = new LambdaQueryWrapper<>();
        contentWrapper.eq(SmsMessageContent::getOrderNo, id).orderByAsc(SmsMessageContent::getContentSort);
        List<SmsMessageContent> contentList = smsMessageContentMapper.selectList(contentWrapper);
        List<String> messageContentList = contentList.stream().map(SmsMessageContent::getContent).toList();

        // 查询手机号列表
        LambdaQueryWrapper<SmsPhoneRecord> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(SmsPhoneRecord::getOrderNo, id).orderByAsc(SmsPhoneRecord::getCreateTime);
        List<SmsPhoneRecord> phoneRecordList = smsPhoneRecordMapper.selectList(phoneWrapper);
        List<String> phoneNumberList = phoneRecordList.stream().map(SmsPhoneRecord::getPhoneNumber).toList();

        SmsOrderDetailVO detailVO = new SmsOrderDetailVO();
        detailVO.setId(order.getId());
        detailVO.setOrderNo(order.getOrderNo());
        detailVO.setCountryId(order.getCountryId());
        detailVO.setCountryName(order.getCountryName());
        detailVO.setHasAreaCode(order.getHasAreaCode());
        detailVO.setScheduledTime(order.getScheduledTime());
        detailVO.setMessageContentList(messageContentList);
        detailVO.setPhoneNumberList(phoneNumberList);
        detailVO.setStatus(order.getStatus());
        detailVO.setStatusDesc(getStatusDesc(order.getStatus()));
        detailVO.setSuccessCount(order.getSuccessCount());
        detailVO.setFailCount(order.getFailCount());
        detailVO.setTotalCount(order.getTotalCount());
        detailVO.setRemark(order.getRemark());
        detailVO.setCreateTime(order.getCreateTime());

        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long id) {
        SmsOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!OrderStatusEnum.PENDING.getValue().equals(order.getStatus())) {
            throw new BusinessException("只有待发送的订单才能取消");
        }

        order.setStatus(OrderStatusEnum.CANCELLED.getValue());
        this.updateById(order);

        //将订单中的手机号记录取消
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, order.getOrderNo()).eq(SmsPhoneRecord::getSendStatus, 0); // 只更新待发送的记录

        List<SmsPhoneRecord> phoneRecordList = smsPhoneRecordMapper.selectList(wrapper);
        for (SmsPhoneRecord record : phoneRecordList) {
            record.setSendStatus(-2);
            smsPhoneRecordMapper.updateById(record);
        }
        return true;
    }

    @Override
    public List<SmsOrder> getPendingOrdersToExecute() {
        // 查询状态为待发送且预约时间小于等于当前时间的订单
        LambdaQueryWrapper<SmsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsOrder::getStatus, OrderStatusEnum.PENDING.getValue()).le(SmsOrder::getScheduledTime, LocalDateTime.now()).orderByAsc(SmsOrder::getScheduledTime);
        return this.list(wrapper);
    }

    @Override
    public List<SmsPhoneRecord> getPhoneRecordsByOrderId(String orderId) {
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderId).orderByAsc(SmsPhoneRecord::getCreateTime);
        return smsPhoneRecordMapper.selectList(wrapper);
    }

    @Override
    public List<SmsMessageContent> getMessageContentsByOrderId(String orderId) {
        LambdaQueryWrapper<SmsMessageContent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsMessageContent::getOrderNo, orderId).orderByAsc(SmsMessageContent::getContentSort);
        return smsMessageContentMapper.selectList(wrapper);
    }

    /**
     * 生成订单编号
     */
    private String generateOrderNo() {
        String date = DateUtil.format(new Date(), "yyyyMMdd");
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "ORD" + date + uuid;
    }

    /**
     * 获取状态描述
     */
    private String getStatusDesc(Integer status) {
        for (OrderStatusEnum statusEnum : OrderStatusEnum.values()) {
            if (statusEnum.getValue().equals(status)) {
                return statusEnum.getLabel();
            }
        }
        return "";
    }

    /**
     * 异步多线程批量插入手机号记录
     *
     * @param allRecords 待插入的所有手机号记录
     */
    private void asyncBatchInsertPhoneRecords(List<SmsPhoneRecord> allRecords) {
        // 将记录按 BATCH_SIZE 分批
        List<List<SmsPhoneRecord>> batches = new ArrayList<>();
        for (int i = 0; i < allRecords.size(); i += BATCH_SIZE) {
            batches.add(allRecords.subList(i, Math.min(i + BATCH_SIZE, allRecords.size())));
        }

        // 多线程异步执行每一批的插入
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<SmsPhoneRecord> batch : batches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 使用 MyBatis-Plus 的批量插入
                    for (SmsPhoneRecord record : batch) {
                        smsPhoneRecordMapper.insert(record);
                    }
                } catch (Exception e) {
                    log.error("批量插入手机号记录失败, orderNo={}, batchSize={}", batch.get(0).getOrderNo(), batch.size(), e);
                }
            }, PHONE_RECORD_EXECUTOR);
            futures.add(future);
        }

        // 异步等待所有批次完成并记录日志
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("手机号记录全部保存完成, 总数={}", allRecords.size()))
                .exceptionally(ex -> {
                    log.error("手机号记录保存过程中出现异常", ex);
                    return null;
                });
    }

}
