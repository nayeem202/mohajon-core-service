package com.haalkhata.feature.assistant.application;

import com.haalkhata.core.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Generates speech audio for assistant answers.
 *
 * <p>Primary: Microsoft Edge's online neural TTS ("bn-BD-NabanitaNeural", a
 * natural female Bangla voice) via Microsoft's Speech websocket. This is the
 * same free quality consumers get from Edge/Translate and is far more
 * human-sounding than any device's built-in TTS.
 *
 * <p>Fallback: Google Translate's endpoint (also a female Bangla voice) if the
 * websocket service is unreachable. Returns one or more MP3 segments (long
 * answers are chunked on sentence boundaries).
 */
@Component
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private static final String EDGE_WS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String EDGE_VERSION = "1-143.0.3650.75";
    private static final String VOICE = "bn-BD-NabanitaNeural";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final String TTS_URL =
            "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=bn&q=";
    private static final int MAX_CHUNK = 180;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE MMM d yyyy HH:mm:ss").withZone(ZoneOffset.UTC);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    /** Synthesizes [text] into one or more MP3 segments. */
    public List<byte[]> synthesize(String text) {
        if (text == null || text.isBlank()) return List.of();
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 2000) {
            cleaned = cleaned.substring(0, 2000).trim();
        }
        List<String> chunks = chunk(cleaned, MAX_CHUNK);
        List<byte[]> segments = new ArrayList<>();

        Boolean edgeEnabled = null;
        for (String chunk : chunks) {
            if (edgeEnabled == null) {
                edgeEnabled = tryEdge(chunk, segments);
            } else if (edgeEnabled) {
                if (!tryEdge(chunk, segments)) {
                    tryGoogle(chunk, segments);
                }
            } else {
                tryGoogle(chunk, segments);
            }
        }
        return segments;
    }

    private boolean tryEdge(String chunk, List<byte[]> segments) {
        try {
            byte[] audio = edgeMp3(chunk);
            if (audio != null && audio.length > 64) {
                segments.add(audio);
                return true;
            }
        } catch (Exception ex) {
            log.warn("Edge TTS failed ({}): {}", chunk.length() > 40 ? chunk.substring(0, 40) : chunk, ex.toString());
        }
        return false;
    }

    private void tryGoogle(String chunk, List<byte[]> segments) {
        try {
            byte[] audio = googleMp3(chunk);
            if (audio != null && audio.length > 0) segments.add(audio);
        } catch (Exception ex) {
            log.warn("Google TTS failed: {}", ex.toString());
        }
    }

    /* ---------- Microsoft Edge neural TTS ---------- */

    private byte[] edgeMp3(String text) throws Exception {
        String token = edgeToken();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String connId = UUID.randomUUID().toString().replace("-", "");
        String url = EDGE_WS_URL
                + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN
                + "&Sec-MS-GEC=" + token
                + "&Sec-MS-GEC-Version=" + EDGE_VERSION
                + "&ConnectionId=" + connId;

        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        CompletableFuture<byte[]> done = new CompletableFuture<>();

        WebSocket ws = http.newWebSocketBuilder()
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", USER_AGENT)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "en-US,en;q=0.9")
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.sendText(
                                "X-Timestamp:" + timestamp() + "\r\n"
                                        + "Content-Type:application/json; charset=utf-8\r\n"
                                        + "Path:speech.config\r\n\r\n"
                                        + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":"
                                        + "{\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"},"
                                        + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n",
                                true);
                        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                                + "<voice name='" + VOICE + "'>"
                                + "<prosody pitch='+0Hz' rate='+8%' volume='+0%'>"
                                + xmlEscape(text)
                                + "</prosody></voice></speak>";
                        webSocket.sendText(
                                "X-RequestId:" + requestId + "\r\n"
                                        + "Content-Type:application/ssml+xml\r\n"
                                        + "X-Timestamp:" + timestamp() + "Z\r\n"
                                        + "Path:ssml\r\n\r\n"
                                        + ssml,
                                true);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String s = data.toString();
                        if (s.contains("turn.end")) {
                            done.complete(audio.toByteArray());
                            webSocket.sendClose(1000, "done");
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        byte[] b = new byte[data.remaining()];
                        data.get(b);
                        if (b.length > 2) {
                            int hdrSize = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
                            if (hdrSize + 2 <= b.length) {
                                audio.write(b, hdrSize + 2, b.length - hdrSize - 2);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        done.complete(audio.toByteArray());
                        return null;
                    }
                })
                .join();
        try {
            byte[] result = done.get(25, TimeUnit.SECONDS);
            return result;
        } catch (Exception ex) {
            try {
                ws.sendClose(1000, "abort");
            } catch (Exception ignored) {
            }
            throw ex;
        }
    }

    static String edgeToken() {
        double now = System.currentTimeMillis() / 1000.0;
        double ticks = now + 11644473600.0;
        ticks -= ticks % 300.0;
        ticks *= 1e7;
        return sha256Hex(String.format("%.0f%s", ticks, TRUSTED_CLIENT_TOKEN));
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest(raw.getBytes(StandardCharsets.US_ASCII))) {
                sb.append(String.format("%02X", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String timestamp() {
        return DATE_FMT.format(Instant.now()) + " GMT+0000 (Coordinated Universal Time)";
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /* ---------- Google Translate TTS fallback ---------- */

    private byte[] googleMp3(String chunk) throws Exception {
        String q = URLEncoder.encode(chunk, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TTS_URL + q))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://translate.google.com/")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
            return null;
        }
        return response.body();
    }

    /** Splits [text] into chunks no longer than [max] chars on sentence boundaries. */
    static List<String> chunk(String text, int max) {
        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String sentence : text.split("(?<=[।.!?؟])")) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;
            if (buf.length() + s.length() <= max) {
                if (buf.length() > 0) buf.append(' ');
                buf.append(s);
                continue;
            }
            if (buf.length() > 0) {
                result.add(buf.toString().trim());
                buf.setLength(0);
            }
            if (s.length() <= max) {
                buf.append(s);
            } else {
                for (String word : s.split(" ")) {
                    if (buf.length() + word.length() + 1 > max && buf.length() > 0) {
                        result.add(buf.toString().trim());
                        buf.setLength(0);
                    }
                    if (buf.length() > 0) buf.append(' ');
                    buf.append(word);
                }
            }
        }
        if (buf.length() > 0) result.add(buf.toString().trim());
        return result;
    }
}