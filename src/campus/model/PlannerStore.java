package campus.model;
import campus.calendar.Semester;
import campus.calendar.PlannerTime;


import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** UTF-8 XML storage. A failed load is reported so a damaged file is not replaced. */
public final class PlannerStore {
    private PlannerStore() { }

    public static PlannerModel load(Path path) throws IOException {
        if (Files.notExists(path)) {
            return new PlannerModel();
        }
        try (InputStream input = Files.newInputStream(path)) {
            Document document = newBuilder().parse(input);
            Element root = document.getDocumentElement();
            if (!"campus-planner".equals(root.getTagName())) {
                throw new IOException("개인 일정 파일 형식이 올바르지 않습니다.");
            }
            String version = root.getAttribute("version");
            if (!version.isEmpty() && !"1".equals(version) && !"2".equals(version) && !"3".equals(version)) {
                throw new IOException("지원하지 않는 개인 일정 파일 버전입니다: " + version);
            }
            PlannerModel model = new PlannerModel();
            Set<String> eventIds = new HashSet<>();
            for (Element element : entries(root, "events", "event")) {
                String id = requiredAttribute(element, "id");
                if (!eventIds.add(id)) {
                    throw new IOException("중복된 일정 ID가 있습니다: " + id);
                }
                String weekday = element.getAttribute("weekday");
                String date = element.getAttribute("date");
                String category = element.getAttribute("category");
                PlannerModel.Event event = new PlannerModel.Event(id,
                        requiredAttribute(element, "title"),
                        requiredAttribute(element, "buildingId"),
                        LocalTime.parse(requiredAttribute(element, "start")),
                        LocalTime.parse(requiredAttribute(element, "end")),
                        category.isEmpty() ? PlannerModel.Category.OTHER
                                : PlannerModel.Category.valueOf(category),
                        weekday.isEmpty() ? null : DayOfWeek.valueOf(weekday),
                        date.isEmpty() ? null : LocalDate.parse(date),
                        childText(element, "note"));
                String semester = element.getAttribute("semester");
                if (!semester.isEmpty()) model.putEvent(event, Semester.parse(semester));
                else if (!"3".equals(version)) {
                    model.putEvent(event, Semester.forDate(event.recurring() ? PlannerTime.today() : event.date()));
                } else model.putEvent(event);
            }
            for (Element element : entries(root, "favorites", "building")) {
                String id = requiredAttribute(element, "id");
                if (!model.isFavorite(id)) {
                    model.toggleFavorite(id);
                }
            }
            for (Element element : entries(root, "cancellations", "occurrence")) {
                String eventId = requiredAttribute(element, "eventId");
                LocalDate date = LocalDate.parse(requiredAttribute(element, "date"));
                // Keep older exceptions even when their date falls outside the newly assigned term.
                model.restoreCancellation(eventId, date);
            }
            for (Element element : entries(root, "day-notes", "day")) {
                model.setDayNote(LocalDate.parse(requiredAttribute(element, "date")),
                        element.getTextContent());
            }
            Set<String> namedBuildingIds = new HashSet<>();
            for (Element element : entries(root, "building-names", "building")) {
                String id = requiredAttribute(element, "id");
                if (!namedBuildingIds.add(id)) throw new IOException("중복된 건물 이름 ID가 있습니다: " + id);
                model.renameBuilding(id, requiredAttribute(element, "name"));
            }
            Set<String> pinIds = new HashSet<>();
            for (Element element : entries(root, "pins", "pin")) {
                String id = requiredAttribute(element, "id");
                if (!pinIds.add(id)) throw new IOException("중복된 핀 ID가 있습니다: " + id);
                model.putPin(new PlannerModel.Pin(id, requiredAttribute(element, "name"),
                        Double.parseDouble(requiredAttribute(element, "lat")),
                        Double.parseDouble(requiredAttribute(element, "lon"))));
            }
            Set<String> deadlineIds = new HashSet<>();
            for (Element element : entries(root, "deadlines", "deadline")) {
                String id = requiredAttribute(element, "id");
                if (!deadlineIds.add(id)) throw new IOException("중복된 마감 일정 ID가 있습니다: " + id);
                String completed = element.getAttribute("completed");
                if (!completed.isEmpty() && !"true".equals(completed) && !"false".equals(completed)) {
                    throw new IOException("마감 일정 완료 상태가 올바르지 않습니다: " + id);
                }
                String completedOn = element.getAttribute("completedOn");
                model.putDeadline(new PlannerModel.Deadline(id, requiredAttribute(element, "title"),
                        LocalDate.parse(requiredAttribute(element, "date")), childText(element, "note"),
                        Boolean.parseBoolean(completed), completedOn.isEmpty() ? null : LocalDate.parse(completedOn)));
            }
            return model;
        } catch (ParserConfigurationException | SAXException | RuntimeException exception) {
            throw new IOException("개인 일정 파일을 읽을 수 없습니다. 기존 파일을 확인해 주세요.", exception);
        }
    }

