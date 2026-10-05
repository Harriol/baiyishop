package com.harriol.baiyishop.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

/**
 * 时间入参的宽松解析（docs/api.md 1.8）。
 * <p>Jackson 默认只认 ISO-8601 的 {@code 2026-10-05T10:00:00}，而接口文档给出的是
 * {@code 2026-10-05 10:00:00}（空格分隔）。前端按文档写就会被 400 挡下 —— 这种失败很难查，
 * 因此在公共层统一放宽：**两种写法都收，输出仍用 ISO**（解析点收敛一处，业务代码不用各自处理）。
 */
@AutoConfiguration
@ConditionalOnClass(JsonMapper.class)
public class JacksonTimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lenientLocalDateTimeCustomizer")
    public JsonMapperBuilderCustomizer lenientLocalDateTimeCustomizer() {
        return builder -> builder.addModule(lenientDateTimeModule());
    }

    private SimpleModule lenientDateTimeModule() {
        SimpleModule module = new SimpleModule("baiyishop-lenient-datetime");
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer() {
            @Override
            public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
                    throws JacksonException {
                if (parser.currentToken() != JsonToken.VALUE_STRING) {
                    // 非字符串（数字时间戳等）交回默认实现，保持原有语义
                    return super.deserialize(parser, context);
                }
                String text = parser.getString();
                if (text == null || text.isBlank()) {
                    return null;
                }
                return LocalDateTime.parse(text.trim().replace(' ', 'T'));
            }
        });
        return module;
    }
}
