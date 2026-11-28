package campus.weather;

import java.awt.*;
import javax.swing.Icon;

/** A font-independent weather symbol and a compact high/low temperature row. */
public final class WeatherBadge implements Icon {
    private static final int SIZE = 16;
    private static final Color HIGH = new Color(0xA62928);
    private static final Color LOW = new Color(0x175A9C);
    private static final Color SEPARATOR = new Color(0x526176);

    private final WeatherForecast.Day forecast;
    private final FontMetrics metrics;
    private final String high;
    private final String low;

    public WeatherBadge(WeatherForecast.Day forecast, FontMetrics metrics) {
        this.forecast = forecast;
        this.metrics = metrics;
        high = forecast.high() + "°";
        low = forecast.low() + "°";
    }

    @Override public int getIconWidth() {
        return SIZE + 4 + metrics.stringWidth(high + "/" + low);
    }

    @Override public int getIconHeight() { return Math.max(SIZE, metrics.getHeight()); }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.translate(x, y + (getIconHeight() - SIZE) / 2);
            paintWeather(g);
            g.translate(0, -(getIconHeight() - SIZE) / 2);
            g.setFont(metrics.getFont());
            int baseline = (getIconHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            int textX = SIZE + 4;
            g.setColor(HIGH);
            g.drawString(high, textX, baseline);
            textX += metrics.stringWidth(high);
            g.setColor(SEPARATOR);
            g.drawString("/", textX, baseline);
            g.setColor(LOW);
            g.drawString(low, textX + metrics.charWidth('/'), baseline);
        } finally {
            g.dispose();
        }
    }

    private void paintWeather(Graphics2D g) {
        switch (forecast.condition()) {
            case CLEAR -> {
                g.setColor(new Color(0xA76000));
                g.fillOval(4, 4, 8, 8);
                for (int i = 0; i < 8; i++) {
                    g.drawLine(8, 1, 8, 2);
                    g.rotate(Math.PI / 4, 8, 8);
                }
            }
            case CLOUD -> cloud(g, new Color(0x52637A));
            case FOG -> {
                g.setColor(new Color(0x58686D));
                g.drawLine(3, 5, 13, 5);
                g.drawLine(1, 8, 11, 8);
                g.drawLine(4, 11, 14, 11);
            }
            case RAIN -> {
                cloud(g, new Color(0x24588B));
                g.drawLine(5, 12, 4, 15);
                g.drawLine(10, 12, 9, 15);
            }
            case SNOW -> {
                g.setColor(new Color(0x087280));
                for (int i = 0; i < 3; i++) {
                    g.drawLine(8, 2, 8, 14);
                    g.drawLine(6, 4, 8, 6);
                    g.drawLine(10, 4, 8, 6);
                    g.drawLine(6, 12, 8, 10);
                    g.drawLine(10, 12, 8, 10);
                    g.rotate(Math.PI / 3, 8, 8);
                }
            }
            case THUNDER -> {
                cloud(g, new Color(0x623D88));
                g.setColor(new Color(0xA76000));
                g.fillPolygon(new int[] {8, 5, 8, 7, 12, 9}, new int[] {7, 11, 11, 16, 10, 10}, 6);
            }
        }
    }

    private static void cloud(Graphics2D g, Color color) {
        g.setColor(color);
        g.fillOval(1, 5, 9, 6);
        g.fillOval(5, 2, 8, 9);
        g.fillOval(10, 5, 6, 6);
    }
}
