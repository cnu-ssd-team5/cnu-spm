package campus.timetable;

import campus.map.CampusMap;
import campus.ui.PlacePicker;
import campus.ui.Theme;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 선택한 주의 일정 보기 및 일회성/여러 요일 반복 일정 편집. */
public final class TimetablePanel extends JPanel {
    private CampusMap map;
    private PlannerModel model;
    private boolean readOnly;
    private final JScrollPane scroll;
    private final TimetableService service;
    private final BooleanSupplier save;
    private final WeekGrid grid;
    public TimetablePanel(CampusMap map,PlannerModel model,BooleanSupplier save) {
        super(new BorderLayout());setOpaque(false);this.map=map;this.model=model;this.service=new TimetableService(model);this.save=save;
        grid=new WeekGrid(service,map,this::edit,save);
        JPanel bar=Theme.transparent(new BorderLayout(10,0));
        bar.add(period,BorderLayout.CENTER);
        bar.add(editHint, BorderLayout.SOUTH);
        add(bar,BorderLayout.NORTH);
        scroll = grid.scrollPane();
        add(scroll, BorderLayout.CENTER);
    }
    private LocalDate selectedDate = campus.calendar.PlannerTime.today();
    private final JLabel editHint = Theme.label("빈 시간대 드래그로 추가 · 일정 더블클릭으로 편집 · 우클릭으로 수정·삭제 선택", 11, Theme.MUTED);

    public void setScheduleContext(CampusMap map, PlannerModel model, boolean readOnly) {
        this.map = map;
        this.model = model;
        this.readOnly = readOnly;
        service.setModel(model);
        grid.setScheduleContext(map, readOnly);
        editHint.setVisible(!readOnly);
    }

    public void linkVerticalScroll(TimetablePanel other) {
        scroll.getVerticalScrollBar().setModel(other.scroll.getVerticalScrollBar().getModel());
    }

    private final JLabel period=Theme.heading("주간 시간표",17);
    public void refresh(campus.calendar.Semester semester) {
        refresh(semester.start());
    }
    public void refresh(LocalDate date) { refresh(date, 8); }

