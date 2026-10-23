package com.signasource.signa_api.organizations.service;

final class MetricMath {

    private MetricMath() {}

    static int percentage(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    static double average(long sum, long count) {
        return count == 0 ? 0 : Math.round(sum * 10.0 / count) / 10.0;
    }
}
