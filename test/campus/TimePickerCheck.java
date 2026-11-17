package campus;

import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/** Checks independent hour/minute selection, day boundaries, and lossless older values. */
public final class TimePickerCheck {
    private static int checks;
    private TimePickerCheck() { }

    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                TimePicker start = new TimePicker(false);
                TimePicker end = new TimePicker(true);
                JComboBox<Integer> startHours = choices(start, "hour");
                JComboBox<Integer> endHours = choices(end, "hour");
                JComboBox<Integer> startMinutes = choices(start, "minute");
                JComboBox<Integer> endMinutes = choices(end, "minute");
                check(!startHours.isEditable() && !startMinutes.isEditable(), "times use two list selections");
                check(startHours.getItemCount() == 16 && endHours.getItemCount() == 17, "new hours start at08 and only end offers24");
                for (int index = 0; index < 16; index++) {
                    check(startHours.getItemAt(index) == index + 8 && endHours.getItemAt(index) == index + 8,
                            "ordered independent hour choice" + index);
                }
                check(startMinutes.getItemCount() == 12 && endMinutes.getItemCount() == 12, "independent minutes have12 choices");
                for (int index = 0; index < 12; index++) {
                    check(startMinutes.getItemAt(index) == index * 5 && endMinutes.getItemAt(index) == index * 5,
                            "ordered five-minute choice" + index);
                }
                check(endHours.getItemAt(16) == 24, "end hour includes24");
                end.setTime(LocalTime.of(23, 55));
                endHours.setSelectedItem(24);
                check(end.time().equals(LocalTime.MAX) && !endMinutes.isEnabled()
                        && endMinutes.getSelectedItem().equals(0), "24 hour forces00 minutes and represents end-of-day");
                endHours.setSelectedItem(23);
                endMinutes.setSelectedItem(30);
                check(endMinutes.isEnabled() && end.time().equals(LocalTime.of(23, 30)), "leaving24 re-enables independent minute selection");
                end.setTime(LocalTime.MAX);
                check(end.time().equals(LocalTime.MAX), "saved24:00 retained");
                start.setTime(LocalTime.of(8, 5));
                check(start.time().equals(LocalTime.of(8, 5)) && startHours.getSelectedItem().equals(8)
                        && startMinutes.getSelectedItem().equals(5), "normal time selects both parts");
                LocalTime legacy = LocalTime.of(7, 2, 11, 9);
                start.setTime(legacy);
                check(start.time().equals(legacy), "existing pre08/off-grid/seconds precision preserved on edit");
                int hourCount = startHours.getItemCount();
                int minuteCount = startMinutes.getItemCount();
                start.setTime(legacy);
                check(startHours.getItemCount() == hourCount && startMinutes.getItemCount() == minuteCount,
                        "reselecting legacy time does not duplicate choices");
                startHours.setSelectedItem(9);
                startMinutes.setSelectedItem(10);
                check(start.time().equals(LocalTime.of(9, 10)), "editing a time clears preserved legacy seconds");
                check(TimePicker.display(legacy).equals("07:02") && TimePicker.display(LocalTime.MAX).equals("24:00"),
                        "ordinary and end-of-day captions");
                try {
                    start.setTime(LocalTime.MAX);
                    throw new AssertionError("start24:00 accepted");
                } catch (IllegalArgumentException expected) { checks++; }
                check(ScheduleSlot.before(LocalTime.of(13, 2)).equals(
                        new ScheduleSlot(LocalTime.of(12, 0), LocalTime.of(13, 0))), "before slot rounds boundary down");
                check(ScheduleSlot.after(LocalTime.of(13, 2)).equals(
                        new ScheduleSlot(LocalTime.of(13, 5), LocalTime.of(14, 5))), "after slot rounds boundary up");
                check(ScheduleSlot.after(LocalTime.of(13, 0, 1)).start().equals(LocalTime.of(13, 5)),
                        "seconds cannot place suggestion before actual boundary");
                check(ScheduleSlot.between(LocalTime.of(13, 2), LocalTime.of(14, 7)).equals(
                        new ScheduleSlot(LocalTime.of(13, 5), LocalTime.of(14, 5))), "between slot fits inside gap");
                check(ScheduleSlot.between(LocalTime.of(13, 2), LocalTime.of(13, 7)) == null,
                        "gap with no full five-minute interval has no suggestion");
                check(ScheduleSlot.after(LocalTime.of(23, 55)).equals(
                        new ScheduleSlot(LocalTime.of(23, 55), LocalTime.MAX)), "last five minutes end at24:00");
                check(ScheduleSlot.after(LocalTime.of(23, 56)) == null
                        && ScheduleSlot.after(LocalTime.MAX) == null, "no start at or beyond24:00");
                check(ScheduleSlot.before(LocalTime.MAX).equals(
                        new ScheduleSlot(LocalTime.of(23, 0), LocalTime.MAX)), "before24:00 keeps whole final hour");
                check(ScheduleSlot.before(LocalTime.of(8, 0)) == null, "no new suggested time before08");
                check(ScheduleSlot.after(LocalTime.of(7, 30)).equals(new ScheduleSlot(LocalTime.of(8, 0), LocalTime.of(9, 0))),
                        "after old early schedule proposes new time from08");
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) throw new AssertionError("Time picker check failed", failure.get());
        System.out.println("PASS " + checks + " time picker checks");
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Integer> choices(TimePicker picker, String name) throws Exception {
        Field field = TimePicker.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JComboBox<Integer>) field.get(picker);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}