    public void refresh(LocalDate date, int earliestHour) {
        selectedDate = date;
        service.weekOf(date);
        LocalDate sunday = TimetableService.sunday(date);
        period.setText("주간 시간표 · " + campus.calendar.Semester.forDate(date) + " · "
                + sunday.getMonthValue() + "/" + sunday.getDayOfMonth() + "–"
                + sunday.plusDays(6).getMonthValue() + "/" + sunday.plusDays(6).getDayOfMonth());
        grid.refresh(earliestHour);
    }
    /** 우측 날짜 버튼과 지도에서 사용하는 공통 일정 등록창. */
    public void addEvent(LocalDate date, CampusMap.Building initialPlace) {
        refresh(date);
        edit(null,date.getDayOfWeek(),LocalTime.of(9,0),LocalTime.of(10,0),initialPlace);
    }
    public void editEvent(Event event) {
        edit(event,event.recurring()?event.weekday():event.date().getDayOfWeek(),event.start(),event.end());
    }
    private void edit(Event original,DayOfWeek day,LocalTime time,LocalTime selectedEnd) {
        edit(original,day,time,selectedEnd,null);
    }
    private void edit(Event original,DayOfWeek day,LocalTime time,LocalTime selectedEnd,CampusMap.Building initialPlace) {
        if (readOnly) return;
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),original==null?"일정 추가":"일정 편집",Dialog.ModalityType.APPLICATION_MODAL);
        JTextField title=new JTextField(original==null?"":original.title());
        TimePicker start=new TimePicker(false); start.setTime(original==null?time:original.start());
        LocalTime until=selectedEnd;
        TimePicker end=new TimePicker(true); end.setTime(original==null?until:original.end());
        JTextField query=new JTextField();
        CampusMap.Building initial = original == null ? initialPlace : map.building(original.buildingId());
        PlacePicker places = new PlacePicker(map, model, query, initial, original == null ? "" : original.customPlace());
        JComboBox<Category> category = new JComboBox<>(Category.values());
        category.setEditable(true);
        category.getAccessibleContext().setAccessibleName("일정 종류");
        if (original != null) {
            category.setSelectedItem(original.customCategory().isEmpty() ? original.category() : original.customCategory());
        }
        JTextArea note=new JTextArea(original==null?"":original.note(),3,25);note.setLineWrap(true);
        JPanel days=Theme.transparent(new FlowLayout(FlowLayout.LEFT,3,0));Map<DayOfWeek,JCheckBox> checks=new EnumMap<>(DayOfWeek.class);
        Set<DayOfWeek> selected=new HashSet<>();if(original==null||!original.recurring())selected.add(day);else service.group(original).forEach(e->selected.add(e.weekday()));
        String[] labels={"일","월","화","수","목","금","토"};
        for(int i=0;i<7;i++){DayOfWeek d=DayOfWeek.SUNDAY.plus(i);JCheckBox c=new JCheckBox(labels[i],selected.contains(d));c.setOpaque(false);c.setFont(Theme.font(13,Font.PLAIN));checks.put(d,c);days.add(c);}
        JRadioButton once = new JRadioButton("선택한 날짜만", original != null && !original.recurring());
        JRadioButton repeat = new JRadioButton("매주 반복", original == null || original.recurring());
        once.setOpaque(false); repeat.setOpaque(false);
        ButtonGroup modes = new ButtonGroup(); modes.add(once); modes.add(repeat);
        JPanel mode = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0)); mode.add(once); mode.add(repeat);
        JTextField date = new JTextField((original != null && !original.recurring()
                ? original.date() : service.dateFor(day)).toString());
        Theme.field(date);
        Runnable updateMode = () -> {
            date.setEnabled(once.isSelected());
            checks.values().forEach(check -> check.setEnabled(repeat.isSelected()));
        };
        once.addActionListener(e -> updateMode.run()); repeat.addActionListener(e -> updateMode.run());
        updateMode.run();
        JPanel content=new JPanel(new BorderLayout(0,16));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24,26,22,26));
        content.add(Theme.heading(original==null?"어떤 일정을 추가할까요?":"일정 편집",21),BorderLayout.NORTH);
        JPanel fields=Theme.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        int row=0;
        row=row(fields,c,row,"일정 이름",title);
        row=row(fields,c,row,"반복",mode);
        row=row(fields,c,row,"날짜 (YYYY-MM-DD)",date);
        row=row(fields,c,row,"반복 요일 · 여러 요일 선택",days);
        JPanel times=Theme.transparent(new GridLayout(1,2,12,0));times.add(start);times.add(end);
        row=row(fields,c,row,"시작 / 종료",times);
        row=row(fields,c,row,"일정 종류 · 선택 또는 직접 입력",category);
        row=row(fields,c,row,"건물 검색",query);
        row=row(fields,c,row,"장소 · 선택 또는 직접 입력",places);
        note.setWrapStyleWord(true);
        JScrollPane memoScroll=new JScrollPane(note);memoScroll.setPreferredSize(new Dimension(0,80));memoScroll.setMinimumSize(new Dimension(0,65));
        row(fields,c,row,"메모 · 강의실, 준비물, 모임 안내",memoScroll);
        for(JComponent field:List.of(title,query,note))Theme.field(field);
        category.setFont(Theme.font(13,Font.PLAIN));
        category.setBackground(Color.WHITE);
        content.add(Theme.scroll(fields),BorderLayout.CENTER);
        JLabel error=Theme.label(" ",12,Theme.WARNING);
        JPanel south=Theme.transparent(new BorderLayout(0,8));south.add(error,BorderLayout.NORTH);
        JPanel buttons=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        if(original!=null){JButton delete=Theme.deleteButton(original.recurring()?"반복 일정 전체 삭제":"일정 삭제",()->{
            if (grid.confirmDelete(dialog, original)) dialog.dispose();
        });buttons.add(delete);}
        buttons.add(Theme.button("취소",false,dialog::dispose));
        JButton commit=Theme.button("일정 저장",true,()->{
            try {
                PlacePicker.Selection place = places.selection();
                String categoryText = ((JTextField) category.getEditor().getEditorComponent()).getText().strip();
                if (categoryText.isEmpty()) throw new IllegalArgumentException("일정 종류를 선택하거나 직접 입력해 주세요.");
                Category kind = Category.fromLabel(categoryText);
                String customCategory = kind.toString().equals(categoryText) ? "" : categoryText;
                Set<DayOfWeek> chosen=EnumSet.noneOf(DayOfWeek.class);checks.forEach((d,check)->{if(check.isSelected())chosen.add(d);});
                List<Event> proposed = repeat.isSelected()
                        ? service.prepare(original, title.getText(), place.buildingId(), start.time(), end.time(),
                                kind, chosen, note.getText(), customCategory, place.customPlace())
                        : List.of(service.prepareOnce(original, title.getText(), place.buildingId(), start.time(), end.time(),
                                kind, LocalDate.parse(date.getText().strip()), note.getText(), customCategory, place.customPlace()));
                if(original!=null && original.recurring() && once.isSelected()
                        && JOptionPane.showConfirmDialog(dialog,"기존 반복 일정의 모든 요일을 삭제하고 선택한 날짜의 일정 하나로 바꿀까요?",
                                "반복 변경",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                if(service.overlaps(original,proposed)&&JOptionPane.showConfirmDialog(dialog,"다른 일정과 시간이 겹칩니다. 저장할까요?","시간 겹침",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                service.apply(original,proposed);save.getAsBoolean();grid.refresh();dialog.dispose();
            } catch(java.time.format.DateTimeParseException ex){error.setText("날짜는 2026-11-17 형식으로 입력하세요.");}
            catch(IllegalArgumentException ex){error.setText(ex.getMessage());}
        });
        buttons.add(commit);south.add(buttons,BorderLayout.CENTER);content.add(south,BorderLayout.SOUTH);
        dialog.setContentPane(content);dialog.getRootPane().setDefaultButton(commit);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialog.setMinimumSize(new Dimension(500,620));
        dialog.setSize(560,Math.min(900,Toolkit.getDefaultToolkit().getScreenSize().height-65));dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    private static int row(JPanel panel,GridBagConstraints c,int row,String label,JComponent field){
        c.gridy=row++;c.insets=new Insets(9,0,5,0);panel.add(Theme.label(label,12,Theme.MUTED),c);
        c.gridy=row++;c.insets=new Insets(0,0,3,0);panel.add(field,c);return row;
    }
}
