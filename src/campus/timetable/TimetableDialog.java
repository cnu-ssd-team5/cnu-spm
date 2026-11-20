package campus.timetable;

import campus.map.CampusMap;
import campus.ui.Theme;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 주간 반복 시간표 보기 및 여러 요일 일괄 편집. 저장은 호스트의 기존 저장 경로를 사용한다. */
public final class TimetableDialog extends JDialog {
    private final CampusMap map;
    private final PlannerModel model;
    private final TimetableService service;
    private final BooleanSupplier save;
    private final WeekGrid grid;
    public TimetableDialog(JFrame owner,CampusMap map,PlannerModel model,BooleanSupplier save) {
        super(owner,"주간 시간표",true);this.map=map;this.model=model;this.service=new TimetableService(model);this.save=save;
        grid=new WeekGrid(service,map,(event,day,start,end)->edit(event,day,start),save);
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=new JButton("+ 수업 추가");add.addActionListener(e->edit(null,DayOfWeek.MONDAY,LocalTime.of(9,0)));bar.add(add);
        JLabel hint=new JLabel("빈칸 우클릭으로 추가 · 일정 우클릭으로 수정·삭제 선택");hint.setForeground(Color.GRAY);bar.add(hint);
        add(bar,BorderLayout.NORTH);add(grid.scrollPane(),BorderLayout.CENTER);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();setSize(Math.min(1080,screen.width-60),Math.min(790,screen.height-80));setLocationRelativeTo(owner);
    }
    private void edit(Event original,DayOfWeek day,LocalTime time) {
        JDialog dialog=new JDialog(this,original==null?"수업 추가":"수업 편집",true);
        JTextField title=new JTextField(original==null?"":original.title());
        JComboBox<String> start=timePicker(original==null?time:original.start());
        LocalTime until=time.plusHours(1);if(!until.isAfter(time))until=LocalTime.of(23,59);
        JComboBox<String> end=timePicker(original==null?until:original.end());
        JTextField query=new JTextField();JComboBox<CampusMap.Building> places=new JComboBox<>();
        JComboBox<Category> category=new JComboBox<>(Category.values());category.setSelectedItem(original==null?Category.CLASS:original.category());
        JTextArea note=new JTextArea(original==null?"":original.note(),3,25);note.setLineWrap(true);
        List<CampusMap.Building> choices=map.buildings().stream().sorted(Comparator
                .comparing((CampusMap.Building b)->!model.isFavorite(b.id())).thenComparing(CampusMap.Building::name)).toList();
        Set<String> seen=new HashSet<>(),duplicates=new HashSet<>();
        choices.forEach(b->{if(!seen.add(b.name()))duplicates.add(b.name());});
        places.setRenderer(new DefaultListCellRenderer(){
            public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                String text="장소를 선택하세요";
                if(value instanceof CampusMap.Building b)text=(model.isFavorite(b.id())?"★ ":"")+b.name()+(duplicates.contains(b.name())?" · "+b.id():"");
                return super.getListCellRendererComponent(list,text,index,selected,focus);
            }
        });
        Runnable filter=()->{
            CampusMap.Building previous=(CampusMap.Building)places.getSelectedItem();
            String q=query.getText().strip().toLowerCase(Locale.ROOT);
            places.removeAllItems();places.addItem(null);
            choices.stream().filter(b->b.name().toLowerCase(Locale.ROOT).contains(q)).forEach(places::addItem);
            if(previous!=null&&previous.name().toLowerCase(Locale.ROOT).contains(q))places.setSelectedItem(previous);
            else if(places.getItemCount()==2)places.setSelectedIndex(1);
        };filter.run();
        if(original!=null)places.setSelectedItem(map.building(original.buildingId()));
        query.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter.run();}public void removeUpdate(DocumentEvent e){filter.run();}public void changedUpdate(DocumentEvent e){filter.run();}});
        JPanel days=Theme.transparent(new FlowLayout(FlowLayout.LEFT,3,0));Map<DayOfWeek,JCheckBox> checks=new EnumMap<>(DayOfWeek.class);
        Set<DayOfWeek> selected=new HashSet<>();if(original==null)selected.add(day);else service.group(original).forEach(e->selected.add(e.weekday()));
        String[] labels={"월","화","수","목","금","토","일"};
        for(DayOfWeek d:DayOfWeek.values()){JCheckBox c=new JCheckBox(labels[d.getValue()-1],selected.contains(d));c.setOpaque(false);c.setFont(Theme.font(13,Font.PLAIN));checks.put(d,c);days.add(c);}
        JPanel content=new JPanel(new BorderLayout(0,16));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24,26,22,26));
        content.add(Theme.heading(original==null?"어떤 수업을 추가할까요?":"수업 편집",21),BorderLayout.NORTH);
        JPanel fields=Theme.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        int row=0;
        row=row(fields,c,row,"수업 이름",title);
        row=row(fields,c,row,"반복 요일 · 여러 요일 선택",days);
        JPanel times=Theme.transparent(new GridLayout(1,2,12,0));times.add(start);times.add(end);
        row=row(fields,c,row,"시작 / 종료 (24시간 HH:mm)",times);
        row=row(fields,c,row,"일정 종류",category);
        row=row(fields,c,row,"건물 검색",query);
        row=row(fields,c,row,"장소 · 즐겨찾기 건물이 먼저 표시됩니다",places);
        note.setWrapStyleWord(true);
        JScrollPane memoScroll=new JScrollPane(note);memoScroll.setPreferredSize(new Dimension(0,80));memoScroll.setMinimumSize(new Dimension(0,65));
        row(fields,c,row,"메모 · 강의실, 준비물, 모임 안내",memoScroll);
        for(JComponent field:List.of(title,query,note))Theme.field(field);
        for(JComboBox<?> combo:List.of(category,places,start,end)){combo.setFont(Theme.font(13,Font.PLAIN));combo.setBackground(Color.WHITE);}
        places.setMaximumRowCount(12);
        query.setToolTipText("건물 이름을 입력한 뒤 아래 목록에서 장소를 선택하세요");
        content.add(Theme.scroll(fields),BorderLayout.CENTER);
        JLabel error=Theme.label(" ",12,Theme.WARNING);
        JPanel south=Theme.transparent(new BorderLayout(0,8));south.add(error,BorderLayout.NORTH);
        JPanel buttons=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        if(original!=null){JButton delete=Theme.deleteButton("수업 전체 요일 삭제",()->{
            if(JOptionPane.showConfirmDialog(dialog,"이 수업의 모든 요일을 삭제할까요?","삭제",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            service.delete(original);save.getAsBoolean();grid.refresh();dialog.dispose();
        });buttons.add(delete);}
        buttons.add(Theme.button("취소",false,dialog::dispose));
        JButton commit=Theme.button("수업 저장",true,()->{
            try {
                CampusMap.Building building=(CampusMap.Building)places.getSelectedItem();if(building==null)throw new IllegalArgumentException("건물을 선택하세요.");
                Set<DayOfWeek> chosen=EnumSet.noneOf(DayOfWeek.class);checks.forEach((d,check)->{if(check.isSelected())chosen.add(d);});
                List<Event> proposed=service.prepare(original,title.getText(),building.id(),LocalTime.parse(start.getEditor().getItem().toString().strip()),LocalTime.parse(end.getEditor().getItem().toString().strip()),(Category)category.getSelectedItem(),chosen,note.getText());
                if(service.overlaps(original,proposed)&&JOptionPane.showConfirmDialog(dialog,"다른 일정과 시간이 겹칩니다. 저장할까요?","시간 겹침",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                service.apply(original,proposed);save.getAsBoolean();grid.refresh();dialog.dispose();
            } catch(java.time.format.DateTimeParseException ex){error.setText("시간은 09:00 형식으로 입력하세요.");}
            catch(IllegalArgumentException ex){error.setText(ex.getMessage());}
        });
        buttons.add(commit);south.add(buttons,BorderLayout.CENTER);content.add(south,BorderLayout.SOUTH);
        dialog.setContentPane(content);dialog.getRootPane().setDefaultButton(commit);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);dialog.setMinimumSize(new Dimension(500,620));
        dialog.setSize(540,Math.min(820,Toolkit.getDefaultToolkit().getScreenSize().height-65));dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    static JComboBox<String> timePicker(LocalTime value) {
        JComboBox<String> picker=new JComboBox<>();
        for(int minute=0;minute<1440;minute+=15)picker.addItem(String.format("%02d:%02d",minute/60,minute%60));
        picker.setEditable(true);picker.setMaximumRowCount(10);picker.setSelectedItem(value.toString());
        Theme.field((JComponent)picker.getEditor().getEditorComponent());
        return picker;
    }
    private static int row(JPanel panel,GridBagConstraints c,int row,String label,JComponent field){
        c.gridy=row++;c.insets=new Insets(9,0,5,0);panel.add(Theme.label(label,12,Theme.MUTED),c);
        c.gridy=row++;c.insets=new Insets(0,0,3,0);panel.add(field,c);return row;
    }
}
