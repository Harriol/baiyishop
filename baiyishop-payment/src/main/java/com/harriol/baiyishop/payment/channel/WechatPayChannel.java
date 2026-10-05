package com.harriol.baiyishop.payment.channel;

import com.harriol.baiyishop.payment.config.PaymentProperties;
import com.harriol.baiyishop.payment.entity.Payment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付（模拟实现）。
 * <p>支付参数按微信小程序 `wx.requestPayment` 的字段设计：prepayId / nonceStr / timeStamp /
 * signType / paySign —— 前端唤起逻辑与接真渠道时一致，替换实现即可。
 */
@Component
public class WechatPayChannel extends AbstractMockPaymentChannel {

    public static final String CODE = "WECHAT";

    public WechatPayChannel(PaymentProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> payParams(Payment payment) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("prepayId", "mock_prepay_" + payment.getPaymentNo());
        params.put("nonceStr", UUID.randomUUID().toString().replace("-", ""));
        params.put("timeStamp", String.valueOf(Instant.now().getEpochSecond()));
        params.put("signType", "RSA");
        params.put("paySign", sign(payment.getPaymentNo() + ":" + payment.getAmount()));
        return params;
    }
}
