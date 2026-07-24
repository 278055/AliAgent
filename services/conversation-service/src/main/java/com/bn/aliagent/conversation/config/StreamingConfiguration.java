package com.bn.aliagent.conversation.config;

import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.streaming.DraftStore;
import com.bn.aliagent.conversation.streaming.FailingDraftStore;
import com.bn.aliagent.conversation.streaming.RedisDraftStore;
import com.bn.aliagent.conversation.streaming.SseEventHub;
import com.bn.aliagent.conversation.streaming.StreamingRepository;
import com.bn.aliagent.conversation.streaming.StreamingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@Profile("database")
public class StreamingConfiguration {
    @Bean SseEventHub sseEventHub() { return new SseEventHub(); }
    @Bean DraftStore draftStore(@Value("${CONVERSATION_REDIS_HOST:localhost}") String host, @Value("${CONVERSATION_REDIS_PORT:6379}") int port) { return new RedisDraftStore(host, port, 3600); }
    @Bean StreamingService streamingService(ConversationService conversations, StreamingRepository generations, DraftStore drafts, SseEventHub events) { return new StreamingService(conversations, generations, drafts, events); }
}
