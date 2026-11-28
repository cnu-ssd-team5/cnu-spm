package campus.ui;

import campus.folder.FolderService;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import javax.swing.*;

/** 과목자료와 파일 검색에서 사용하는 비모달 자료함. */
public final class MiniFolderPopup extends JDialog {
    private final FolderBrowserPanel browser;
    private final BuildingFolderController controller;
    private final Path building;
    public MiniFolderPopup(Window owner, BuildingFolderController controller, FolderService service,
                           Path building, Runnable updated) throws java.io.IOException {
        super(owner);
        this.controller = controller;
        this.building = building;
        setUndecorated(true);
        browser = new FolderBrowserPanel(controller, service, building, updated, this::dispose);
        setContentPane(browser);
        setSize(365,300);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    public boolean isWorking() { return browser.isWorking(); }
    public void startAt(Path folder) throws java.io.IOException { browser.startAt(folder); }
    public void showBeside(JComponent anchor) { showAt(anchor, new Point(anchor.getWidth(), 0)); }
    public void showAt(JComponent anchor, Point localPoint) {
        Point point = new Point(localPoint);
        SwingUtilities.convertPointToScreen(point, anchor);
        Rectangle screen = anchor.getGraphicsConfiguration().getBounds();
        int left = point.x + 6;
        if (left + getWidth() > screen.x + screen.width) left = point.x - getWidth() - 6;
        setLocation(Math.max(screen.x, left), Math.max(screen.y, Math.min(point.y, screen.y + screen.height - getHeight())));
        setVisible(true);
        browser.focusFiles();
    }
    @Override public void dispose() {
        if (isWorking()) return;
        boolean wasOpen = isDisplayable();
        browser.release();
        super.dispose();
        if (wasOpen) controller.previewClosed(building);
    }
}
