package com.bn.aliagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AliAgentApplicationTests {

    @Autowired
    private ChatClient chatClient;

    @Test
    void contextLoads() {
        assertThat(chatClient).isNotNull();
    }

    @Test
    void testChatWithQwen() {
        String response = chatClient.prompt()
                .user("请用一句话介绍一下你自己")
                .call()
                .content();

        System.out.println("========== 千问回复 ==========");
        System.out.println(response);
        System.out.println("==============================");

        assertThat(response).isNotNull().isNotEmpty();
    }
}
