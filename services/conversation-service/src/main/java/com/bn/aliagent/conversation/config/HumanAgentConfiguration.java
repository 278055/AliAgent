package com.bn.aliagent.conversation.config;

import com.bn.aliagent.conversation.agent.AgentDirectoryPort;
import com.bn.aliagent.conversation.assignment.AssignmentPorts;
import com.bn.aliagent.conversation.assignment.AssignmentRepository;
import com.bn.aliagent.conversation.assignment.AssignmentService;
import com.bn.aliagent.conversation.persistence.JdbcAssignmentRepository;
import com.bn.aliagent.conversation.persistence.JdbcCollaborationRepositories;
import com.bn.aliagent.conversation.persistence.JdbcHumanAgentAdapters;
import com.bn.aliagent.conversation.persistence.JdbcSkillGroupRepository;
import com.bn.aliagent.conversation.persistence.JdbcSkillGroupAdministrationRepository;
import com.bn.aliagent.conversation.persistence.JdbcVerifiedConversationTagRepository;
import com.bn.aliagent.conversation.queue.HumanQueueModels;
import com.bn.aliagent.conversation.queue.HumanQueueRepository;
import com.bn.aliagent.conversation.queue.HumanQueueService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import com.bn.aliagent.conversation.skill.SkillGroupRepository;
import com.bn.aliagent.conversation.skill.SkillRoutingService;
import com.bn.aliagent.conversation.skill.SkillGroupAdministrationRepository;
import com.bn.aliagent.conversation.skill.SkillGroupAdministrationService;
import com.bn.aliagent.conversation.queue.VerifiedConversationTagRepository;
import com.bn.aliagent.conversation.queue.TrustedHumanQueueEntryService;

@Configuration
@Profile("database")
public class HumanAgentConfiguration {
    @Bean MessageConverter p7RabbitMessageConverter() { return new Jackson2JsonMessageConverter(); }
    @Bean HumanQueueRepository humanQueueRepository(JdbcTemplate jdbc) { return new JdbcHumanAgentAdapters(jdbc); }
    @Bean AgentDirectoryPort agentDirectoryPort(JdbcTemplate jdbc) { return new JdbcHumanAgentAdapters(jdbc); }
    @Bean SkillGroupRepository skillGroupRepository(JdbcTemplate jdbc) { return new JdbcSkillGroupRepository(jdbc); }
    @Bean SkillGroupAdministrationRepository skillGroupAdministrationRepository(JdbcTemplate jdbc) { return new JdbcSkillGroupAdministrationRepository(jdbc); }
    @Bean SkillGroupAdministrationService skillGroupAdministrationService(SkillGroupAdministrationRepository repository) { return new SkillGroupAdministrationService(repository); }
    @Bean SkillRoutingService skillRoutingService(SkillGroupRepository repository) { return new SkillRoutingService(repository); }
    @Bean VerifiedConversationTagRepository verifiedConversationTagRepository(JdbcTemplate jdbc) { return new JdbcVerifiedConversationTagRepository(jdbc); }
    @Bean AssignmentRepository assignmentRepository(JdbcTemplate jdbc) { return new JdbcAssignmentRepository(jdbc); }
    @Bean com.bn.aliagent.conversation.takeover.TakeoverRepository takeoverRepository(JdbcTemplate jdbc) { return new JdbcCollaborationRepositories.Takeovers(jdbc); }
    @Bean com.bn.aliagent.conversation.transfer.TransferRepository transferRepository(JdbcTemplate jdbc) { return new JdbcCollaborationRepositories.Transfers(jdbc); }
    @Bean com.bn.aliagent.conversation.handoff.HandoffRepository handoffRepository(JdbcTemplate jdbc) { return new JdbcCollaborationRepositories.Handoffs(jdbc); }
    @Bean com.bn.aliagent.conversation.staffmessage.StaffMessageRepository staffMessageRepository(JdbcTemplate jdbc) { return new JdbcCollaborationRepositories.StaffMessages(jdbc); }
    @Bean AssignmentPorts assignmentPorts(@Qualifier("agentDirectoryPort") AgentDirectoryPort directory) { return directory::candidates; }
    @Bean HumanQueueService humanQueueService(@Qualifier("humanQueueRepository") HumanQueueRepository repository) { return new HumanQueueService(repository, new HumanQueueModels.PriorityPolicy()); }
    @Bean TrustedHumanQueueEntryService trustedHumanQueueEntryService(VerifiedConversationTagRepository tags, SkillRoutingService routing, HumanQueueService queues) { return new TrustedHumanQueueEntryService(tags, routing, queues); }
    @Bean AssignmentService assignmentService(HumanQueueService queues, AssignmentPorts directory, AssignmentRepository repository) { return new AssignmentService(queues, directory, repository, 30, 3); }
    @Bean com.bn.aliagent.conversation.takeover.TakeoverService takeoverService(com.bn.aliagent.conversation.takeover.TakeoverRepository repository) { return new com.bn.aliagent.conversation.takeover.TakeoverService(repository); }
    @Bean com.bn.aliagent.conversation.transfer.TransferService transferService(com.bn.aliagent.conversation.transfer.TransferRepository repository) { return new com.bn.aliagent.conversation.transfer.TransferService(repository); }
    @Bean com.bn.aliagent.conversation.handoff.HandoffService handoffService(com.bn.aliagent.conversation.handoff.HandoffRepository repository, com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox outbox) { return new com.bn.aliagent.conversation.handoff.HandoffService(repository, outbox); }
    @Bean com.bn.aliagent.conversation.staffmessage.StaffMessageService staffMessageService(com.bn.aliagent.conversation.staffmessage.StaffMessageRepository repository, com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox outbox) { return new com.bn.aliagent.conversation.staffmessage.StaffMessageService(repository, outbox); }
    @Bean com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox humanCollaborationOutbox(JdbcTemplate jdbc) { return new com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox(jdbc); }
    @Bean Queue humanCollaborationEventsQueue() { return new Queue("conversation.human.events.v1", true); }
    @Bean Queue copilotSuggestionRequestedV2Queue() { return new Queue("copilot.suggestion.requested.v2", true); }
    @Bean com.bn.aliagent.conversation.messaging.HumanCollaborationOutboxDispatcher humanCollaborationOutboxDispatcher(com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox outbox, RabbitTemplate rabbit, MessageConverter p7RabbitMessageConverter) { rabbit.setMessageConverter(p7RabbitMessageConverter); return new com.bn.aliagent.conversation.messaging.HumanCollaborationOutboxDispatcher(outbox, rabbit); }
    @Bean com.bn.aliagent.conversation.realtime.HumanAgentRealtimePublisher humanAgentRealtimePublisher(com.bn.aliagent.conversation.realtime.RealtimeCollaborationService realtime) { return new com.bn.aliagent.conversation.realtime.HumanAgentRealtimePublisher(realtime); }
}
