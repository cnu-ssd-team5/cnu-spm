package campus;

import java.awt.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import campus.MapData.Building;
import campus.MapData.Route;

final class RouteDialog extends JDialog {
    record Result(Building from, Building to, Route route) { }

    private final MapData data;
    private final PlacePicker from;
    private final PlacePicker to;
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private Result result;

    static Result open(JFrame owner, MapData data, PlannerModel model, Building initial) {
        RouteDialog dialog = new RouteDialog(owner, data, model, initial);
        dialog.setVisible(true);
        return dialog.result;
    }

    private RouteDialog(JFrame owner, MapData data, PlannerModel model, Building initial) {
        super(owner, "건물 간 최단거리 찾기", true);
        this.data = data;
        from = new PlacePicker(data, model);
        to = new PlacePicker(data, model);
        from.select(initial);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
        JPanel heading = Theme.transparent(new GridLayout(2, 1, 0, 6));
        heading.add(Theme.heading("건물 사이의 길을 찾아보세요", 20));
        heading.add(Theme.label("출발지와 도착지를 선택하면 거리와 도보 경로를 보여줍니다.", 12, Theme.MUTED));
        content.add(heading, BorderLayout.NORTH);

        JPanel fields = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        c.gridy = 0;
        fields.add(Theme.heading("출발 건물", 13), c);
        c.gridy++;
        fields.add(from, c);
        c.gridy++;
        c.insets = new Insets(4, 0, 14, 0);
        fields.add(Theme.button("출발 / 도착 바꾸기", false, this::swap), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        fields.add(Theme.heading("도착 건물", 13), c);
        c.gridy++;
        fields.add(to, c);
        content.add(fields, BorderLayout.CENTER);

        JPanel south = Theme.transparent(new BorderLayout(0, 8));
        south.add(error, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(Theme.button("취소", false, this::dispose));
        JButton search = Theme.button("최단거리 찾기", true, this::findRoute);
        buttons.add(search);
        south.add(buttons, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(search);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(560, Math.max(470, getHeight()));
        setMinimumSize(new Dimension(500, 450));
        setLocationRelativeTo(owner);
    }

    private void swap() {
        Building before = from.selected();
        from.select(to.selected());
        to.select(before);
    }

    private void findRoute() {
        Building source = from.selected();
        Building destination = to.selected();
        if (source == null || destination == null) {
            error.setText("출발 건물과 도착 건물을 모두 선택해 주세요.");
            return;
        }
        result = new Result(source, destination, data.route(source, destination));
        dispose();
    }

    private static final class PlacePicker extends JPanel {
        private final JTextField query = new JTextField();
        private final JComboBox<Building> choices = new JComboBox<>();
        private final List<Building> buildings;

        PlacePicker(MapData data, PlannerModel model) {
            super(new BorderLayout(0, 6));
            setOpaque(false);
            Theme.field(query);
            query.setToolTipText("건물 이름을 입력하고 아래 목록에서 선택하세요.");
            choices.setFont(Theme.font(13, Font.PLAIN));
            choices.setBackground(Color.WHITE);
            choices.setMaximumRowCount(10);
            buildings = data.buildings().stream().sorted(Comparator
                    .comparing((Building building) -> !model.isFavorite(building.id()))
                    .thenComparing(building -> building.name().startsWith("이름 없는"))
                    .thenComparing(Building::name)).toList();
            Set<String> duplicateNames = new HashSet<>();
            Set<String> seenNames = new HashSet<>();
            for (Building building : buildings) {
                if (!seenNames.add(building.name())) duplicateNames.add(building.name());
            }
            choices.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean selected, boolean focus) {
                    String text = "건물을 선택하세요";
                    if (value instanceof Building building) {
                        text = (model.isFavorite(building.id()) ? "★ " : "") + building.name();
                        if (duplicateNames.contains(building.name())) text += " · " + building.id();
                    }
                    return super.getListCellRendererComponent(list, text, index, selected, focus);
                }
            });
            refresh();
            query.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { refresh(); }
                public void removeUpdate(DocumentEvent e) { refresh(); }
                public void changedUpdate(DocumentEvent e) { refresh(); }
            });
            add(query, BorderLayout.NORTH);
            add(choices, BorderLayout.CENTER);
        }

        Building selected() {
            return (Building) choices.getSelectedItem();
        }

        void select(Building building) {
            query.setText("");
            choices.setSelectedItem(building);
        }

        private void refresh() {
            Building previous = selected();
            String filter = query.getText().strip().toLowerCase(Locale.ROOT);
            choices.removeAllItems();
            choices.addItem(null);
            for (Building building : buildings) {
                if (building.name().toLowerCase(Locale.ROOT).contains(filter)) choices.addItem(building);
            }
            if (previous != null && previous.name().toLowerCase(Locale.ROOT).contains(filter)) {
                choices.setSelectedItem(previous);
            } else if (choices.getItemCount() == 2) {
                choices.setSelectedIndex(1);
            }
        }
    }
}
