package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import javax.swing.*;
import javax.swing.event.*;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.places.PlaceService;

/** Map context: searchable places, real route endpoints and shortcuts. */
final class PlacesPanel {
    final JPanel panel = Theme.transparent(new BorderLayout(0, 20));
    private final MainFrame frame;
    private CampusMap data;
    private PlannerModel model;
    private final JTextField search = Theme.searchField("캠퍼스 내 장소 검색");
    private final JCheckBox favoritesOnly = Theme.checkBox("즐겨찾기만 보기",false);
    private final DefaultListModel<Building> buildingItems = new DefaultListModel<>();
    private final JList<Building> buildings = new JList<>(buildingItems);
    private final JLabel buildingCount = Theme.label(" ", 11, Theme.MUTED);
    private final JLabel selectedName = Theme.heading("선택한 장소", 13);
    private final JButton favoriteButton;
    private final JButton addEvent;
    private final JPanel fileSlot = Theme.transparent(new BorderLayout());
    private final JComboBox<Building> fromChoice = new JComboBox<>(), toChoice = new JComboBox<>();
    private final JPopupMenu results = new JPopupMenu();
    private boolean refreshing, pinsOnly;

    PlacesPanel(MainFrame frame, CampusMap data, PlannerModel model) {
        this.frame=frame;this.data=data;this.model=model;
        favoriteButton=Theme.button("즐겨찾기",false,()->frame.toggleFavorite());
        addEvent=Theme.actionRow("일정 추가","calendar",()->frame.edit(null));
        JPanel heading=Theme.transparent(new BorderLayout(0,12));
        heading.add(Theme.heading("지도",24),BorderLayout.NORTH);
        heading.add(search,BorderLayout.SOUTH);
        panel.add(heading,BorderLayout.NORTH);
        search.addActionListener(e->showResults());
        search.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){changed();}
            public void removeUpdate(DocumentEvent e){changed();}
            public void changedUpdate(DocumentEvent e){changed();}
            private void changed(){refreshBuildings();if(search.isFocusOwner()&&search.isShowing())showResults();}
        });
        favoritesOnly.setOpaque(false);favoritesOnly.setFont(Theme.font(12,Font.PLAIN));
        favoritesOnly.addActionListener(e->refreshBuildings());
        buildings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buildings.setFixedCellHeight(40);
        buildings.setBackground(Color.WHITE);
        buildings.setCellRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                Building b=(Building)value;
                label.setText((PlacesPanel.this.model.isFavorite(b.id())?"★  ":"")+b.name());
                label.setFont(Theme.font(12,Font.PLAIN));label.setBorder(BorderFactory.createEmptyBorder(0,12,0,8));
                label.setBackground(selected?Theme.PALE_BLUE:Color.WHITE);label.setForeground(selected?Theme.BLUE:Theme.INK);
                label.setToolTipText(b.name());return label;
            }
        });
        buildings.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!refreshing&&buildings.getSelectedValue()!=null)frame.selectBuilding(buildings.getSelectedValue());});
        buildings.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))results.setVisible(false);}
            @Override public void mousePressed(MouseEvent e){noteMenu(e);}
            @Override public void mouseReleased(MouseEvent e){noteMenu(e);}
            private void noteMenu(MouseEvent e){
                if(!e.isPopupTrigger())return;int row=buildings.locationToIndex(e.getPoint());
                if(row<0||!buildings.getCellBounds(row,row).contains(e.getPoint()))return;
                JMenuItem note=frame.buildingNoteItem(buildingItems.get(row));if(note==null)return;
                buildings.setSelectedIndex(row);JPopupMenu menu=new JPopupMenu();menu.add(note);menu.show(buildings,e.getX(),e.getY());
            }
        });
        JPanel resultContent=Theme.transparent(new BorderLayout(0,8));
        JPanel filter=Theme.transparent(new BorderLayout());filter.add(favoritesOnly,BorderLayout.WEST);filter.add(buildingCount,BorderLayout.EAST);
        resultContent.add(filter,BorderLayout.NORTH);
        JScrollPane list=Theme.scroll(buildings);list.setPreferredSize(new Dimension(320,280));resultContent.add(list,BorderLayout.CENTER);
        JPanel selected=Theme.transparent(new BorderLayout(8,0));selected.add(selectedName,BorderLayout.CENTER);selected.add(favoriteButton,BorderLayout.EAST);
        resultContent.add(selected,BorderLayout.SOUTH);resultContent.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        results.setBorder(BorderFactory.createLineBorder(Theme.LINE));results.add(resultContent);
        results.setFocusable(false);

        JPanel content=new JPanel(new GridBagLayout()){
            @Override public Dimension getPreferredSize(){return new Dimension(0,super.getPreferredSize().height);}
        };content.setOpaque(false);GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTH;
        JPanel route=Theme.transparent(new BorderLayout(0,12));
        JPanel endpoints=Theme.transparent(new GridLayout(0,1,0,8));
        endpoints.add(endpoint("출발",fromChoice));endpoints.add(endpoint("도착",toChoice));route.add(endpoints,BorderLayout.NORTH);
        JButton find=Theme.button("경로 안내",true,()->frame.findInlineRoute((Building)fromChoice.getSelectedItem(),(Building)toChoice.getSelectedItem()));
        find.setIcon(UiIcons.of("route",17));route.add(find,BorderLayout.SOUTH);content.add(route,c);
        c.gridy++;c.insets=new Insets(24,4,8,0);content.add(Theme.heading("빠른 기능",13),c);
        JPanel actions=Theme.transparent(new GridLayout(0,1,0,0));
        actions.setBorder(BorderFactory.createMatteBorder(1,0,0,0,Theme.LINE));
        actions.add(Theme.actionRow("즐겨찾는 장소","star",()->browse(true,false)));
        actions.add(Theme.actionRow("내 핀 · 메모한 장소","pin",()->browse(false,true)));
        actions.add(fileSlot);actions.add(addEvent);
        actions.add(Theme.actionRow("일정 공유","share",frame::showSharingDialog));
        actions.add(Theme.actionRow("일정 가져오기","import",()->frame.share(true)));
        c.gridy++;c.insets=new Insets(0,0,0,0);content.add(actions,c);
        c.gridy++;c.weighty=1;content.add(Theme.transparent(new BorderLayout()),c);
        panel.add(Theme.scroll(content),BorderLayout.CENTER);
        populateEndpoints();
    }

    private JPanel endpoint(String title,JComboBox<Building> choice){
        JPanel row=Theme.rounded(new BorderLayout(4,0),12);row.setBorder(BorderFactory.createEmptyBorder(4,8,4,4));
        JLabel label=Theme.label(title,11,title.equals("출발")?Theme.BLUE:Theme.TODAY_TEXT);row.add(label,BorderLayout.WEST);
        choice.setFont(Theme.font(12,Font.PLAIN));choice.setBackground(Color.WHITE);choice.setMaximumRowCount(12);
        Theme.comboBox(choice);
        choice.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                return super.getListCellRendererComponent(list,value instanceof Building b?b.name():"건물 선택",index,selected,focus);
            }
        });
        choice.getAccessibleContext().setAccessibleName(title+" 건물");row.add(choice,BorderLayout.CENTER);return row;
    }
    private void populateEndpoints(){
        for(JComboBox<Building> choice:java.util.List.of(fromChoice,toChoice)){
            choice.removeAllItems();choice.addItem(null);
            data.buildings().stream().filter(PlaceService::hasName)
                    .sorted(Comparator.comparing(Building::name)).forEach(choice::addItem);
        }
    }
    void installFileSearch(JButton button){
        button.setText("자료 파일 검색");button.setIcon(UiIcons.of("folder"));button.setIconTextGap(8);
        button.putClientProperty("button.flat",true);button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(Theme.font(13,Font.PLAIN));button.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));fileSlot.add(button);
    }
    private void browse(boolean favorites,boolean pins){
        favoritesOnly.setSelected(favorites);pinsOnly=pins;refreshBuildings();showResults();
    }
    void searchFor(String query,Component anchor){
        favoritesOnly.setSelected(false);pinsOnly=false;search.setText(query);refreshBuildings();
        if(search.isShowing())showResults();
        else if(anchor.isShowing())results.show(anchor,0,anchor.getHeight()+5);
    }
    private void showResults(){
        if(!search.isShowing())return;
        results.show(search,0,search.getHeight()+5);
        search.requestFocusInWindow();
    }
    void closePopup(){results.setVisible(false);}
    void setScheduleContext(CampusMap data,PlannerModel model,boolean readOnly){
        this.data=data;this.model=model;favoriteButton.setVisible(!readOnly);addEvent.setEnabled(!readOnly);populateEndpoints();
    }
    void refresh(){refreshBuildings();updateSelectedBuilding();}
    void showSelected(Building building){
        updateSelectedBuilding();boolean old=refreshing;refreshing=true;
        if(!Objects.equals(buildings.getSelectedValue(),building))buildings.setSelectedValue(building,true);
        refreshing=old;
        if(building!=null&&toChoice.getSelectedItem()==null)toChoice.setSelectedItem(building);
    }
    private void updateSelectedBuilding(){
        Building selected=frame.selectedBuilding();favoriteButton.setEnabled(selected!=null);
        selectedName.setText(selected==null?"선택한 장소":selected.name());selectedName.setToolTipText(selectedName.getText());
        favoriteButton.setText(selected!=null&&model.isFavorite(selected.id())?"★ 해제":"☆ 즐겨찾기");
    }
    private void refreshBuildings(){
        boolean old=refreshing;refreshing=true;String query=search.getText().strip().toLowerCase(Locale.ROOT);buildingItems.clear();
        data.buildings().stream().filter(b->matchesSearch(b,query))
                .filter(b->!favoritesOnly.isSelected()||model.isFavorite(b.id()))
                .filter(b->!pinsOnly||b.id().startsWith("pin:")||!model.buildingNote(b.id()).isBlank())
                .sorted(Comparator.comparing((Building b)->!model.isFavorite(b.id())).thenComparing(Building::name)).forEach(buildingItems::addElement);
        if(frame.selectedBuilding()!=null)buildings.setSelectedValue(frame.selectedBuilding(),true);
        buildingCount.setText(buildingItems.size()+"개 장소");refreshing=old;
    }
    private boolean matchesSearch(Building building,String query){
        if(!query.isEmpty()&&model.buildingNote(building.id()).toLowerCase(Locale.ROOT).contains(query))return true;
        return PlaceService.hasName(building)&&building.name().toLowerCase(Locale.ROOT).contains(query);
    }
}
