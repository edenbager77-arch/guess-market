package guessmarket.client;

import com.google.gson.Gson;
import guessmarket.engine.dto.Dtos;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything this client knows about talking to the server.
 *
 * The base URL is fixed to the local Tomcat and the context path of the
 * submitted WAR, as the exercise allows. A CookieManager is installed so the
 * session established at login is carried on every later call, which is how
 * the server knows who is acting.
 */
public class ServerClient {

    public static final String BASE = "http://localhost:8080/guess-market/api";

    private final HttpClient http;
    private final Gson gson = new Gson();

    public ServerClient() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ---------------- calls ----------------

    public Dtos.ApiResult login(String name) throws IOException, InterruptedException {
        return postForm("/login", form("name", name), Dtos.ApiResult.class);
    }

    public Dtos.ApiResult logout() throws IOException, InterruptedException {
        return postForm("/logout", form(), Dtos.ApiResult.class);
    }

    public long version() throws IOException, InterruptedException {
        return get("/version", Dtos.VersionDto.class).version;
    }

    public Dtos.StateDto state() throws IOException, InterruptedException {
        return get("/state", Dtos.StateDto.class);
    }

    public Dtos.ApiResult deposit(double amount) throws IOException, InterruptedException {
        return postForm("/deposit", form("amount", String.valueOf(amount)), Dtos.ApiResult.class);
    }

    public Dtos.ApiResult openEvent(String event) throws IOException, InterruptedException {
        return postForm("/openEvent", form("event", event), Dtos.ApiResult.class);
    }

    public Dtos.ApiResult closeEvent(String event, String winner)
            throws IOException, InterruptedException {
        return postForm("/closeEvent", form("event", event, "winner", winner), Dtos.ApiResult.class);
    }

    public Dtos.ApiResult lmsrBuy(String event, String option, double quantity)
            throws IOException, InterruptedException {
        return postForm("/lmsrBuy",
                form("event", event, "option", option, "quantity", String.valueOf(quantity)),
                Dtos.ApiResult.class);
    }

    public Dtos.ApiResult placeOrder(String event, String option, String side,
                                     double price, double quantity)
            throws IOException, InterruptedException {
        return postForm("/placeOrder",
                form("event", event, "option", option, "side", side,
                        "price", String.valueOf(price), "quantity", String.valueOf(quantity)),
                Dtos.ApiResult.class);
    }

    /** Uploads an events file as multipart/form-data, built by hand - no third-party library. */
    public Dtos.ApiResult upload(File file) throws IOException, InterruptedException {
        String boundary = "----GuessMarket" + System.currentTimeMillis();
        byte[] body = multipartBody(boundary, file);

        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return parse(resp.body(), Dtos.ApiResult.class);
    }

    private byte[] multipartBody(String boundary, File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: application/xml\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(Files.readAllBytes(file.toPath()));
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    // ---------------- plumbing ----------------

    private <T> T get(String path, Class<T> type) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return parse(resp.body(), type);
    }

    private <T> T postForm(String path, Map<String, String> fields, Class<T> type)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return parse(resp.body(), type);
    }

    private <T> T parse(String body, Class<T> type) throws IOException {
        try {
            T v = gson.fromJson(body, type);
            if (v == null) throw new IOException("Empty response from the server.");
            return v;
        } catch (com.google.gson.JsonSyntaxException e) {
            // a Tomcat error page rather than our JSON - say so in plain words
            throw new IOException("The server returned an unexpected response. "
                    + "Is the Guess Market WAR deployed at /guess-market ?");
        }
    }

    private Map<String, String> form(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
