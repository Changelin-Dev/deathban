package com.jqshuv.deathban.utils;

public class TimeUtils {

    /**
     * Formate une durée en millisecondes en texte lisible ("1j 2h 15m 30s").
     * Les unités nulles sont omises, sauf s'il ne reste que des secondes.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";

        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("j ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sb.length() == 0 || seconds > 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
