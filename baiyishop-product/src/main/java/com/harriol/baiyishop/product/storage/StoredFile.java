package com.harriol.baiyishop.product.storage;

/**
 * 上传结果：可直接入库的图片地址 + 对象信息。
 *
 * @param url         对外可访问地址（写入 main_image / image_url 等字段）
 * @param objectName  对象名（桶内路径，排查用）
 * @param size        字节数
 * @param contentType 内容类型
 */
public record StoredFile(String url, String objectName, long size, String contentType) {
}
