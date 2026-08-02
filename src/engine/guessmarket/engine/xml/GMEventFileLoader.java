package guessmarket.engine.xml;

import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.GMEvent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses and validates a Guess Market Ex1 XML events file (DOM based,
 * no external libraries needed).
 *
 * Tag names below are confirmed against the official course files:
 * GM-EX1-Schema.xsd and the single.xml / error-2.xml / error-3.xml samples
 * from Mama (קבצי בדיקה -> EX 1). The 0-90 commission range is NOT enforced
 * by the XSD (comision is a plain unrestricted xs:int there) - it's a
 * business rule from the assignment text, which error-3.xml (comision=115)
 * is specifically designed to test. Validated below in the application
 * layer, not via schema validation.
 */
public class GMEventFileLoader {

    private static final String ROOT_TAG = "Guess-Market";
    private static final String EVENTS_TAG = "GM-events";
    private static final String EVENT_TAG = "GM-event";
    private static final String NAME_ATTR = "name";
    private static final String ID_TAG = "id";
    private static final String DESC_TAG = "description";
    private static final String COMMISSION_TAG = "comision"; // spelled this way in the spec table
    private static final String COMMISSION_TYPE_ATTR = "type";
    private static final String OPTIONS_TAG = "GM-options";
    private static final String OPTION_TAG = "GM-option";
    private static final String METHOD_TAG = "GM-method";
    private static final String LMSR_TAG = "GM-LMSR";
    private static final String B_TAG = "b";

    public EventFileValidationResult load(String path) {
        List<String> errors = new ArrayList<>();

        if (path == null || path.trim().isEmpty()) {
            errors.add("No file path was provided.");
            return EventFileValidationResult.failure(errors);
        }
        String trimmedPath = path.trim();

        if (!trimmedPath.toLowerCase().endsWith(".xml")) {
            errors.add("The file must have an .xml extension.");
            return EventFileValidationResult.failure(errors);
        }

        File file = new File(trimmedPath);
        if (!file.exists() || !file.isFile()) {
            errors.add("The file '" + trimmedPath + "' does not exist.");
            return EventFileValidationResult.failure(errors);
        }

        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(file);
            doc.getDocumentElement().normalize();
        } catch (Exception e) {
            errors.add("The file could not be parsed as XML (" + e.getMessage() + ").");
            return EventFileValidationResult.failure(errors);
        }

        Element root = doc.getDocumentElement();
        if (!ROOT_TAG.equals(root.getTagName())) {
            errors.add("Unexpected root element '" + root.getTagName()
                    + "' - expected '" + ROOT_TAG + "'. Is this the right file?");
            return EventFileValidationResult.failure(errors);
        }
        Element eventsContainer = firstChild(root, EVENTS_TAG);
        NodeList eventNodes = eventsContainer != null
                ? eventsContainer.getElementsByTagName(EVENT_TAG)
                : root.getElementsByTagName(EVENT_TAG);

        if (eventNodes.getLength() == 0) {
            errors.add("The file does not contain any events.");
            return EventFileValidationResult.failure(errors);
        }

        List<GMEvent> events = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        for (int i = 0; i < eventNodes.getLength(); i++) {
            Element eventEl = (Element) eventNodes.item(i);
            String context = "Event #" + (i + 1) + ": ";

            String name = eventEl.getAttribute(NAME_ATTR);
            if (name == null || name.trim().isEmpty()) {
                errors.add(context + "missing a name attribute.");
                continue;
            }
            name = name.trim();

            Integer id = readIntChild(eventEl, ID_TAG);
            if (id == null) {
                errors.add(context + "missing or non-numeric 'id'.");
                continue;
            }
            if (!seenIds.add(id)) {
                errors.add(context + "duplicate event id (" + id + "). Every event needs a unique id.");
                continue;
            }

            String description = readTextChild(eventEl, DESC_TAG);
            if (description == null) description = "";

            Element commissionEl = firstChild(eventEl, COMMISSION_TAG);
            if (commissionEl == null) {
                errors.add(context + "missing commission element.");
                continue;
            }
            Integer commissionPercent = parseIntSafe(commissionEl.getTextContent());
            if (commissionPercent == null || commissionPercent < 0 || commissionPercent > 90) {
                errors.add(context + "commission must be a whole number between 0 and 90 (found: '"
                        + commissionEl.getTextContent().trim() + "').");
                continue;
            }
            String typeStr = commissionEl.getAttribute(COMMISSION_TYPE_ATTR);
            CommissionType commissionType = CommissionType.fromXmlValue(typeStr);
            if (commissionType == null) {
                errors.add(context + "commission type must be 'on-close' or 'on-purchase' (found: '"
                        + typeStr + "').");
                continue;
            }

            Element optionsEl = firstChild(eventEl, OPTIONS_TAG);
            List<String> optionNames = new ArrayList<>();
            if (optionsEl != null) {
                NodeList optionNodes = optionsEl.getElementsByTagName(OPTION_TAG);
                for (int j = 0; j < optionNodes.getLength(); j++) {
                    String optName = optionNodes.item(j).getTextContent().trim();
                    if (!optName.isEmpty()) optionNames.add(optName);
                }
            }
            if (optionNames.size() != 2) {
                errors.add(context + "expected exactly 2 options, found " + optionNames.size() + ".");
                continue;
            }

            Element methodEl = firstChild(eventEl, METHOD_TAG);
            Element lmsrEl = methodEl == null ? null : firstChild(methodEl, LMSR_TAG);
            if (lmsrEl == null) {
                errors.add(context + "missing LMSR method definition.");
                continue;
            }
            Integer b = readIntChild(lmsrEl, B_TAG);
            if (b == null || b <= 0) {
                errors.add(context + "LMSR liquidity parameter 'b' must be a positive whole number.");
                continue;
            }

            events.add(new GMEvent(id, name, description, commissionPercent, commissionType, optionNames, b));
        }

        if (!errors.isEmpty()) {
            return EventFileValidationResult.failure(errors);
        }
        return EventFileValidationResult.success(events);
    }

    private Element firstChild(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() == 0 ? null : (Element) nl.item(0);
    }

    private String readTextChild(Element parent, String tag) {
        Element el = firstChild(parent, tag);
        return el == null ? null : el.getTextContent().trim();
    }

    private Integer readIntChild(Element parent, String tag) {
        return parseIntSafe(readTextChild(parent, tag));
    }

    private Integer parseIntSafe(String text) {
        if (text == null) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
