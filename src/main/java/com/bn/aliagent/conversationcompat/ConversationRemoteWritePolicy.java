package com.bn.aliagent.conversationcompat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 开关仅在服务端按可信租户判定；无法证明租户来源时必须保留旧单体链路。 */
@Component
public class ConversationRemoteWritePolicy {
    private final boolean enabled;
    private final Set<String> tenants;

    public ConversationRemoteWritePolicy(@Value("${feature.conversation.remote-write:false}") boolean enabled,
            @Value("${feature.conversation.remote-write-tenants:}") String tenantList) {
        this.enabled = enabled;
        this.tenants = Arrays.stream(tenantList.split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public boolean enabledForTrustedTenant(String tenantId, boolean trusted) {
        return trusted && enabled && tenants.contains(tenantId);
    }
}
