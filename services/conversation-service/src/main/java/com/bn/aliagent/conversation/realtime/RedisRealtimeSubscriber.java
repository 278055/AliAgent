package com.bn.aliagent.conversation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 每个实例只订阅自己的定向频道；收到后按 connectionId 投递给本地 WebSocket。 */
public final class RedisRealtimeSubscriber implements AutoCloseable {
    private final String host;
    private final int port;
    private final String channel;
    private final RealtimeSessionRegistry sessions;
    private final ObjectMapper json = new ObjectMapper();
    private volatile boolean running = true;
    private Socket socket;

    public RedisRealtimeSubscriber(String host, int port, String instanceId, RealtimeSessionRegistry sessions) {
        this.host = host; this.port = port; this.channel = "conversation:*:instance:" + instanceId + ":events"; this.sessions = sessions;
    }

    public void start() { Thread thread = new Thread(this::listen, "conversation-realtime-subscriber"); thread.setDaemon(true); thread.start(); }
    private void listen() {
        while (running) {
            try (Socket current = new Socket()) {
                socket = current; current.connect(new InetSocketAddress(host, port), 1000);
                BufferedWriter output = new BufferedWriter(new java.io.OutputStreamWriter(current.getOutputStream(), StandardCharsets.UTF_8));
                output.write("*2\r\n$10\r\nPSUBSCRIBE\r\n$" + channel.getBytes(StandardCharsets.UTF_8).length + "\r\n" + channel + "\r\n"); output.flush();
                BufferedReader input = new BufferedReader(new java.io.InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8));
                while (running) {
                    List<String> event = array(input);
                    if (event.size() == 4 && "pmessage".equals(event.get(0))) sessions.send(json.readValue(event.get(3), RealtimeEnvelope.class));
                }
            } catch (Exception ignored) {
                if (running) try { Thread.sleep(250); } catch (InterruptedException interruption) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    private List<String> array(BufferedReader input) throws IOException {
        String header = input.readLine(); if (header == null || !header.startsWith("*")) throw new IOException("Invalid Redis Pub/Sub response");
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int item = 0; item < Integer.parseInt(header.substring(1)); item++) { String lengthLine = input.readLine(); int length = Integer.parseInt(lengthLine.substring(1)); char[] text = new char[length]; int offset = 0; while (offset < length) { int count = input.read(text, offset, length - offset); if (count < 0) throw new IOException("Unexpected Redis response end"); offset += count; } input.readLine(); values.add(new String(text)); }
        return values;
    }
    @Override public void close() { running = false; try { if (socket != null) socket.close(); } catch (IOException ignored) { } }
}
