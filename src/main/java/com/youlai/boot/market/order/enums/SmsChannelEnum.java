package com.youlai.boot.market.order.enums;

import com.youlai.boot.common.base.IBaseEnum;
import lombok.Getter;

/**
 * 短信渠道枚举
 *
 * @author Ray.Hao
 * @since 2026/04/09
 */
@Getter
public enum SmsChannelEnum implements IBaseEnum<String> {

    ONBUKA("ONBUKA", "Onbuka短信"),
    ALIYUN("ALIYUN", "阿里云短信"),
    TENCENT("TENCENT", "腾讯云短信");

    private final String value;
    private final String label;

    SmsChannelEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
