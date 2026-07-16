package com.bn.aliagent.conversation.realtime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Redis 路由只保存短生命周期连接信息，数据库中的消息仍是投递事实来源。 */
public final class RedisRealtimeRouteStore implements RealtimeRouteStore {
    private final String host;
    private final int port;
    private final int ttlSeconds;

    public RedisRealtimeRouteStore(String host, int port, int ttlSeconds) {
        this.host = host;
        this.port = port;
        this.ttlSeconds = ttlSeconds;
    }

    @Override public void bind(RealtimeConnection connection) {
        String connectionKey = connectionKey(connection.tenantId(), connection.connectionId());
        command("SET", connectionKey, connection.conversationId() + "|" + connection.instanceId(), "EX", Integer.toString(ttlSeconds));
        String routeKey = routeKey(connection.tenantId(), connection.conversationId());
        command("HSET", routeKey, connection.connectionId().toString(), connection.instanceId());
        command("EXPIRE", routeKey, Integer.toString(ttlSeconds));
    }

    @Override public void unbind(String tenantId, UUID connectionId) {
        String value = command("GET", connectionKey(tenantId, connectionId));
        command("DEL", connectionKey(tenantId, connectionId));
        if (value != null && value.contains("|")) command("HDEL", routeKey(tenantId, UUID.fromString(value.substring(0, value.indexOf('|')))), connectionId.toString());
    }

    @Override public List<RealtimeConnection> find(String tenantId, UUID conversationId) {
        List<String> fields = arrayCommand("HGETALL", routeKey(tenantId, conversationId));
        List<RealtimeConnection> result = new ArrayList<>();
        for (int index = 0; index + 1 < fields.size(); index += 2) {
            UUID connectionId = UUID.fromString(fields.get(index));
            if (command("GET", connectionKey(tenantId, connectionId)) != null) result.add(new RealtimeConnection(tenantId, conversationId, connectionId, fields.get(index + 1)));
        }
        return result;
    }

    private String routeKey(String tenantId, UUID conversationId) { return "conversation:" + tenantId + ":route:" + conversationId; }
    private String connectionKey(String tenantId, UUID connectionId) { return "conversation:" + tenantId + ":connection:" + connectionId; }

    private String command(String... parts) { return request(parts).single(); }
    private List<String> arrayCommand(String... parts) { return request(parts).array(); }
    private RedisReply request(String... parts) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000); socket.setSoTimeout(1000);
            BufferedWriter output = new BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            output.write("*" + parts.length + "\r\n");
            for (String part : parts) output.write("$" + part.getBytes(StandardCharsets.UTF_8).length + "\r\n" + part + "\r\n");
            output.flush();
            return RedisReply.read(new BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
        } catch (IOException exception) { throw new IllegalStateException("Redis unavailable", exception); }
    }

    private record RedisReply(String single, List<String> array) {
        static RedisReply read(BufferedReader input) throws IOException {
            String line = input.readLine();
            if (line == null || line.startsWith("-")) throw new IllegalStateException("Redis command failed");
            if (line.startsWith("*")) { List<String> values = new ArrayList<>(); for (int i = 0; i < Integer.parseInt(line.substring(1)); i++) values.add(readBulk(input)); return new RedisReply(null, values); }
            if (line.equals("$-1")) return new RedisReply(null, List.of());
            if (line.startsWith("$")) return new RedisReply(readBulk(input, Integer.parseInt(line.substring(1))), List.of());
            return new RedisReply(line.length() > 1 ? line.substring(1) : "", List.of());
        }
        private static String readBulk(BufferedReader input) throws IOException { String size = input.readLine(); return size.equals("$-1") ? null : readBulk(input, Integer.parseInt(size.substring(1))); }
        private static String readBulk(BufferedReader input, int length) throws IOException {
            char[] value = new char[length];
            int offset = 0;
            while (offset < length) { int count = input.read(value, offset, length - offset); if (count < 0) throw new IOException("Unexpected Redis response end"); offset += count; }
            input.readLine();
            return new String(value);
        }
    }
}
