package com.harriol.baiyishop.payment.channel;

/**
 * 渠道回调报文（模拟渠道与真实渠道同构）。
 * <p>真实渠道的字段名各不相同，由各自的 {@link PaymentChannel} 实现负责解析到本结构。
 */
public record ChannelCallback(String paymentNo, String channelTradeNo, Long amount, String status) {

    public boolean success() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
