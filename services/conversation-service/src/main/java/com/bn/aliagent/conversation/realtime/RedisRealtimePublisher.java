package com.bn.aliagent.conversation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class RedisRealtimePublisher implements RealtimePublisher {
    private final String host;
    private final int port;
    private final ObjectMapper json = new ObjectMapper();
    public RedisRealtimePublisher(String host, int port) { this.host = host; this.port = port; }
    @Override public void publish(String channel, RealtimeEnvelope envelope) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000); socket.setSoTimeout(1000);
            String payload = json.writeValueAsString(envelope);
            BufferedWriter output = new BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            output.write("*3\r\n$7\r\nPUBLISH\r\n$" + channel.getBytes(StandardCharsets.UTF_8).length + "\r\n" + channel + "\r\n$" + payload.getBytes(StandardCharsets.UTF_8).length + "\r\n" + payload + "\r\n"); output.flush();
            String result = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
            if (result == null || result.startsWith("-")) throw new IllegalStateException("Redis publish failed");
        } catch (IOException exception) { throw new IllegalStateException("Redis unavailable", exception); }
    }
}
