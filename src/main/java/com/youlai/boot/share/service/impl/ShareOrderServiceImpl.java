package com.youlai.boot.share.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.common.exception.BusinessException;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.share.enums.ShareOrderStatusEnum;
import com.youlai.boot.share.mapper.ShareContentMapper;
import com.youlai.boot.share.mapper.ShareOrderMapper;
import com.youlai.boot.share.mapper.SharePhoneRecordMapper;
import com.youlai.boot.share.model.entity.ShareContent;
import com.youlai.boot.share.model.entity.ShareOrder;
import com.youlai.boot.share.model.entity.SharePhoneRecord;
import com.youlai.boot.share.model.form.ShareOrderForm;
import com.youlai.boot.share.model.query.ShareOrderQuery;
import com.youlai.boot.share.model.vo.ShareOrderDetailVO;
import com.youlai.boot.share.model.vo.ShareOrderPageVO;
import com.youlai.boot.share.service.ShareOrderService;
import com.youlai.boot.system.mapper.CountryMapper;
import com.youlai.boot.system.model.entity.Country;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 社群订单业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Service
@RequiredArgsConstructor
public class ShareOrderServiceImpl extends ServiceImpl<ShareOrderMapper, ShareOrder> implements ShareOrderService {

    private final ShareOrderMapper shareOrderMapper;
    private final CountryMapper countryMapper;
    private final ShareContentMapper shareContentMapper;
    private final SharePhoneRecordMapper sharePhoneRecordMapper;

    @Override
    public Page<ShareOrderPageVO> getShareOrderPage(ShareOrderQuery queryParams) {
        // 数据权限：非管理员只能查看自己的订单
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<ShareOrderPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return shareOrderMapper.getShareOrderPage(page, queryParams, currentUserId, isRoot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(ShareOrderForm formData) {
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
        String orderNo = generateOrderNo();
        ShareOrder order = new ShareOrder();
        order.setOrderNo(orderNo);
        order.setCountryId(formData.getCountryId());
        order.setCountryName(country.getName());
        order.setHasAreaCode(formData.getHasAreaCode());
        order.setScheduledTime(scheduledTime);
        order.setStatus(ShareOrderStatusEnum.PENDING.getValue());
        order.setSuccessCount(0);
        order.setFailCount(0);
        order.setTotalCount(formData.getPhoneNumberList().size());
        order.setReportCount(0);
        order.setPaidCount(0);
        order.setCancelCount(0);
        order.setRemark(formData.getRemark());

        this.save(order);

        // 保存短信内容
        List<String> messageContentList = formData.getMessageContentList();
        List<Long> contentIds = new java.util.ArrayList<>();
        for (int i = 0; i < messageContentList.size(); i++) {
            ShareContent content = new ShareContent();
            content.setOrderNo(orderNo);
            content.setContent(messageContentList.get(i));
            content.setContentSort(i);
            shareContentMapper.insert(content);
            contentIds.add(content.getContentId());
        }

        // 保存手机号记录（多对多：每个手机号对应所有内容）
        List<String> phoneNumberList = formData.getPhoneNumberList();
        for (String phoneNumber : phoneNumberList) {
            for (Long contentId : contentIds) {
                SharePhoneRecord record = new SharePhoneRecord();
                record.setOrderNo(orderNo);
                record.setContentId(contentId);
                record.setPhoneNumber(phoneNumber);
                record.setSendStatus(0); // 待发送
                sharePhoneRecordMapper.insert(record);
            }
        }

        return orderNo;
    }

    @Override
    public ShareOrderDetailVO getOrderDetail(Long id) {
        ShareOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 查询短信内容列表
        LambdaQueryWrapper<ShareContent> contentWrapper = new LambdaQueryWrapper<>();
        contentWrapper.eq(ShareContent::getOrderNo, id)
            .orderByAsc(ShareContent::getContentSort);
        List<ShareContent> contentList = shareContentMapper.selectList(contentWrapper);
        List<String> messageContentList = contentList.stream()
            .map(ShareContent::getContent)
            .toList();

        // 查询手机号列表
        LambdaQueryWrapper<SharePhoneRecord> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(SharePhoneRecord::getOrderNo, id)
            .orderByAsc(SharePhoneRecord::getCreateTime);
        List<SharePhoneRecord> phoneRecordList = sharePhoneRecordMapper.selectList(phoneWrapper);
        List<String> phoneNumberList = phoneRecordList.stream()
            .map(SharePhoneRecord::getPhoneNumber)
            .toList();

        ShareOrderDetailVO detailVO = new ShareOrderDetailVO();
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
        detailVO.setReportCount(order.getReportCount());
        detailVO.setPaidCount(order.getPaidCount());
        detailVO.setCancelCount(order.getCancelCount());
        detailVO.setRemark(order.getRemark());
        detailVO.setCreateTime(order.getCreateTime());

        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long id) {
        ShareOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!ShareOrderStatusEnum.PENDING.getValue().equals(order.getStatus())) {
            throw new BusinessException("只有待发送的订单才能取消");
        }

        order.setStatus(ShareOrderStatusEnum.CANCELLED.getValue());
        return this.updateById(order);
    }

    /**
     * 生成订单编号
     */
    private String generateOrderNo() {
        String date = DateUtil.format(new Date(), "yyyyMMdd");
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "SHR" + date + uuid;
    }

    /**
     * 获取状态描述
     */
    private String getStatusDesc(Integer status) {
        ShareOrderStatusEnum statusEnum = ShareOrderStatusEnum.getByValue(status);
        return statusEnum != null ? statusEnum.getDesc() : "未知";
    }

}
