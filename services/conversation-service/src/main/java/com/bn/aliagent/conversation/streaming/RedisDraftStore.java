package com.bn.aliagent.conversation.streaming;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/** 最小 Redis RESP 适配器在不修改服务 POM 的前提下保持 Redis 可选。 */
public final class RedisDraftStore implements DraftStore {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private final String host;
    private final int port;
    private final int ttlSeconds;
    public RedisDraftStore(String host, int port, int ttlSeconds) { this.host = host; this.port = port; this.ttlSeconds = ttlSeconds; }
    public void save(StreamingModels.Generation generation) { command("SET", draftKey(generation), generation.content(), "EX", Integer.toString(ttlSeconds)); command("SET", checkpointKey(generation), Integer.toString(generation.lastChunkIndex()), "EX", Integer.toString(ttlSeconds)); }
    public void markCancelled(StreamingModels.Generation generation) { command("SET", baseKey(generation) + ":cancelled", "true", "EX", Integer.toString(ttlSeconds)); }
    public Optional<String> load(StreamingModels.Generation generation) { String value = command("GET", draftKey(generation)); return value == null ? Optional.empty() : Optional.of(value); }
    private String draftKey(StreamingModels.Generation value) { return baseKey(value) + ":draft"; }
    private String checkpointKey(StreamingModels.Generation value) { return baseKey(value) + ":checkpoint"; }
    private String baseKey(StreamingModels.Generation value) { return "conversation:" + value.tenantId() + ":" + value.conversationId() + ":generation:" + value.generationId(); }
    private String command(String... parts) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) TIMEOUT.toMillis()); socket.setSoTimeout((int) TIMEOUT.toMillis());
            BufferedWriter out = new BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.write("*" + parts.length + "\r\n"); for (String part : parts) out.write("$" + part.getBytes(StandardCharsets.UTF_8).length + "\r\n" + part + "\r\n"); out.flush();
            BufferedReader in = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); String first = in.readLine();
            if (first == null || first.startsWith("-")) throw new IllegalStateException("Redis command failed");
            if (first.equals("$-1")) return null;
            if (first.startsWith("$")) { int length = Integer.parseInt(first.substring(1)); char[] chars = new char[length]; int read = 0; while (read < length) { int count = in.read(chars, read, length - read); if (count < 0) throw new IOException("Unexpected Redis response end"); read += count; } in.readLine(); return new String(chars); }
            return first.substring(1);
        } catch (IOException exception) { throw new IllegalStateException("Redis unavailable", exception); }
    }
}
