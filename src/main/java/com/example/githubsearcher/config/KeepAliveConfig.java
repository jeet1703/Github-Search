package com.example.githubsearcher.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/**
 * Keeps a free-tier instance (e.g. Render, which sleeps after ~15 minutes without traffic) awake
 * by calling its own public health endpoint on a schedule.
 * <p>
 * Active only when {@code keep-alive.url} resolves to a non-blank value: {@code KEEP_ALIVE_URL},
 * or {@code RENDER_EXTERNAL_URL}, which Render sets automatically. Off locally and in tests.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression("!'${keep-alive.url:}'.isBlank()")
public class KeepAliveConfig {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveConfig.class);

    private final RestClient restClient;
    private final String healthUrl;

    public KeepAliveConfig(@Value("${keep-alive.url}") String baseUrl) {
        this.restClient = RestClient.create();
        this.healthUrl = baseUrl.replaceAll("/+$", "") + "/actuator/health";
        log.info("Keep-alive enabled, pinging {}", healthUrl);
    }

    @Scheduled(initialDelayString = "${keep-alive.interval:PT10M}", fixedDelayString = "${keep-alive.interval:PT10M}")
    void ping() {
        try {
            restClient.get().uri(healthUrl).retrieve().toBodilessEntity();
            log.debug("Keep-alive ping OK");
        } catch (Exception e) {
            log.warn("Keep-alive ping to {} failed: {}", healthUrl, e.getMessage());
        }
    }
}
