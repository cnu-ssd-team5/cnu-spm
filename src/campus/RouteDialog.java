package campus;

import java.awt.*;
import javax.swing.*;

final class RouteDialog extends JDialog {
    record Result(Place from, Place to, MapData.Route route) { }

    private final PlaceCatalog places;
    private final PlacePicker from;
    private final PlacePicker to;
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private Result result;

    static Result open(JFrame owner, PlaceCatalog places, PlannerModel model, Place initial) {
        RouteDialog dialog = new RouteDialog(owner, places, model, initial);
        dialog.setVisible(true);
        return dialog.result;
    }

    private RouteDialog(JFrame owner, PlaceCatalog places, PlannerModel model, Place initial) {
        super(owner, "최단거리 찾기", true);
        this.places = places;
        from = new PlacePicker(places, model);
        to = new PlacePicker(places, model);
        from.select(initial);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
        content.add(Theme.heading("최단거리 찾기", 20), BorderLayout.NORTH);
        JPanel fields = Theme.transparent(new GridBagLayout());
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.anchor = GridBagConstraints.NORTH;
        for (Component input : new Component[] { Theme.label("출발", 13, Theme.INK), from,
                Theme.button("출발 / 도착 바꾸기", false, this::swap),
                Theme.label("도착", 13, Theme.INK), to }) {
            row.insets = new Insets(0, 0, 12, 0);
            fields.add(input, row);
            row.gridy++;
        }
        row.weighty = 1;
        fields.add(Theme.transparent(new BorderLayout()), row);
        content.add(Theme.scroll(fields), BorderLayout.CENTER);
        JPanel south = Theme.transparent(new BorderLayout(0, 8));
        south.add(error, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(Theme.button("취소", false, this::dispose));
        JButton search = Theme.button("경로 찾기", true, this::findRoute);
        buttons.add(search);
        south.add(buttons, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(search);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(650, screen.width - 40), Math.min(690, screen.height - 70));
        setMinimumSize(new Dimension(540, Math.min(560, screen.height - 70)));
        setLocationRelativeTo(owner);
    }

    private void swap() {
        Place previous = from.selected();
        from.select(to.selected());
        to.select(previous);
    }

    private void findRoute() {
        Place source = from.selected();
        Place destination = to.selected();
        if (source == null || destination == null) {
            error.setText("출발지와 도착지를 선택해 주세요.");
            return;
        }
        result = new Result(source, destination, places.route(source, destination));
        dispose();
    }
}

