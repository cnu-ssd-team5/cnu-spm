package campus.timetable;

import campus.map.CampusMap;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.ui.Theme;
import java.awt.*;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** 예전 일회성 편집창의 저장 회귀 검사를 공통 편집창에서도 유지한다. 네이티브 입력은 사용하지 않는다. */
public final class TimetableEditorTest {
    private static <T> List<T> components(Container parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container container) result.addAll(components(container, type));
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static JDialog editor(TimetablePanel panel, LocalDate date, Event original, CampusMap.Building place) {
        panel.refresh(date);
        return panel.createEditor(original, date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), place);
    }

    public static void main(String[] args) throws Exception {
        CampusMap map = CampusMap.load(Path.of(args[0]));
        CampusMap.Building building = map.buildings().get(0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDate past = LocalDate.of(2020, 1, 6);
                PlannerModel model = new PlannerModel();
                int[] saves = {0};
                TimetablePanel panel = new TimetablePanel(map, model, () -> { saves[0]++; return true; });
                JDialog dialog = editor(panel, past, null, building);
                try {
                    components(dialog, JRadioButton.class).stream().filter(b -> b.getText().equals("선택한 날짜만"))
                            .findFirst().orElseThrow().doClick();
                    require(components(dialog, JTextField.class).stream().anyMatch(f -> f.getText().equals(past.toString())), "past date clamped");
                    components(dialog, JTextField.class).get(0).setText("Late study");
                    List<TimePicker> times = components(dialog, TimePicker.class);
                    times.get(0).setTime(LocalTime.of(23, 55));
                    times.get(1).setTime(LocalTime.MAX);
                    dialog.getRootPane().getDefaultButton().doClick();
                    require(model.events().size() == 1 && saves[0] == 1, "once event not saved exactly once");
                    Event saved = model.events().get(0);
                    require(saved.date().equals(past) && saved.start().equals(LocalTime.of(23, 55))
                            && saved.end().equals(LocalTime.MAX), "save changed date or midnight");
                    System.out.println("PASS common-editor-past-date-preserved-and-24-end-saved");
                } finally { dialog.dispose(); }

                Event original = new Event("old", "Legacy", building.id(), LocalTime.of(7, 3, 14),
                        LocalTime.of(8, 17, 22), PlannerModel.Category.OTHER, null, past, "memo");
                model = new PlannerModel();
                model.putEvent(original);
                panel = new TimetablePanel(map, model, () -> true);
                dialog = editor(panel, LocalDate.of(2027, 3, 1), original, building);
                try {
                    dialog.getRootPane().getDefaultButton().doClick();
                    require(model.events().equals(List.of(original)), "legacy edit changed original values");
                    System.out.println("PASS common-editor-edit-preserves-off-grid-times-and-original-date");
                } finally { dialog.dispose(); }

                model = new PlannerModel();
                panel = new TimetablePanel(map, model, () -> { throw new AssertionError("invalid event persisted"); });
                dialog = editor(panel, past, null, building);
                try {
                    components(dialog, JTextField.class).get(0).setText("Invalid");
                    List<TimePicker> times = components(dialog, TimePicker.class);
                    times.get(0).setTime(LocalTime.of(12, 0));
                    times.get(1).setTime(LocalTime.of(10, 0));
                    dialog.getRootPane().getDefaultButton().doClick();
                    require(model.events().isEmpty() && components(dialog, JLabel.class).stream()
                            .anyMatch(label -> Theme.WARNING.equals(label.getForeground()) && !label.getText().isBlank()),
                            "invalid interval saved or error not displayed");
                    System.out.println("PASS common-editor-invalid-duration-displays-error");
                } finally { dialog.dispose(); }
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) {
            failure.get().printStackTrace();
            System.exit(1);
        }
        System.out.println("DIALOG RESULT: 3 passed, 0 failed");
    }
}
