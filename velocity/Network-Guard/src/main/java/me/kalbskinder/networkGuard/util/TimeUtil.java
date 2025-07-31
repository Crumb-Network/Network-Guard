package me.kalbskinder.networkGuard.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
    // Turn a time string (12d 18m 10s) into a number
    public static long parseDuration(String input) {
        Pattern pattern = Pattern.compile("(\\d+)([smhd])");
        Matcher matcher = pattern.matcher(input);
        long millis = 0;

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            switch (matcher.group(2)) {
                case "s" -> millis += value * 1000L;
                case "m" -> millis += value * 60 * 1000L;
                case "h" -> millis += value * 60 * 60 * 1000L;
                case "d" -> millis += value * 24 * 60 * 60 * 1000L;
            }
        }
        return millis;
    }

    // Turn a number back into a string format
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;

        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString();
    }
}
