package com.harriol.baiyishop.payment.channel;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.payment.config.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 模拟渠道的公共部分：HMAC-SHA256 签名与验签。
 * <p>真实渠道的验签是「渠道公钥验 RSA 签名」或「平台私钥签名 + 渠道验签」，
 * 这里的**位置**与真实一致（回调入口先验签，之后再谈幂等与业务），只是算法换成对称的 HMAC。
 * <p>密钥从不出现在日志里；验签用 {@link MessageDigest#isEqual} 做常量时间比较，避免时序侧信道。
 */
public abstract class AbstractMockPaymentChannel implements PaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(AbstractMockPaymentChannel.class);

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PaymentProperties properties;
    protected final ObjectMapper objectMapper;

    protected AbstractMockPaymentChannel(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 模拟渠道的签名算法：hex(HMAC-SHA256(rawBody, secret)) */
    String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.mockSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("模拟渠道签名失败", ex);
        }
    }

    @Override
    public ChannelCallback verify(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("渠道回调缺少签名 channel={}", code());
            throw new BizException(ErrorCode.PAYMENT_SIGN_INVALID);
        }
        String expected = sign(rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            log.warn("渠道回调验签失败 channel={}", code());
            throw new BizException(ErrorCode.PAYMENT_SIGN_INVALID);
        }
        try {
            return objectMapper.readValue(rawBody, ChannelCallback.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调报文格式不正确");
        }
    }

    /** 供模拟渠道的「一键支付」入口构造合法回调 */
    public String signForMock(String rawBody) {
        return sign(rawBody);
    }
}
