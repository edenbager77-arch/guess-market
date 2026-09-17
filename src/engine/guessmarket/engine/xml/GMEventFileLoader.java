package guessmarket.engine.xml;

import guessmarket.engine.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/**
 * Loader/validator for the Ex2 XML format.
 *
 * Tag names verified against GM-EX2-Schema.xsd from the course.
 * NOTE: unlike Ex1 (which used the misspelled "comision"), Ex2 spells the
 * element "commission" correctly. Everything else that carried over kept
 * its Ex1 spelling.
 */
public class GMEventFileLoader {

    private static final String ROOT = "Guess-Market";
    private static final String EVENTS = "GM-events";
    private static final String EVENT = "GM-event";
    private static final String ID = "id";
    private static final String DESC = "description";
    private static final String COMMISSION = "commission";
    private static final String OPTIONS = "GM-options";
    private static final String OPTION = "GM-option";
    private static final String METHOD = "GM-method";
    private static final String LMSR = "GM-LMSR";
    private static final String B = "b";
    private static final String ORDER_BOOK = "GM-order-book";
    private static final String USERS = "GM-users";
    private static final String USER = "GM-user";
    private static final String INITIAL_CASH = "initial-cash";
    private static final String MARKET_MAKER = "GM-market-maker";
    private static final String MM_EVENT = "event";

    public LoadResult load(String path) {
        List<String> errors = new ArrayList<>();
        if (path == null || path.trim().isEmpty()) {
            errors.add("No file path was provided.");
            return LoadResult.fail(errors);
        }
        String p = path.trim();
        if (!p.toLowerCase().endsWith(".xml")) {
            errors.add("The file must have an .xml extension.");
            return LoadResult.fail(errors);
        }
        File f = new File(p);
        if (!f.exists() || !f.isFile()) {
            errors.add("The file '" + p + "' does not exist.");
            return LoadResult.fail(errors);
        }

        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringElementContentWhitespace(true);
            doc = factory.newDocumentBuilder().parse(f);
            doc.getDocumentElement().normalize();
        } catch (Exception e) {
            errors.add("The file could not be parsed as XML (" + e.getMessage() + ").");
            return LoadResult.fail(errors);
        }

        Element root = doc.getDocumentElement();
        if (!ROOT.equals(root.getTagName())) {
            errors.add("Unexpected root element '" + root.getTagName() + "' - expected '" + ROOT + "'.");
            return LoadResult.fail(errors);
        }

        List<GMEvent> events = parseEvents(root, errors);
        List<User> users = parseUsers(root, errors);

        if (errors.isEmpty()) crossValidate(events, users, errors);

