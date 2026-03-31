package com.nexusiam.infrastructure.config.oauth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class OktaRequestInterceptor implements RequestInterceptor {

    private static final ThreadLocal<Integer> retryCountHolder = new ThreadLocal<>();

    @Override
    public void apply(RequestTemplate template) {

        if (!template.headers().containsKey("X-Request-Id")) {
            String requestId = UUID.randomUUID().toString();
            template.header("X-Request-Id", requestId);
            log.debug("Added X-Request-Id: {}", requestId);
        }

        Integer retryCount = retryCountHolder.get();
        if (retryCount == null) {
            retryCount = 0;
        }
        template.header("X-Retry-Count", String.valueOf(retryCount));
        log.debug("Added X-Retry-Count: {}", retryCount);

        template.header("X-Request-Timestamp", String.valueOf(System.currentTimeMillis()));

        template.header("X-Client-App", "EXCHANGE-USERMANAGEMENT");
    }

    public static void setRetryCount(int count) {
        retryCountHolder.set(count);
    }

    public static void clearRetryCount() {
        retryCountHolder.remove();
    }
}
