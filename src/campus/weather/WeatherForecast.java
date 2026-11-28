package campus.weather;

import campus.service.PublicJson;
import java.io.IOException;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Open-Meteo daily forecast, indexed by the local date at the campus. */
public final class WeatherForecast {
    private WeatherForecast() { }

    public record Day(int code, int high, int low) {
        Condition condition() {
            if (code == 0) return Condition.CLEAR;
            if (code <= 3) return Condition.CLOUD;
            if (code <= 48) return Condition.FOG;
            if (code >= 95) return Condition.THUNDER;
            if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Condition.SNOW;
            return Condition.RAIN;
        }

        public String description() {
            return condition().label
                    + " · 최고 " + high + "°C · 최저 " + low + "°C";
        }
    }

    enum Condition {
        CLEAR("맑음"), CLOUD("구름"), FOG("안개"), RAIN("비"), SNOW("눈"), THUNDER("뇌우");

        private final String label;

        Condition(String label) { this.label = label; }
    }

    public static Map<LocalDate, Day> fetch(double latitude, double longitude) throws IOException {
        URI uri = URI.create(String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&timezone=Asia%%2FSeoul&forecast_days=7"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min", latitude, longitude));
        return parse(PublicJson.fetch(uri));
    }

    static Map<LocalDate, Day> parse(String response) throws IOException {
        if (!(PublicJson.parse(response) instanceof Map<?, ?> root)
                || !(root.get("daily") instanceof Map<?, ?> daily)
                || !(daily.get("time") instanceof List<?> dates)
                || !(daily.get("weather_code") instanceof List<?> codes)
                || !(daily.get("temperature_2m_max") instanceof List<?> highs)
                || !(daily.get("temperature_2m_min") instanceof List<?> lows)
                || dates.size() != codes.size() || dates.size() != highs.size() || dates.size() != lows.size())
            throw new IOException("Invalid daily forecast");

        Map<LocalDate, Day> result = new HashMap<>();
        for (int i = 0; i < dates.size(); i++) {
            if (dates.get(i) instanceof String date && codes.get(i) instanceof Number code
                    && highs.get(i) instanceof Number high && lows.get(i) instanceof Number low
                    && Double.isFinite(high.doubleValue()) && Double.isFinite(low.doubleValue())
                    && high.doubleValue() >= low.doubleValue()
                    && code.doubleValue() == code.intValue() && code.intValue() >= 0 && code.intValue() <= 99) {
                try {
                    result.put(LocalDate.parse(date), new Day(code.intValue(),
                            (int) Math.round(high.doubleValue()), (int) Math.round(low.doubleValue())));
                } catch (DateTimeException ex) {
                    throw new IOException("Invalid forecast date", ex);
                }
            }
        }
        if (result.isEmpty()) throw new IOException("Empty daily forecast");
        return Map.copyOf(result);
    }
}
