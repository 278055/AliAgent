package com.bn.aliagent.conversation.realtime;

public final class RealtimeCollaborationService {
    private final String instanceId;
    private final RealtimeRouteStore routes;
    private final RealtimePublisher publisher;

    public RealtimeCollaborationService(String instanceId, RealtimeRouteStore routes, RealtimePublisher publisher) {
        this.instanceId = instanceId;
        this.routes = routes;
        this.publisher = publisher;
    }

    public void register(RealtimeConnection connection) { routes.bind(connection); }
    public void unregister(String tenantId, java.util.UUID connectionId) { routes.unbind(tenantId, connectionId); }
    public String instanceId() { return instanceId; }

    public DeliveryResult deliver(RealtimeEnvelope envelope) {
        try {
            var targets = routes.find(envelope.tenantId(), envelope.conversationId());
            if (targets.isEmpty()) return DeliveryResult.rejected("TENANT-403-001");
            for (RealtimeConnection target : targets) {
                if (!target.tenantId().equals(envelope.tenantId())) return DeliveryResult.rejected("TENANT-403-001");
                publisher.publish(channel(envelope.tenantId(), target.instanceId()), envelope.withConnectionId(target.connectionId()));
            }
            return DeliveryResult.successful();
        } catch (RuntimeException exception) {
            return DeliveryResult.rejected("SYSTEM-503-REDIS");
        }
    }

    private String channel(String tenantId, String targetInstanceId) {
        return "conversation:" + tenantId + ":instance:" + targetInstanceId + ":events";
    }
}
