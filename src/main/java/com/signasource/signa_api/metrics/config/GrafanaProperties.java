package com.signasource.signa_api.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Read access to Grafana Cloud's Prometheus HTTP API, used to proxy the performance panels. The
 * token must be a Grafana Cloud access policy with {@code metrics:read} scope — distinct from the
 * remote-write key Alloy already uses. When unset the perf endpoints degrade gracefully (empty
 * series) so the business dashboard keeps working without it.
 *
 * @param queryUrl Prometheus base, e.g. {@code
 *     https://prometheus-prod-40-prod-sa-east-1.grafana.net/api/prom}
 * @param user Prometheus query instance id (basic-auth username)
 * @param token access policy token (basic-auth password)
 */
@ConfigurationProperties(prefix = "grafana-cloud")
public record GrafanaProperties(String queryUrl, String user, String token) {

    public boolean configured() {
        return isSet(queryUrl) && isSet(user) && isSet(token);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
