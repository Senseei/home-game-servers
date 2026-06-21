package com.senseei.launcher.adapter.ark;

import com.senseei.launcher.application.port.WorkshopClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorkshopClient via Steam's public GetPublishedFileDetails endpoint (form-POST,
 * no API key). Titles are pulled out with a small regex so the project keeps a
 * zero-dependency JSON story (the response is tiny and well-formed).
 */
public final class SteamWorkshopClient implements WorkshopClient {

    private static final String URL =
            "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/";
    private static final Pattern ITEM = Pattern.compile(
            "\"publishedfileid\"\\s*:\\s*\"(\\d+)\".*?\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
            Pattern.DOTALL);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public Map<String, String> titles(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        StringBuilder form = new StringBuilder("itemcount=").append(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            form.append("&publishedfileids%5B").append(i).append("%5D=")
                    .append(URLEncoder.encode(ids.get(i), StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        String body;
        try {
            body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }

        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ITEM.matcher(body);
        while (m.find()) {
            out.put(m.group(1), unescape(m.group(2)));
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\/", "/").replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\");
    }
}
