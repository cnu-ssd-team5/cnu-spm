package campus.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Comparator;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import campus.model.PlannerModel;
import campus.service.Place;
import campus.service.PlaceCatalog;

/** Shared place search for schedules and independent route searches. */
final class PlacePicker extends JPanel {
    private final PlaceCatalog catalog;
    private final PlannerModel model;
    private final JTextField query = new JTextField();
    private final JComboBox<Place> choices = new JComboBox<>();

    PlacePicker(PlaceCatalog catalog, PlannerModel model) {
        super(new BorderLayout(0, 6));
        this.catalog = catalog;
        this.model = model;
        setOpaque(false);
        Theme.field(query);
        query.setToolTipText("장소 이름 검색");
        query.getAccessibleContext().setAccessibleName("장소 검색");
        choices.setFont(Theme.font(13, Font.PLAIN));
        choices.setBackground(Color.WHITE);
        choices.setMaximumRowCount(10);
        choices.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                String text = "장소를 선택하세요";
                if (value instanceof Place place) {
                    text = (model.isFavorite(place.id()) ? "★ " : "") + place.toString();
                    if (place.pin()) text += " · 핀";
                }
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        add(query, BorderLayout.NORTH);
        add(choices, BorderLayout.CENTER);
        refresh();
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { refresh(); }
            public void removeUpdate(DocumentEvent event) { refresh(); }
            public void changedUpdate(DocumentEvent event) { refresh(); }
        });
    }

    Place selected() { return (Place) choices.getSelectedItem(); }

    void select(Place place) {
        query.setText("");
        choices.setSelectedItem(null);
        refresh();
        if (place != null && !place.named()) choices.addItem(place);
        choices.setSelectedItem(place);
    }

    private void refresh() {
        Place previous = selected();
        String filter = query.getText().strip().toLowerCase(Locale.ROOT);
        choices.removeAllItems();
        choices.addItem(null);
        catalog.namedPlaces().stream()
                .filter(place -> place.name().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing((Place place) -> !model.isFavorite(place.id()))
                        .thenComparing(Place::name))
                .forEach(choices::addItem);
        if (previous != null && previous.name().toLowerCase(Locale.ROOT).contains(filter)) {
            if (!previous.named()) choices.addItem(previous);
            choices.setSelectedItem(previous);
        } else if (choices.getItemCount() == 2) choices.setSelectedIndex(1);
    }
}
