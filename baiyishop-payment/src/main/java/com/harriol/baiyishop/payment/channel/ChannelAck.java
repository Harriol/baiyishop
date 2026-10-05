package com.harriol.baiyishop.payment.channel;

/**
 * 回调应答（渠道要求的确认报文）。
 * <p>**重复回调也要回 SUCCESS**：否则渠道会一直重试。
 */
public record ChannelAck(String code, String message) {

    public static ChannelAck ok() {
        return new ChannelAck("SUCCESS", "OK");
    }

    public static ChannelAck duplicate() {
        return new ChannelAck("SUCCESS", "DUPLICATE_IGNORED");
    }
}
