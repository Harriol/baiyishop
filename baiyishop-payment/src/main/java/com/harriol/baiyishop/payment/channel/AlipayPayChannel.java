package com.harriol.baiyishop.payment.channel;

import com.harriol.baiyishop.payment.config.PaymentProperties;
import com.harriol.baiyishop.payment.entity.Payment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝支付（模拟实现）。
 * <p>支付参数按支付宝「统一收单下单」的形态设计：商户订单号 + 待签串 + 签名。
 */
@Component
public class AlipayPayChannel extends AbstractMockPaymentChannel {

    public static final String CODE = "ALIPAY";

    public AlipayPayChannel(PaymentProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> payParams(Payment payment) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("outTradeNo", payment.getPaymentNo());
        params.put("totalAmount", payment.getAmount());
        params.put("subject", "百益商城订单 " + payment.getOrderNo());
        params.put("signType", "RSA2");
        params.put("orderStr", sign(payment.getPaymentNo() + ":" + payment.getAmount()));
        params.put("sign", sign(payment.getPaymentNo() + ":" + payment.getAmount()));
        return params;
    }
}
