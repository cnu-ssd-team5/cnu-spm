package campus.ui;

import java.awt.*;
import javax.swing.*;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.service.Place;
import campus.service.PlaceCatalog;

final class RouteDialog extends JDialog {
    record Result(Place from, Place to, RouteService.Route route) { }

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
        JPanel fields = Theme.transparent(new GridLayout(0, 1, 0, 9));
        fields.add(Theme.label("출발", 13, Theme.INK));
        fields.add(from);
        fields.add(Theme.button("출발 / 도착 바꾸기", false, this::swap));
        fields.add(Theme.label("도착", 13, Theme.INK));
        fields.add(to);
        content.add(fields, BorderLayout.CENTER);
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
        setSize(500, 520);
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

