package com.youlai.boot.market.order.service.impl;

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
import com.youlai.boot.system.model.entity.Country;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 短信订单业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Service
@RequiredArgsConstructor
public class SmsOrderServiceImpl extends ServiceImpl<SmsOrderMapper, SmsOrder> implements SmsOrderService {

    private final SmsOrderMapper smsOrderMapper;
    private final CountryMapper countryMapper;
    private final SmsMessageContentMapper smsMessageContentMapper;
    private final SmsPhoneRecordMapper smsPhoneRecordMapper;

    @Override
    public Page<SmsOrderPageVO> getSmsOrderPage(SmsOrderQuery queryParams) {
        // 数据权限：非管理员只能查看自己的订单
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<SmsOrderPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return smsOrderMapper.getSmsOrderPage(page, queryParams, currentUserId, isRoot);
    }

    @Override
    public Page<SmsOrderStatisticsVO> getSmsOrderStatisticsPage(SmsOrderStatisticsQuery queryParams) {
        // 数据权限：非管理员只能查看自己的订单
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<SmsOrderStatisticsVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return smsOrderMapper.getSmsOrderStatisticsPage(page, queryParams, currentUserId, isRoot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(SmsOrderForm formData) {
        // 校验国家是否存在
        Country country = countryMapper.selectById(formData.getCountryId());
        if (country == null) {
            throw new BusinessException("国家不存在");
        }
        if (country.getIsEnabled() == 0) {
            throw new BusinessException("该国家已停用");
        }

        // 将时间戳转换为 LocalDateTime
        LocalDateTime scheduledTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(formData.getScheduledTime()),
            java.time.ZoneId.systemDefault());

        // 校验预约时间
        if (scheduledTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间不能早于当前时间");
        }

        // 创建订单
        SmsOrder order = new SmsOrder();
        order.setOrderNo(generateOrderNo());
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
        Long orderId = order.getId();

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

        // 保存手机号记录（多对多：每个手机号对应所有内容）
        List<String> phoneNumberList = formData.getPhoneNumberList();
        for (String phoneNumber : phoneNumberList) {
            for (Long contentId : contentIds) {
                SmsPhoneRecord record = new SmsPhoneRecord();
                record.setOrderNo(orderId);
                record.setContentId(contentId);
                record.setPhoneNumber(phoneNumber);
                record.setSendStatus(0); // 待发送
                smsPhoneRecordMapper.insert(record);
            }
        }

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
        contentWrapper.eq(SmsMessageContent::getOrderNo, id)
            .orderByAsc(SmsMessageContent::getContentSort);
        List<SmsMessageContent> contentList = smsMessageContentMapper.selectList(contentWrapper);
        List<String> messageContentList = contentList.stream()
            .map(SmsMessageContent::getContent)
            .toList();

        // 查询手机号列表
        LambdaQueryWrapper<SmsPhoneRecord> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(SmsPhoneRecord::getOrderNo, id)
            .orderByAsc(SmsPhoneRecord::getCreateTime);
        List<SmsPhoneRecord> phoneRecordList = smsPhoneRecordMapper.selectList(phoneWrapper);
        List<String> phoneNumberList = phoneRecordList.stream()
            .map(SmsPhoneRecord::getPhoneNumber)
            .toList();

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
        return this.updateById(order);
    }

    @Override
    public List<SmsOrder> getPendingOrdersToExecute() {
        // 查询状态为待发送且预约时间小于等于当前时间的订单
        LambdaQueryWrapper<SmsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsOrder::getStatus, OrderStatusEnum.PENDING.getValue())
            .le(SmsOrder::getScheduledTime, LocalDateTime.now())
            .orderByAsc(SmsOrder::getScheduledTime);
        return this.list(wrapper);
    }

    @Override
    public List<SmsPhoneRecord> getPhoneRecordsByOrderId(Long orderId) {
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderId)
            .orderByAsc(SmsPhoneRecord::getCreateTime);
        return smsPhoneRecordMapper.selectList(wrapper);
    }

    @Override
    public List<SmsMessageContent> getMessageContentsByOrderId(Long orderId) {
        LambdaQueryWrapper<SmsMessageContent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsMessageContent::getOrderNo, orderId)
            .orderByAsc(SmsMessageContent::getContentSort);
        return smsMessageContentMapper.selectList(wrapper);
    }

    /**
     * 生成订单编号
     */
    private String generateOrderNo() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "ORD" + timestamp + uuid;
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

}
