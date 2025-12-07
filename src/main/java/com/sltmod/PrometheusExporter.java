package com.sltmod;

public class PrometheusExporter {

    private static boolean enabled = false;

    public static void initialize() {
        enabled = LayeredTerrainConfig.ENABLE_PROMETHEUS.get();

        if (enabled) {
            int port = LayeredTerrainConfig.PROMETHEUS_PORT.get();
            String endpoint = LayeredTerrainConfig.PROMETHEUS_ENDPOINT.get();

            // Stub - would start HTTP server
            LayeredTerrainMod.LOGGER.info(
                "Prometheus metrics would be available at http://localhost:{}{}",
                port, endpoint
            );
            LayeredTerrainMod.LOGGER.warn(
                "Prometheus export is a stub in this version"
            );
        }
    }

    /**
     * Export metrics in Prometheus format
     */
    public static String exportMetrics() {
        if (!enabled) return "";

        StringBuilder sb = new StringBuilder();

        // Example Prometheus format
        sb.append("# HELP layered_terrain_chunks_processed_total Total chunks processed\n");
        sb.append("# TYPE layered_terrain_chunks_processed_total counter\n");
        sb.append("layered_terrain_chunks_processed_total ").append(Metrics.chunksProcessed.get()).append("\n");

        sb.append("# HELP layered_terrain_avg_processing_time_ms Average processing time\n");
        sb.append("# TYPE layered_terrain_avg_processing_time_ms gauge\n");
        sb.append("layered_terrain_avg_processing_time_ms ").append(Metrics.getAverageProcessingTime()).append("\n");

        return sb.toString();
    }

    public static void shutdown() {
        if (enabled) {
            LayeredTerrainMod.LOGGER.info("Prometheus exporter shutdown");
        }
    }
}
