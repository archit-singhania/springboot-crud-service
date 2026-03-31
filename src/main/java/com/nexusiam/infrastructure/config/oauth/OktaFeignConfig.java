package com.nexusiam.infrastructure.config.oauth;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class OktaFeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Request.Options requestOptions() {

        return new Request.Options(
                30, TimeUnit.SECONDS,
                30, TimeUnit.SECONDS,
                true
        );
    }

    @Bean
    public Retryer retryer() {

        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new OktaErrorDecoder();
    }

    @Bean
    public OktaRequestInterceptor oktaRequestInterceptor() {
        return new OktaRequestInterceptor();
    }

    @Bean
    public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        return new SpringFormEncoder(new SpringEncoder(messageConverters));
    }
}
