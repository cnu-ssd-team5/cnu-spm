package campus.ui;

import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A selected campus building keeps its ID; a typed place stays as text. */
public final class PlacePicker extends JComboBox<Building> {
    public record Selection(String buildingId, String customPlace) { }

    public PlacePicker(CampusMap map, PlannerModel model, JTextField query, Building initial, String customPlace) {
        setEditable(true);
        setFont(Theme.font(13, Font.PLAIN));
        setBackground(Color.WHITE);
        setMaximumRowCount(12);
        setToolTipText("목록에서 교내 장소를 고르거나 온라인·교외 장소를 입력하세요.");
        getAccessibleContext().setAccessibleName("장소 · 선택 또는 직접 입력");
        getEditor().getEditorComponent().getAccessibleContext().setAccessibleName("장소 · 선택 또는 직접 입력");
        query.setToolTipText("건물 이름을 입력하면 아래 장소 목록이 좁혀집니다.");

        List<Building> choices = map.buildings().stream().sorted(Comparator
                .comparing((Building building) -> !model.isFavorite(building.id()))
                .thenComparing(Building::name)).toList();
        Set<String> names = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (Building building : choices) {
            if (!names.add(building.name())) duplicates.add(building.name());
        }
        setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                String text = "장소를 선택하거나 입력하세요";
                if (value instanceof Building building) {
                    text = (model.isFavorite(building.id()) ? "★ " : "") + building.name();
                    if (duplicates.contains(building.name())) text += " · " + building.id();
                } else if (value instanceof String typed && !typed.isBlank()) {
                    text = typed;
                }
                putClientProperty("html.disable", true);
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        addItem(null);
        choices.forEach(this::addItem);
        setSelectedItem(customPlace.isBlank() ? initial : customPlace);
        query.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                Object previous = getEditor().getItem();
                String term = query.getText().strip().toLowerCase(Locale.ROOT);
                List<Building> matches = choices.stream()
                        .filter(place -> place.name().toLowerCase(Locale.ROOT).contains(term)).toList();
                removeAllItems();
                addItem(null);
                matches.forEach(PlacePicker.this::addItem);
                if (previous instanceof String typed && !typed.isBlank()) {
                    setSelectedItem(typed);
                } else if (previous instanceof Building && matches.contains(previous)) {
                    setSelectedItem(previous);
                } else if (matches.size() == 1) {
                    setSelectedIndex(1);
                }
            }
            @Override public void insertUpdate(DocumentEvent event) { filter(); }
            @Override public void removeUpdate(DocumentEvent event) { filter(); }
            @Override public void changedUpdate(DocumentEvent event) { filter(); }
        });
    }

    public Selection selection() {
        Object selected = getEditor().getItem();
        if (selected instanceof Building building) return new Selection(building.id(), "");
        String text = Objects.toString(selected, "").strip();
        if (text.isEmpty()) throw new IllegalArgumentException("교내 장소를 선택하거나 장소를 직접 입력해 주세요.");
        return new Selection("", text);
    }
}
