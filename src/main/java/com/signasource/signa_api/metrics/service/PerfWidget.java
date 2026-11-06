package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import java.util.Locale;

/**
 * Fixed catalog of performance widgets the dashboard may request. The client never sends PromQL; it
 * names a widget and the backend owns the query, which prevents query injection and keeps the
 * Grafana Cloud read cost predictable. {@code %1$s} is replaced with the rate window.
 */
public enum PerfWidget {
    BACKEND_UP("up{job=\"signa-api\"}"),
    R2_UP("signa_r2_up"),

    THROUGHPUT("sum(rate(http_server_requests_seconds_count[%1$s]))"),
    ERROR_RATE(
            "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[%1$s]))"
                    + " / clamp_min(sum(rate(http_server_requests_seconds_count[%1$s])), 1)"),
    ERROR_RATE_4XX(
            "sum(rate(http_server_requests_seconds_count{status=~\"4..\"}[%1$s]))"
                    + " / clamp_min(sum(rate(http_server_requests_seconds_count[%1$s])), 1)"),
    ERRORS_TOTAL("sum by (status) (rate(signa_http_errors_total[%1$s]))"),

    EMAIL_FAILURES("sum(rate(signa_email_failures_total[%1$s]))"),
    PUSH_FAILURES("sum(rate(signa_push_failures_total[%1$s]))"),
    CLIENT_ERRORS("sum by (type) (rate(signa_client_errors_total[%1$s]))"),

    LATENCY_P50(
            "histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket[%1$s])))"),
    LATENCY_P95(
            "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[%1$s])))"),
    LATENCY_P99(
            "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket[%1$s])))"),
    SLOWEST_OPS(
            "topk(10, histogram_quantile(0.95, sum by (le, uri)"
                    + " (rate(http_server_requests_seconds_bucket[%1$s]))))"),

    CPU_HOST("100 * (1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[%1$s])))"),
    MEM_HOST("100 * (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes))"),
    CPU_CONTAINERS("sum by (name) (rate(container_cpu_usage_seconds_total{name!=\"\"}[%1$s]))"),
    MEM_CONTAINERS("sum by (name) (container_memory_usage_bytes{name!=\"\"})"),

    DB_CONNECTIONS_ACTIVE("hikaricp_connections_active"),
    DB_CONNECTIONS_PENDING("hikaricp_connections_pending"),
    PG_CONNECTIONS("sum(pg_stat_database_numbackends)"),

    JVM_HEAP("sum(jvm_memory_used_bytes{area=\"heap\"})"),
    JVM_GC_PAUSE(
            "sum(rate(jvm_gc_pause_seconds_sum[%1$s]))"
                    + " / clamp_min(sum(rate(jvm_gc_pause_seconds_count[%1$s])), 1)"),

    R2_PRESIGN_LATENCY(
            "histogram_quantile(0.95, sum by (le)"
                    + " (rate(signa_r2_presign_duration_seconds_bucket[%1$s])))"),
    ANIMATION_LATENCY(
            "histogram_quantile(0.95, sum by (le, platform)"
                    + " (rate(signa_client_animation_load_seconds_bucket[%1$s])))"),
    WEB_VITALS(
            "histogram_quantile(0.75, sum by (le, name)"
                    + " (rate(signa_client_web_vital_seconds_bucket[%1$s])))"),
    SCREEN_LOAD(
            "histogram_quantile(0.75, sum by (le, name)"
                    + " (rate(signa_client_screen_load_seconds_bucket[%1$s])))"),
    ML_INFERENCE_LATENCY(
            "histogram_quantile(0.95, sum by (le)"
                    + " (rate(signa_client_inference_seconds_bucket[%1$s])))"),
    // Inference throughput (recognitions per second). On-device, so this is "consumo de IA" in
    // volume terms — there is no server-side inference cost to estimate.
    ML_INFERENCE_RATE("sum(rate(signa_client_inference_seconds_count[%1$s]))"),
    ML_CONFIDENCE(
            "sum(rate(signa_client_recognition_confidence_sum[%1$s]))"
                    + " / clamp_min(sum(rate(signa_client_recognition_confidence_count[%1$s])), 1)"),
    ML_FPS(
            "sum(rate(signa_client_recognition_fps_sum[%1$s]))"
                    + " / clamp_min(sum(rate(signa_client_recognition_fps_count[%1$s])), 1)");

    private final String template;

    PerfWidget(String template) {
        this.template = template;
    }

    public String promQl(String rateWindow) {
        return String.format(template, rateWindow);
    }

    /**
     * Resolves a dashboard widget key (e.g. {@code latency_p95}) to a catalog entry, 404 if
     * unknown.
     */
    public static PerfWidget fromKey(String key) {
        if (key == null) {
            throw new NotFoundException("Unknown performance widget: null");
        }
        try {
            return PerfWidget.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Unknown performance widget: " + key);
        }
    }
}
