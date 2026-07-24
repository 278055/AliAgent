package com.bn.aliagent.conversation.config;

import com.bn.aliagent.conversation.api.RealtimeWebSocketController;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.realtime.RedisRealtimeSubscriber;
import com.bn.aliagent.conversation.realtime.RealtimeCollaborationService;
import com.bn.aliagent.conversation.realtime.RealtimePublisher;
import com.bn.aliagent.conversation.realtime.RealtimeRouteStore;
import com.bn.aliagent.conversation.realtime.RealtimeSessionRegistry;
import com.bn.aliagent.conversation.realtime.RealtimeStateRepository;
import com.bn.aliagent.conversation.realtime.RealtimeStateService;
import com.bn.aliagent.conversation.realtime.RedisRealtimePublisher;
import com.bn.aliagent.conversation.realtime.RedisRealtimeRouteStore;
import com.bn.platform.security.ServiceJwtSupport;
import jakarta.servlet.ServletContext;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class RealtimeWebSocketConfiguration {
    @Bean RealtimeSessionRegistry realtimeSessions() { return new RealtimeSessionRegistry(); }
    @Bean RealtimeRouteStore realtimeRoutes(@Value("${CONVERSATION_REDIS_HOST:localhost}") String host, @Value("${CONVERSATION_REDIS_PORT:6379}") int port, @Value("${CONVERSATION_REDIS_PASSWORD:}") String password) { return new RedisRealtimeRouteStore(host, port, password, 60); }
    @Bean RealtimePublisher realtimePublisher(@Value("${CONVERSATION_REDIS_HOST:localhost}") String host, @Value("${CONVERSATION_REDIS_PORT:6379}") int port, @Value("${CONVERSATION_REDIS_PASSWORD:}") String password) { return new RedisRealtimePublisher(host, port, password); }
    @Bean RealtimeStateService realtimeStateService(RealtimeStateRepository repository) { return new RealtimeStateService(repository); }
    @Bean RealtimeCollaborationService realtimeCollaborationService(@Value("${CONVERSATION_INSTANCE_ID:conversation-local}") String instanceId, RealtimeRouteStore routes, RealtimePublisher publisher) { return new RealtimeCollaborationService(instanceId, routes, publisher); }
    @Bean(destroyMethod = "close") RedisRealtimeSubscriber realtimeSubscriber(@Value("${CONVERSATION_REDIS_HOST:localhost}") String host, @Value("${CONVERSATION_REDIS_PORT:6379}") int port, @Value("${CONVERSATION_REDIS_PASSWORD:}") String password, @Value("${CONVERSATION_INSTANCE_ID:conversation-local}") String instanceId, RealtimeSessionRegistry sessions) {
        return new RedisRealtimeSubscriber(host, port, password, instanceId, sessions);
    }
    @Bean ServletContextInitializer realtimeWebSocketEndpoint(@Value("${SERVICE_JWT_SECRET:test-service-jwt-secret-must-be-at-least-32-bytes}") String secret, ConversationService conversations, RealtimeCollaborationService realtime, RealtimeSessionRegistry sessions, RealtimeStateService state, RedisRealtimeSubscriber subscriber) {
        return servletContext -> {
            try { register(servletContext, new ServiceJwtSupport(secret), conversations, realtime, sessions, state, subscriber); }
            catch (Exception exception) { throw new IllegalStateException("Unable to register conversation WebSocket endpoint", exception); }
        };
    }

    private void register(ServletContext servletContext, ServiceJwtSupport jwt, ConversationService conversations, RealtimeCollaborationService realtime, RealtimeSessionRegistry sessions, RealtimeStateService state, RedisRealtimeSubscriber subscriber) throws Exception {
        ServerContainer container = (ServerContainer) servletContext.getAttribute(ServerContainer.class.getName());
        ServerEndpointConfig endpoint = ServerEndpointConfig.Builder.create(RealtimeWebSocketController.class, "/api/v1/ws/conversations")
                .configurator(new ServerEndpointConfig.Configurator() {
                    @Override public <T> T getEndpointInstance(Class<T> type) { return type.cast(new RealtimeWebSocketController(conversations, realtime, sessions, state, subscriber)); }
                    @Override public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, jakarta.websocket.HandshakeResponse response) {
                        try {
                            var headers = request.getHeaders();
                            String authorization = required(headers, "X-Service-Authorization");
                            if (!authorization.startsWith("Bearer ")) throw new IllegalArgumentException("X-Service-Authorization");
                            jwt.verify(authorization.substring(7), "conversation-service", "GET:/api/v1/ws/conversations");
                            config.getUserProperties().put(RealtimeWebSocketController.CONTEXT, new TrustedConversationRequestContext(required(headers, "X-Tenant-Id"), required(headers, "X-Subject-Id"), required(headers, "X-Subject-Type"), required(headers, "X-Trace-Id"), UUID.fromString(required(headers, "X-Request-Id"))));
                        } catch (RuntimeException exception) { throw new IllegalArgumentException("AUTH-401-001 trusted WebSocket context is invalid", exception); }
                    }
                    private String required(java.util.Map<String, List<String>> headers, String name) { List<String> values = headers.get(name); if (values == null || values.isEmpty() || values.get(0).isBlank()) throw new IllegalArgumentException(name); return values.get(0); }
                }).build();
        container.addEndpoint(endpoint);
    }
}
