package guessmarket.engine.xml;

import guessmarket.engine.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loader/validator for the Exercise 3 XML format.
 *
 * Differences from Exercise 2, per appendix C:
 *   - there is no GM-users element (users register themselves as clients)
 *   - a GM-event has no <id> child; events are identified by their name
 *
 * The content arrives from an HTTP upload as bytes, so this loader parses a
 * stream rather than a path. Nothing is ever written to the server's disk.
 */
public class Ex3FileLoader {

    private static final String ROOT = "Guess-Market";
    private static final String EVENTS = "GM-events";
    private static final String EVENT = "GM-event";
    private static final String DESC = "description";
    private static final String COMMISSION = "commission";
    private static final String OPTIONS = "GM-options";
    private static final String OPTION = "GM-option";
    private static final String METHOD = "GM-method";
    private static final String LMSR = "GM-LMSR";
    private static final String B = "b";
    private static final String ORDER_BOOK = "GM-order-book";

    /**
     * @param fileName        the uploaded file's name, checked for the .xml extension
     * @param content         the raw bytes of the uploaded file
     * @param existingNames   event names already in the system; a file may not reuse one
     */
    public LoadResult load(String fileName, byte[] content, Set<String> existingNames) {
        List<String> errors = new ArrayList<>();

        if (fileName == null || fileName.trim().isEmpty()) {
            errors.add("No file was supplied.");
            return LoadResult.fail(errors);
        }
        if (!fileName.trim().toLowerCase().endsWith(".xml")) {
            errors.add("The file must have an .xml extension (got '" + fileName + "').");
            return LoadResult.fail(errors);
        }
        if (content == null || content.length == 0) {
            errors.add("The uploaded file is empty.");
            return LoadResult.fail(errors);
        }

        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setIgnoringElementContentWhitespace(true);
            InputStream in = new ByteArrayInputStream(content);
            doc = f.newDocumentBuilder().parse(in);
            doc.getDocumentElement().normalize();
        } catch (Exception e) {
            errors.add("The file could not be parsed as XML (" + e.getMessage() + ").");
            return LoadResult.fail(errors);
        }

        Element root = doc.getDocumentElement();
        if (!ROOT.equals(root.getTagName())) {
            errors.add("Unexpected root element '" + root.getTagName()
                    + "' - expected '" + ROOT + "'. Is this an Exercise 3 file?");
            return LoadResult.fail(errors);
        }

        Element container = first(root, EVENTS);
        if (container == null) {
            errors.add("The file contains no " + EVENTS + " section.");
            return LoadResult.fail(errors);
        }
        List<Element> nodes = childrenNamed(container, EVENT);
        if (nodes.isEmpty()) {
            errors.add("The file does not contain any events.");
            return LoadResult.fail(errors);
        }

        List<GMEvent> events = new ArrayList<>();
        Set<String> inThisFile = new HashSet<>();
        int i = 0;

        for (Element el : nodes) {
            i++;
            String ctx = "Event #" + i + ": ";

            String name = attr(el, "name");
            if (name.isEmpty()) {
                errors.add(ctx + "missing a name attribute.");
                continue;
            }
            String key = name.toLowerCase();
            if (!inThisFile.add(key)) {
                errors.add(ctx + "the name '" + name + "' appears more than once in this file.");
                continue;
            }
            if (existingNames != null && existingNames.contains(key)) {
                errors.add(ctx + "an event named '" + name
                        + "' already exists in the system. Event names must be unique.");
                continue;
            }

            String desc = textChild(el, DESC);
            if (desc == null) desc = "";

            Element comEl = first(el, COMMISSION);
            if (comEl == null) {
                errors.add(ctx + "missing commission element.");
                continue;
            }
            Integer com = parseInt(comEl.getTextContent());
            if (com == null || com < 0 || com > 90) {
                errors.add(ctx + "commission must be a whole number between 0 and 90 (found '"
                        + comEl.getTextContent().trim() + "').");
                continue;
            }
            CommissionType ct = CommissionType.fromXmlValue(attr(comEl, "type"));
            if (ct == null) {
                errors.add(ctx + "commission type must be 'on-close' or 'on-purchase'.");
                continue;
            }

            Element optsEl = first(el, OPTIONS);
            List<String> optNames = new ArrayList<>();
            if (optsEl != null) {
                for (Element o : childrenNamed(optsEl, OPTION)) {
                    String t = o.getTextContent().trim();
                    if (!t.isEmpty()) optNames.add(t);
                }
            }
            if (optNames.size() != 2) {
                errors.add(ctx + "expected exactly 2 options, found " + optNames.size() + ".");
                continue;
            }
            if (optNames.get(0).equalsIgnoreCase(optNames.get(1))) {
                errors.add(ctx + "the two options must have different names.");
                continue;
            }

            Element methodEl = first(el, METHOD);
            if (methodEl == null) {
                errors.add(ctx + "missing " + METHOD + ".");
                continue;
            }
            Element lmsrEl = first(methodEl, LMSR);
            Element obEl = first(methodEl, ORDER_BOOK);

            if (lmsrEl != null) {
                Integer b = intChild(lmsrEl, B);
                if (b == null || b <= 0) {
                    errors.add(ctx + "LMSR 'b' must be a positive whole number.");
                    continue;
                }
                events.add(new LmsrEvent(0, name, desc, com, ct, optNames, b));
            } else if (obEl != null) {
                Integer d = parseInt(attr(obEl, "d"));
                Integer initial = parseInt(attr(obEl, "initial"));
                String mint = attr(obEl, "allow-mint");
                if (d == null || d <= 0) {
                    errors.add(ctx + "order book 'd' must be a positive whole number.");
                    continue;
                }
                if (initial == null || initial < 0) {
                    errors.add(ctx + "order book 'initial' must be a non-negative whole number.");
                    continue;
                }
                if (!mint.equalsIgnoreCase("true") && !mint.equalsIgnoreCase("false")) {
                    errors.add(ctx + "order book 'allow-mint' must be true or false.");
                    continue;
                }
                events.add(new OrderBookEvent(0, name, desc, com, ct, optNames,
                        d, initial, Boolean.parseBoolean(mint.toLowerCase())));
            } else {
                errors.add(ctx + "must define either " + LMSR + " or " + ORDER_BOOK + ".");
            }
        }

        if (!errors.isEmpty()) return LoadResult.fail(errors);
        return LoadResult.ok(events, Collections.emptyList());
    }

    private Element first(Element parent, String tag) {
        for (Element e : children(parent)) if (e.getTagName().equals(tag)) return e;
        return null;
    }
    private List<Element> childrenNamed(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Element e : children(parent)) if (e.getTagName().equals(tag)) out.add(e);
        return out;
    }
    private List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }
    private String attr(Element e, String a) {
        String v = e.getAttribute(a);
        return v == null ? "" : v.trim();
    }
    private String textChild(Element parent, String tag) {
        Element e = first(parent, tag);
        return e == null ? null : e.getTextContent().trim();
    }
    private Integer intChild(Element parent, String tag) { return parseInt(textChild(parent, tag)); }
    private Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