        return errors.isEmpty() ? LoadResult.ok(events, users) : LoadResult.fail(errors);
    }

    // ---------- events ----------

    private List<GMEvent> parseEvents(Element root, List<String> errors) {
        List<GMEvent> events = new ArrayList<>();
        Element container = first(root, EVENTS);
        if (container == null) {
            errors.add("The file contains no " + EVENTS + " section.");
            return events;
        }
        List<Element> nodes = childrenNamed(container, EVENT);
        if (nodes.isEmpty()) {
            errors.add("The file does not contain any events.");
            return events;
        }

        Set<Integer> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        int i = 0;
        for (Element el : nodes) {
            i++;
            String ctx = "Event #" + i + ": ";
            String name = attr(el, "name");
            if (name.isEmpty()) { errors.add(ctx + "missing a name attribute."); continue; }
            if (!seenNames.add(name.toLowerCase())) {
                errors.add(ctx + "duplicate event name '" + name + "'."); continue;
            }
            Integer id = intChild(el, ID);
            if (id == null) { errors.add(ctx + "missing or non-numeric 'id'."); continue; }
            if (!seenIds.add(id)) { errors.add(ctx + "duplicate event id (" + id + ")."); continue; }

            String desc = textChild(el, DESC);
            if (desc == null) desc = "";

            Element comEl = first(el, COMMISSION);
            if (comEl == null) { errors.add(ctx + "missing commission element."); continue; }
            Integer com = parseInt(comEl.getTextContent());
            if (com == null || com < 0 || com > 90) {
                errors.add(ctx + "commission must be a whole number between 0 and 90 (found '"
                        + comEl.getTextContent().trim() + "')."); continue;
            }
            CommissionType ct = CommissionType.fromXmlValue(attr(comEl, "type"));
            if (ct == null) {
                errors.add(ctx + "commission type must be 'on-close' or 'on-purchase'."); continue;
            }

            Element optsEl = first(el, OPTIONS);
            List<String> optNames = new ArrayList<>();
            if (optsEl != null)
                for (Element o : childrenNamed(optsEl, OPTION)) {
                    String t = o.getTextContent().trim();
                    if (!t.isEmpty()) optNames.add(t);
                }
            if (optNames.size() != 2) {
                errors.add(ctx + "expected exactly 2 options, found " + optNames.size() + "."); continue;
            }
            if (optNames.get(0).equalsIgnoreCase(optNames.get(1))) {
                errors.add(ctx + "the two options must have different names."); continue;
            }

            Element methodEl = first(el, METHOD);
            if (methodEl == null) { errors.add(ctx + "missing " + METHOD + "."); continue; }

            Element lmsrEl = first(methodEl, LMSR);
            Element obEl = first(methodEl, ORDER_BOOK);

            if (lmsrEl != null) {
                Integer b = intChild(lmsrEl, B);
                if (b == null || b <= 0) {
                    errors.add(ctx + "LMSR 'b' must be a positive whole number."); continue;
                }
                events.add(new LmsrEvent(id, name, desc, com, ct, optNames, b));
            } else if (obEl != null) {
                Integer d = parseInt(attr(obEl, "d"));
                Integer initial = parseInt(attr(obEl, "initial"));
                String mintStr = attr(obEl, "allow-mint");
                if (d == null || d <= 0) {
                    errors.add(ctx + "order book 'd' must be a positive whole number."); continue;
                }
                if (initial == null || initial < 0) {
                    errors.add(ctx + "order book 'initial' must be a non-negative whole number."); continue;
                }
                if (!mintStr.equalsIgnoreCase("true") && !mintStr.equalsIgnoreCase("false")) {
                    errors.add(ctx + "order book 'allow-mint' must be true or false."); continue;
                }
                events.add(new OrderBookEvent(id, name, desc, com, ct, optNames,
                        d, initial, Boolean.parseBoolean(mintStr.toLowerCase())));
            } else {
                errors.add(ctx + "must define either " + LMSR + " or " + ORDER_BOOK + ".");
            }
        }
        return events;
    }

    // ---------- users ----------

    private static class MmRef { final String user; final int eventId;
        MmRef(String u, int e) { user = u; eventId = e; } }

    private final List<MmRef> mmRefs = new ArrayList<>();

    private List<User> parseUsers(Element root, List<String> errors) {
        mmRefs.clear();
        List<User> users = new ArrayList<>();
        Element container = first(root, USERS);
        if (container == null) {
            errors.add("The file contains no " + USERS + " section.");
            return users;
        }
        List<Element> nodes = childrenNamed(container, USER);
        if (nodes.isEmpty()) {
            errors.add("The file does not contain any users.");
            return users;
        }

        Set<String> seen = new HashSet<>();
        int i = 0;
        for (Element el : nodes) {
            i++;
            String ctx = "User #" + i + ": ";
            String name = attr(el, "name");
            if (name.isEmpty()) { errors.add(ctx + "missing a name attribute."); continue; }
            if (!seen.add(name.toLowerCase())) {
                errors.add(ctx + "duplicate user name '" + name + "'. Every user needs a unique name.");
                continue;
            }
            Integer cash = intChild(el, INITIAL_CASH);
            if (cash == null) { errors.add(ctx + "missing or non-numeric " + INITIAL_CASH + "."); continue; }
            if (cash <= 0) {
                errors.add(ctx + "'" + name + "' has an initial cash of " + cash
                        + "; it must be greater than 0.");
                continue;
            }
            users.add(new User(name, cash));

            Element mm = first(el, MARKET_MAKER);
            if (mm != null)
                for (Element ev : childrenNamed(mm, MM_EVENT)) {
                    Integer refId = parseInt(attr(ev, "id"));
                    if (refId == null) {
                        errors.add(ctx + "'" + name + "' has a market-maker entry with a missing/invalid event id.");
                    } else {
                        mmRefs.add(new MmRef(name, refId));
                    }
                }
        }
        return users;
    }

    // ---------- cross validation ----------

    private void crossValidate(List<GMEvent> events, List<User> users, List<String> errors) {
        Map<Integer, GMEvent> byId = new LinkedHashMap<>();
        for (GMEvent e : events) byId.put(e.getId(), e);
        Map<String, User> byName = new LinkedHashMap<>();
        for (User u : users) byName.put(u.getName(), u);

        Map<Integer, List<String>> mmPerEvent = new LinkedHashMap<>();

        for (MmRef r : mmRefs) {
            GMEvent e = byId.get(r.eventId);
            if (e == null) {
                errors.add("User '" + r.user + "' is market maker for event id " + r.eventId
                        + ", but no such event exists in the file.");
                continue;
            }
            mmPerEvent.computeIfAbsent(r.eventId, k -> new ArrayList<>()).add(r.user);
        }
        if (!errors.isEmpty()) return;

        for (GMEvent e : events) {
            List<String> mms = mmPerEvent.getOrDefault(e.getId(), Collections.emptyList());
            if (mms.isEmpty()) {
                errors.add("Event '" + e.getName() + "' (id " + e.getId()
                        + ") has no market maker assigned to it.");
            } else if (mms.size() > 1) {
                errors.add("Event '" + e.getName() + "' (id " + e.getId()
                        + ") has more than one market maker: " + String.join(", ", mms) + ".");
            } else {
                e.setMarketMakerName(mms.get(0));
                User u = byName.get(mms.get(0));
                if (u != null) u.addMmEvent(e.getName());
            }
        }
    }

    // ---------- dom helpers ----------

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
