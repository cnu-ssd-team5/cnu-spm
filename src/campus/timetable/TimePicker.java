package campus.timetable;
import campus.ui.Theme;


import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

/** Separate hour/minute choices; LocalTime.MAX represents the 24:00 end boundary. */
public final class TimePicker extends JPanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final JComboBox<Integer> hour = new JComboBox<>();
    private final JComboBox<Integer> minute = new JComboBox<>();
    private final boolean endOfDayAllowed;
    private boolean adjusting;
    private LocalTime preserved;

    public TimePicker(boolean endOfDayAllowed) {
        super(new GridLayout(1, 2, 6, 0));
        this.endOfDayAllowed = endOfDayAllowed;
        setOpaque(false);
        for (int value = 8; value <= (endOfDayAllowed ? 24 : 23); value++) hour.addItem(value);
        for (int value = 0; value < 60; value += 5) minute.addItem(value);
        for (JComboBox<Integer> choices : java.util.List.of(hour, minute)) {
            choices.setMaximumRowCount(12);
            choices.setFont(Theme.font(13, Font.PLAIN));
            choices.setBackground(Color.WHITE);
            choices.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean selected, boolean focus) {
                    return super.getListCellRendererComponent(list,
                            value instanceof Integer number ? String.format("%02d", number) : "",
                            index, selected, focus);
                }
            });
        }
        hour.getAccessibleContext().setAccessibleName("시");
        minute.getAccessibleContext().setAccessibleName("분");
        hour.setToolTipText("시 (08~24)");
        minute.setToolTipText("분 (5분 단위)");
        JPanel hours = Theme.transparent(new BorderLayout(3, 0));
        hours.add(hour, BorderLayout.CENTER);
        hours.add(Theme.label("시", 12, Theme.MUTED), BorderLayout.EAST);
        JPanel minutes = Theme.transparent(new BorderLayout(3, 0));
        minutes.add(minute, BorderLayout.CENTER);
        minutes.add(Theme.label("분", 12, Theme.MUTED), BorderLayout.EAST);
        add(hours);
        add(minutes);
        hour.addActionListener(e -> {
            if (adjusting) return;
            preserved = null;
            if ((Integer) hour.getSelectedItem() == 24) minute.setSelectedItem(0);
            minute.setEnabled((Integer) hour.getSelectedItem() != 24);
        });
        minute.addActionListener(e -> { if (!adjusting) preserved = null; });
    }

    public LocalTime time() {
        if (preserved != null) return preserved;
        int hours = (Integer) hour.getSelectedItem();
        return hours == 24 ? LocalTime.MAX : LocalTime.of(hours, (Integer) minute.getSelectedItem());
    }

    public void setTime(LocalTime time) {
        if (time.equals(LocalTime.MAX) && !endOfDayAllowed) {
            throw new IllegalArgumentException("시작 시각으로 24:00을 선택할 수 없습니다.");
        }
        adjusting = true;
        try {
            int hours = time.equals(LocalTime.MAX) ? 24 : time.getHour();
            int minutes = time.equals(LocalTime.MAX) ? 0 : time.getMinute();
            retainValue(hour, hours);
            retainValue(minute, minutes);
            minute.setEnabled(hours != 24);
            // Keep an older/imported off-grid time intact until the user changes it.
            preserved = time;
        } finally {
            adjusting = false;
        }
    }

    private static void retainValue(JComboBox<Integer> choices, int value) {
        int index = 0;
        while (index < choices.getItemCount() && choices.getItemAt(index) < value) index++;
        if (index == choices.getItemCount() || choices.getItemAt(index) != value) choices.insertItemAt(value, index);
        choices.setSelectedItem(value);
    }

    public static String display(LocalTime time) {
        return time.equals(LocalTime.MAX) ? "24:00" : time.format(CLOCK);
    }
}
