package guessmarket.server;

import com.google.gson.Gson;
import guessmarket.engine.dto.Dtos;
import guessmarket.engine.dto.StateMapper;
import guessmarket.engine.model.GMEvent;
import guessmarket.engine.model.OrderSide;
import guessmarket.engine.service.GuessMarketEngine;
import guessmarket.engine.service.GuessMarketException;
import guessmarket.engine.xml.Ex3FileLoader;
import guessmarket.engine.xml.LoadResult;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * The whole server API, dispatched on the path after /api/.
 *
 * One servlet rather than a dozen: every endpoint needs the same engine
 * lookup, the same "who is calling" resolution and the same JSON error
 * handling, so keeping them together removes a lot of repetition and a lot of
 * places where a mapping could be mis-declared.
 *
 * Endpoints:
 *   POST /api/login       name=...
 *   POST /api/logout
 *   GET  /api/version                       -> cheap poll
 *   GET  /api/state                         -> everything this client can see
 *   POST /api/upload      multipart "file"  -> adds events, uploader becomes MM
 *   POST /api/deposit     amount=...
 *   POST /api/openEvent   event=...
 *   POST /api/closeEvent  event=...&winner=...
 *   POST /api/lmsrBuy     event=...&option=...&quantity=...
 *   POST /api/placeOrder  event=...&option=...&side=BUY|SELL&price=...&quantity=...
 */
@WebServlet(name = "ApiServlet", urlPatterns = {"/api/*"})
@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 10 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024)
public class ApiServlet extends HttpServlet {

    private static final String SESSION_USER = "gm.user";
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (path.startsWith("/")) path = path.substring(1);

        GuessMarketEngine engine = EngineContextListener.get(getServletContext());

        try {
            switch (path) {
                case "login":      login(req, resp, engine);        break;
                case "logout":     logout(req, resp);               break;
                case "version":    version(resp, engine);           break;
                case "state":      state(req, resp, engine);        break;
                case "upload":     upload(req, resp, engine);       break;
                case "deposit":    deposit(req, resp, engine);      break;
                case "openEvent":  openEvent(req, resp, engine);    break;
                case "closeEvent": closeEvent(req, resp, engine);   break;
                case "lmsrBuy":    lmsrBuy(req, resp, engine);      break;
                case "placeOrder": placeOrder(req, resp, engine);   break;
                default:
                    write(resp, Dtos.ApiResult.failure("Unknown endpoint: /api/" + path));
            }
        } catch (GuessMarketException e) {
            // a rule the user broke - expected, and reported as a clean message
            write(resp, Dtos.ApiResult.failure(e.getMessage()));
        } catch (Exception e) {
            // anything unforeseen still leaves the client with usable JSON
            write(resp, Dtos.ApiResult.failure("Server error: " + e));
        }
    }

    // ---------------- endpoints ----------------

    private void login(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        String name = param(req, "name");
        if (name == null || name.trim().isEmpty()) {
            write(resp, Dtos.ApiResult.failure("Please enter a user name."));
            return;
        }
        engine.registerUser(name);          // throws if the name is taken
        req.getSession(true).setAttribute(SESSION_USER, name.trim());
        write(resp, Dtos.ApiResult.success("Welcome, " + name.trim() + "!"));
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        write(resp, Dtos.ApiResult.success("Logged out."));
    }

    private void version(HttpServletResponse resp, GuessMarketEngine engine) throws IOException {
        write(resp, new Dtos.VersionDto(engine.getVersion()));
    }

    private void state(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        write(resp, StateMapper.buildState(engine, currentUser(req)));
    }

    private void upload(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws Exception {
        String user = requireUser(req);
        Part part = req.getPart("file");
        if (part == null) {
            write(resp, Dtos.ApiResult.failure("No file was included in the request."));
            return;
        }
        String fileName = part.getSubmittedFileName();

        // The content is read straight into memory and never written to disk:
        // the grader's server has no rights to write files.
        byte[] bytes;
        try (InputStream in = part.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }

        LoadResult r = new Ex3FileLoader().load(fileName, bytes, engine.getEventNamesLower());
        if (!r.isValid()) {
            write(resp, Dtos.ApiResult.failure(
                    "The file '" + fileName + "' is not valid and was not loaded.", r.getErrors()));
            return;
        }
        engine.addEvents(r.getEvents(), user);
        write(resp, Dtos.ApiResult.success(
                "Loaded " + r.getEvents().size() + " event(s) from '" + fileName
                        + "'. You are now the market maker of " + (r.getEvents().size() == 1 ? "it." : "them.")));
    }

    private void deposit(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        String user = requireUser(req);
        Double amount = parseDouble(param(req, "amount"));
        if (amount == null) {
            write(resp, Dtos.ApiResult.failure("Deposit amount must be a number."));
            return;
        }
        engine.deposit(user, amount);
        write(resp, Dtos.ApiResult.success("Deposited " + String.format("%.2f", amount) + "."));
    }

    private void openEvent(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        String user = requireUser(req);
        engine.openEvent(requireParam(req, "event"), user);
        write(resp, Dtos.ApiResult.success("Event opened."));
    }

    private void closeEvent(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        String user = requireUser(req);
        engine.closeEvent(requireParam(req, "event"), user, requireParam(req, "winner"));
        write(resp, Dtos.ApiResult.success("Event closed."));
    }

    private void lmsrBuy(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        String user = requireUser(req);
        Double qty = parseDouble(param(req, "quantity"));
        if (qty == null) {
            write(resp, Dtos.ApiResult.failure("Quantity must be a number."));
            return;
        }
        engine.lmsrBuy(requireParam(req, "event"), user, requireParam(req, "option"), qty);
        write(resp, Dtos.ApiResult.success("Purchase completed."));
    }

    private void placeOrder(HttpServletRequest req, HttpServletResponse resp, GuessMarketEngine engine)
            throws IOException {
        String user = requireUser(req);
        Double qty = parseDouble(param(req, "quantity"));
        Double price = parseDouble(param(req, "price"));
        if (qty == null || price == null) {
            write(resp, Dtos.ApiResult.failure("Quantity and price must both be numbers."));
            return;
        }
        String sideStr = requireParam(req, "side");
        OrderSide side;
        try {
            side = OrderSide.valueOf(sideStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            write(resp, Dtos.ApiResult.failure("Side must be BUY or SELL."));
            return;
        }
        engine.placeOrder(requireParam(req, "event"), user, side,
                requireParam(req, "option"), price, qty);
        write(resp, Dtos.ApiResult.success("Order submitted."));
    }

    // ---------------- helpers ----------------

    /**
     * Who is calling. The session is the normal answer; a "user" parameter is
     * accepted as a fallback so the API stays usable from a tool like Postman,
     * which the exercise recommends for checking the server on its own.
     */
    private String currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) {
            Object v = s.getAttribute(SESSION_USER);
            if (v != null) return (String) v;
        }
        String p = param(req, "user");
        return (p == null || p.trim().isEmpty()) ? null : p.trim();
    }

    private String requireUser(HttpServletRequest req) {
        String u = currentUser(req);
        if (u == null) throw new GuessMarketException("You are not logged in. Please log in again.");
        return u;
    }

    private String param(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v;
    }

    private String requireParam(HttpServletRequest req, String name) {
        String v = param(req, name);
        if (v == null || v.trim().isEmpty())
            throw new GuessMarketException("Missing required parameter: " + name);
        return v.trim();
    }

    private Double parseDouble(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private void write(HttpServletResponse resp, Object body) throws IOException {
        PrintWriter w = resp.getWriter();
        w.write(gson.toJson(body));
        w.flush();
    }
}
