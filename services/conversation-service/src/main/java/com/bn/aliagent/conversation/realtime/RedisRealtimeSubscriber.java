package com.bn.aliagent.conversation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 每个可信租户使用独立的实例定向频道，避免通配订阅跨越租户边界。 */
public final class RedisRealtimeSubscriber implements AutoCloseable {
    private final String host;
    private final int port;
    private final String password;
    private final String instanceId;
    private final RealtimeSessionRegistry sessions;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Set<String> tenants = ConcurrentHashMap.newKeySet();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, Socket> tenantSockets = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public RedisRealtimeSubscriber(String host, int port, String password, String instanceId,
            RealtimeSessionRegistry sessions) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.instanceId = instanceId;
        this.sessions = sessions;
    }

    public void subscribeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || !tenants.add(tenantId)) return;
        Thread thread = new Thread(() -> listen(tenantId), "conversation-realtime-" + tenantId);
        thread.setDaemon(true);
        thread.start();
    }

    int subscribedTenantCount() { return tenants.size(); }
    public void unsubscribeTenant(String tenantId) {
        tenants.remove(tenantId);
        Socket socket = tenantSockets.remove(tenantId);
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
    static String channel(String tenantId, String instanceId) {
        return "conversation:" + tenantId + ":instance:" + instanceId + ":events";
    }

    private void listen(String tenantId) {
        String targetChannel = channel(tenantId, instanceId);
        while (running && tenants.contains(tenantId)) {
            try (Socket socket = new Socket()) {
                sockets.add(socket);
                tenantSockets.put(tenantId, socket);
                socket.connect(new InetSocketAddress(host, port), 1000);
                BufferedWriter output = new BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                DataInputStream input = new DataInputStream(socket.getInputStream());
                if (password != null && !password.isBlank()) {
                    write(output, "AUTH", password);
                    String auth = readLine(input);
                    if (auth == null || auth.startsWith("-")) throw new IllegalStateException("Redis authentication failed");
                }
                write(output, "SUBSCRIBE", targetChannel);
                while (running && tenants.contains(tenantId)) {
                    List<String> event = array(input);
                    if (event.size() == 3 && "message".equals(event.get(0)) && targetChannel.equals(event.get(1))) {
                        RealtimeEnvelope envelope = json.readValue(event.get(2), RealtimeEnvelope.class);
                        if (tenantId.equals(envelope.tenantId())) sessions.send(envelope);
                    }
                }
            } catch (Exception ignored) {
                if (running) try { Thread.sleep(250); } catch (InterruptedException interruption) { Thread.currentThread().interrupt(); return; }
            } finally { tenantSockets.remove(tenantId); sockets.removeIf(Socket::isClosed); }
        }
    }

    private void write(BufferedWriter output, String... parts) throws IOException {
        output.write("*" + parts.length + "\r\n");
        for (String part : parts) output.write("$" + part.getBytes(StandardCharsets.UTF_8).length + "\r\n" + part + "\r\n");
        output.flush();
    }

    private List<String> array(DataInputStream input) throws IOException {
        String header = readLine(input);
        if (header == null || !header.startsWith("*")) throw new IOException("Invalid Redis Pub/Sub response");
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int item = 0; item < Integer.parseInt(header.substring(1)); item++) {
            String lengthLine = readLine(input);
            if (lengthLine.startsWith(":")) { values.add(lengthLine.substring(1)); continue; }
            if (!lengthLine.startsWith("$")) throw new IOException("Unsupported Redis Pub/Sub response");
            int length = Integer.parseInt(lengthLine.substring(1));
            byte[] text = input.readNBytes(length);
            if (text.length != length) throw new IOException("Unexpected Redis response end");
            if (input.readByte() != '\r' || input.readByte() != '\n') throw new IOException("Invalid Redis bulk ending");
            values.add(new String(text, StandardCharsets.UTF_8));
        }
        return values;
    }

    private String readLine(DataInputStream input) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = input.read();
            if (current < 0) throw new IOException("Unexpected Redis response end");
            if (previous == '\r' && current == '\n') break;
            if (previous >= 0) bytes.write(previous);
            previous = current;
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Override public void close() {
        running = false;
        sockets.forEach(socket -> { try { socket.close(); } catch (IOException ignored) { } });
        sockets.clear();
        tenantSockets.clear();
        tenants.clear();
    }
}
