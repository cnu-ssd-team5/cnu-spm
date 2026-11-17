package campus.ui;

import java.awt.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import javax.swing.*;
import javax.swing.event.*;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;

/** 왼쪽의 건물 목록. 검색, 즐겨찾기, 선택한 건물 정보를 보여줍니다. */
final class PlacesPanel {
    final JPanel panel = Theme.card(new BorderLayout(0, 12));
    private final MainFrame frame;
    private final CampusMap data;
    private final PlannerModel model;
    private final JTextField search = new JTextField();
    private final JCheckBox favoritesOnly = new JCheckBox("즐겨찾기만 보기");
    private final DefaultListModel<Building> buildingItems = new DefaultListModel<>();
    private final JList<Building> buildings = new JList<>(buildingItems);
    private final JLabel buildingCount = Theme.label(" ", 11, Theme.MUTED);
    private final JLabel selectedName = Theme.heading("지도에서 건물을 선택하세요", 13);
    private final JLabel selectedDetail = Theme.label("건물 검색과 지도 클릭을 지원합니다", 11, Theme.MUTED);
    private final JButton favoriteButton;
    private boolean refreshing;

    PlacesPanel(MainFrame frame, CampusMap data, PlannerModel model) {
        this.frame = frame;
        this.data = data;
        this.model = model;
        favoriteButton = Theme.button("☆ 즐겨찾기", false, frame::toggleFavorite);
        JPanel top = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.insets = new Insets(0,0,12,0);
        top.add(Theme.heading("캠퍼스 장소", 17),c);
        Theme.field(search);
        search.setToolTipText("건물·정류장·핀 검색");
        search.putClientProperty("JTextField.placeholderText", "건물·정류장·핀 검색");
        c.gridy++;
        c.insets = new Insets(0,0,7,0);
        top.add(Theme.label("건물·정류장·핀 검색",11,Theme.MUTED),c);
        c.gridy++;
        top.add(search,c);
        favoritesOnly.setFont(Theme.font(12,Font.PLAIN));
        favoritesOnly.setForeground(Theme.INK);
        favoritesOnly.setOpaque(false);
        c.gridy++;
        top.add(favoritesOnly,c);
        c.gridy++;
        c.insets = new Insets(0,0,0,0);
        top.add(buildingCount,c);
        panel.add(top,BorderLayout.NORTH);
        buildings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buildings.setBackground(Color.WHITE);
        buildings.setFixedCellHeight(44);
        buildings.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                JLabel label = (JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                Building b = (Building)value;
                label.setText((model.isFavorite(b.id()) ? "★  " : "    ") + b.name());
                label.setFont(Theme.font(12,Font.PLAIN));
                label.setBorder(BorderFactory.createEmptyBorder(0,6,0,4));
                label.setBackground(selected ? Theme.PALE_BLUE : Color.WHITE);
                label.setForeground(selected ? Theme.BLUE : Theme.INK);
                label.setToolTipText(b.name());
                return label;
            }
        });
        buildings.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing && buildings.getSelectedValue()!=null) frame.selectBuilding(buildings.getSelectedValue());
        });
        panel.add(Theme.scroll(buildings),BorderLayout.CENTER);
        JPanel bottom = Theme.transparent(new GridBagLayout());
        c.gridy = 0;
        c.insets = new Insets(8,0,7,0);
        bottom.add(selectedName,c);
        c.gridy++;
        bottom.add(selectedDetail,c);
        c.gridy++;
        bottom.add(favoriteButton,c);
        panel.add(bottom,BorderLayout.SOUTH);
        favoritesOnly.addActionListener(e -> refreshBuildings());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshBuildings(); }
            public void removeUpdate(DocumentEvent e) { refreshBuildings(); }
            public void changedUpdate(DocumentEvent e) { refreshBuildings(); }
        });
    }

    void refresh() {
        refreshBuildings();
        updateSelectedBuilding();
    }

    void showSelected(Building building) {
        updateSelectedBuilding();
        if(!Objects.equals(buildings.getSelectedValue(),building)) {
            boolean old=refreshing;
            refreshing=true;
            buildings.setSelectedValue(building,true);
            refreshing=old;
        }
    }

    private void updateSelectedBuilding() {
        Building selectedBuilding=frame.selectedBuilding();
        boolean selected=selectedBuilding!=null;
        favoriteButton.setEnabled(selected);
        if(!selected)return;
        selectedName.setText(selectedBuilding.name());
        selectedName.setToolTipText(selectedBuilding.name());
        selectedDetail.setText(selectedBuilding.name().replace(" ","").startsWith("이름없는")?" ":selectedBuilding.id().startsWith("bus:")?"정류장":selectedBuilding.id().startsWith("pin:")?"사용자 핀":" ");
        favoriteButton.setText(model.isFavorite(selectedBuilding.id()) ? "★ 즐겨찾기 해제" : "☆ 즐겨찾기 추가");
    }

    private void refreshBuildings() {
        boolean old=refreshing;
        refreshing=true;
        String query=search.getText().strip().toLowerCase(Locale.ROOT);
        buildingItems.clear();
        data.buildings().stream()
                .filter(b -> !b.name().replace(" ","").startsWith("이름없는"))
                .filter(b -> b.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(b -> !favoritesOnly.isSelected() || model.isFavorite(b.id()))
                .sorted(Comparator.comparing((Building b) -> !model.isFavorite(b.id()))
                        .thenComparing(b -> b.name().replace(" ","").startsWith("이름없는"))
                        .thenComparing(Building::name))
                .forEach(buildingItems::addElement);
        if(frame.selectedBuilding()!=null)buildings.setSelectedValue(frame.selectedBuilding(),true);
        buildingCount.setText(buildingItems.size()+"개 장소 · 즐겨찾기 "+model.favorites().size()+"개");
        refreshing=old;
    }
}