    public static void save(Path path, PlannerModel model) throws IOException {
        Document document;
        try {
            document = newBuilder().newDocument();
        } catch (ParserConfigurationException exception) {
            throw new IOException("일정 저장 형식을 준비할 수 없습니다.", exception);
        }
        Element root = document.createElement("campus-planner");
        root.setAttribute("version", "3");
        document.appendChild(root);
        // Capture schedules and their exceptions from the same consistent model state.
        synchronized (model) {
            Element events = append(document, root, "events");
            for (PlannerModel.Event event : model.events()) {
                Element element = append(document, events, "event");
                element.setAttribute("id", event.id());
                element.setAttribute("title", event.title());
                element.setAttribute("buildingId", event.buildingId());
                element.setAttribute("start", event.start().toString());
                element.setAttribute("end", event.end().toString());
                element.setAttribute("category", event.category().name());
                Semester semester = model.semesterOf(event.id());
                if (semester != null) element.setAttribute("semester", semester.id());
                if (event.recurring()) {
                    element.setAttribute("weekday", event.weekday().name());
                } else {
                    element.setAttribute("date", event.date().toString());
                }
                append(document, element, "note").setTextContent(event.note());
            }
            Element favorites = append(document, root, "favorites");
            model.favorites().stream().sorted().forEach(id ->
                    append(document, favorites, "building").setAttribute("id", id));
            Element cancellations = append(document, root, "cancellations");
            model.cancellations().stream()
                    .sorted(java.util.Comparator.comparing(PlannerModel.Cancellation::eventId)
                            .thenComparing(PlannerModel.Cancellation::date))
                    .forEach(value -> {
                        Element element = append(document, cancellations, "occurrence");
                        element.setAttribute("eventId", value.eventId());
                        element.setAttribute("date", value.date().toString());
                    });
            Element notes = append(document, root, "day-notes");
            model.dayNotes().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Element element = append(document, notes, "day");
                        element.setAttribute("date", entry.getKey().toString());
                        element.setTextContent(entry.getValue());
                    });
            Element names = append(document, root, "building-names");
            model.buildingNames().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Element element = append(document, names, "building");
                        element.setAttribute("id", entry.getKey());
                        element.setAttribute("name", entry.getValue());
                    });
            Element pins = append(document, root, "pins");
            for (PlannerModel.Pin pin : model.pins()) {
                Element element = append(document, pins, "pin");
                element.setAttribute("id", pin.id());
                element.setAttribute("name", pin.name());
                element.setAttribute("lat", Double.toString(pin.lat()));
                element.setAttribute("lon", Double.toString(pin.lon()));
            }
            Element deadlines = append(document, root, "deadlines");
            for (PlannerModel.Deadline deadline : model.deadlines()) {
                Element element = append(document, deadlines, "deadline");
                element.setAttribute("id", deadline.id());
                element.setAttribute("title", deadline.title());
                element.setAttribute("date", deadline.date().toString());
                element.setAttribute("completed", Boolean.toString(deadline.completed()));
                if (deadline.completedOn() != null) {
                    element.setAttribute("completedOn", deadline.completedOn().toString());
                }
                append(document, element, "note").setTextContent(deadline.note());
            }
        }

        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".campus-planner-", ".tmp");
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                transformer.transform(new DOMSource(document),
                        new StreamResult(Channels.newOutputStream(channel)));
                channel.force(true);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (TransformerException | RuntimeException exception) {
            throw new IOException("개인 일정을 저장할 수 없습니다.", exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static DocumentBuilder newBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder;
    }

    private static Element append(Document document, Element parent, String name) {
        Element child = document.createElement(name);
        parent.appendChild(child);
        return child;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<Element> entries(Element root, String container, String entry) {
        List<Element> result = new ArrayList<>();
        for (Element group : children(root, container)) {
            result.addAll(children(group, entry));
        }
        return result;
    }

    private static String childText(Element parent, String name) {
        List<Element> children = children(parent, name);
        return children.isEmpty() ? "" : children.get(0).getTextContent();
    }

    private static String requiredAttribute(Element element, String name) throws IOException {
        String value = element.getAttribute(name);
        if (value.isBlank()) {
            throw new IOException("개인 일정에 필수 항목이 없습니다: " + name);
        }
        return value.strip();
    }
}
