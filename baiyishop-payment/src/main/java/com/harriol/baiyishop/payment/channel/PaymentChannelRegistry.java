package com.harriol.baiyishop.payment.channel;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道注册表：把 {@code channel} 字符串映射到策略实现。
 * <p>新增渠道 = 新增一个 {@link PaymentChannel} 实现类，注册表与业务代码都不用改（REQ-801）。
 */
@Component
public class PaymentChannelRegistry {

    private final Map<String, PaymentChannel> channels = new HashMap<>();

    public PaymentChannelRegistry(List<PaymentChannel> channelList) {
        channelList.forEach(channel -> channels.put(channel.code().toUpperCase(), channel));
    }

    public PaymentChannel of(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择支付方式");
        }
        PaymentChannel channel = channels.get(code.toUpperCase());
        if (channel == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的支付渠道：" + code);
        }
        return channel;
    }

    /** 支持「一键支付」的模拟渠道（用于本地联调入口） */
    public AbstractMockPaymentChannel mockOf(String code) {
        PaymentChannel channel = of(code);
        if (!(channel instanceof AbstractMockPaymentChannel mock)) {
            throw new BizException(ErrorCode.PAYMENT_NOT_ALLOWED, "该渠道不支持模拟支付");
        }
        return mock;
    }
}
