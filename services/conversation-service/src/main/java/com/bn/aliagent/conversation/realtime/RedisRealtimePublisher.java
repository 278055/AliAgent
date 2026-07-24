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
    private final String password;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public RedisRealtimePublisher(String host, int port, String password) { this.host = host; this.port = port; this.password = password; }
    public RedisRealtimePublisher(String host, int port) { this(host, port, ""); }
    @Override public void publish(String channel, RealtimeEnvelope envelope) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000); socket.setSoTimeout(1000);
            String payload = json.writeValueAsString(envelope);
            BufferedWriter output = new BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader input = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            if (password != null && !password.isBlank()) { write(output, "AUTH", password); String auth = input.readLine(); if (auth == null || auth.startsWith("-")) throw new IllegalStateException("Redis authentication failed"); }
            write(output, "PUBLISH", channel, payload);
            String result = input.readLine();
            if (result == null || !result.startsWith(":") || Integer.parseInt(result.substring(1)) < 1) {
                throw new IllegalStateException("Redis publish had no subscriber");
            }
        } catch (IOException exception) { throw new IllegalStateException("Redis unavailable", exception); }
    }
    private void write(BufferedWriter output, String... parts) throws IOException { output.write("*" + parts.length + "\r\n"); for (String part : parts) output.write("$" + part.getBytes(StandardCharsets.UTF_8).length + "\r\n" + part + "\r\n"); output.flush(); }
}
