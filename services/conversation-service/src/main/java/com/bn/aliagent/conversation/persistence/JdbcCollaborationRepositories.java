package com.bn.aliagent.conversation.persistence;

import com.bn.aliagent.conversation.handoff.HandoffCommand;
import com.bn.aliagent.conversation.handoff.HandoffRepository;
import com.bn.aliagent.conversation.handoff.HandoffResult;
import com.bn.aliagent.conversation.staffmessage.ConversationAccess;
import com.bn.aliagent.conversation.staffmessage.StaffMessage;
import com.bn.aliagent.conversation.staffmessage.StaffMessageRepository;
import com.bn.aliagent.conversation.takeover.Takeover;
import com.bn.aliagent.conversation.takeover.TakeoverCommand;
import com.bn.aliagent.conversation.takeover.TakeoverRepository;
import com.bn.aliagent.conversation.transfer.SkillGroupTransferCommand;
import com.bn.aliagent.conversation.transfer.Transfer;
import com.bn.aliagent.conversation.transfer.TransferRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 各领域端口同名方法返回类型不同，因此保持独立的 JDBC 适配器。 */
public final class JdbcCollaborationRepositories {
    private JdbcCollaborationRepositories() { }

    public static class Takeovers implements TakeoverRepository {
        private final JdbcTemplate jdbc;
        public Takeovers(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        public boolean conversationExists(String tenant, UUID conversation) { return exists(jdbc, tenant, conversation); }
        public boolean acceptedOrClaimed(TakeoverCommand command) {
            return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM human_assignment_offer WHERE tenant_id=? AND conversation_id=? AND staff_id=? AND status='ACCEPTED') OR EXISTS (SELECT 1 FROM human_takeover WHERE tenant_id=? AND conversation_id=? AND staff_id=? AND status='ACTIVE')", Boolean.class, command.tenantId(), command.conversationId(), command.staffId(), command.tenantId(), command.conversationId(), command.staffId()));
        }
        public Takeover findByRequest(String tenant, UUID request) { return jdbc.query("SELECT id,tenant_id,conversation_id,staff_id,request_id,status,created_at FROM human_takeover WHERE tenant_id=? AND request_id=?", (rs, n) -> takeover(rs), tenant, request).stream().findFirst().orElse(null); }
        @Transactional public Takeover createActiveIfAbsent(Takeover value) {
            try {
                jdbc.update("INSERT INTO human_takeover (id,tenant_id,conversation_id,staff_id,request_id,status,created_at) VALUES (?,?,?,?,?,?,?)", value.id(), value.tenantId(), value.conversationId(), value.staffId(), value.requestId(), value.status(), Timestamp.from(value.createdAt()));
                jdbc.update("INSERT INTO conversation_human_state (tenant_id,conversation_id,staff_id,status) VALUES (?,?,?,'HUMAN_ACTIVE') ON CONFLICT (tenant_id,conversation_id) DO UPDATE SET staff_id=EXCLUDED.staff_id,status=EXCLUDED.status,updated_at=CURRENT_TIMESTAMP", value.tenantId(), value.conversationId(), value.staffId());
                return value;
            } catch (DuplicateKeyException duplicate) {
                return jdbc.query("SELECT id,tenant_id,conversation_id,staff_id,request_id,status,created_at FROM human_takeover WHERE tenant_id=? AND conversation_id=? AND status='ACTIVE'", (rs, n) -> takeover(rs), value.tenantId(), value.conversationId()).stream().findFirst().orElseThrow(() -> duplicate);
            }
        }
    }

    public static class Transfers implements TransferRepository {
        private final JdbcTemplate jdbc;
        public Transfers(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        public String currentAgent(String tenant, UUID conversation) { return jdbc.query("SELECT staff_id FROM human_takeover WHERE tenant_id=? AND conversation_id=? AND status='ACTIVE'", (rs, n) -> rs.getString(1), tenant, conversation).stream().findFirst().orElse(null); }
        public boolean conversationExists(String tenant, UUID conversation) { return exists(jdbc, tenant, conversation); }
        public boolean targetEligible(String tenant, String staff) {
            return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM agent_skill_group_member m JOIN conversation_agent_presence p ON p.tenant_id=m.tenant_id AND p.staff_id=m.staff_id AND p.status='ONLINE' WHERE m.tenant_id=? AND m.staff_id=? AND m.enabled AND (SELECT COUNT(*) FROM human_takeover t WHERE t.tenant_id=m.tenant_id AND t.staff_id=m.staff_id AND t.status='ACTIVE') < m.max_concurrent)", Boolean.class, tenant, staff));
        }
        public Transfer findByRequest(String tenant, UUID request) { return transfers(" WHERE tenant_id=? AND request_id=?", tenant, request).stream().findFirst().orElse(null); }
        public Transfer save(Transfer value) { try { jdbc.update("INSERT INTO human_transfer (id,tenant_id,conversation_id,source_staff_id,target_staff_id,target_skill_group_id,request_id,status,reason,created_at) VALUES (?,?,?,?,?,CAST(? AS uuid),?,?,?,?)", value.id(), value.tenantId(), value.conversationId(), value.sourceStaffId(), value.targetStaffId(), value.targetSkillGroupId(), value.requestId(), value.status(), value.reason(), Timestamp.from(value.createdAt())); return value; } catch (DuplicateKeyException duplicate) { return findByRequest(value.tenantId(), value.requestId()); } }
        public Transfer find(UUID id, String tenant) { return transfers(" WHERE id=? AND tenant_id=?", id, tenant).stream().findFirst().orElse(null); }
        @Transactional public Transfer acceptAndReplaceIfPending(Transfer transfer, UUID request) {
            if (jdbc.update("UPDATE human_transfer SET status='ACCEPTED',version=version+1 WHERE id=? AND tenant_id=? AND status='PENDING'", transfer.id(), transfer.tenantId()) == 0) return find(transfer.id(), transfer.tenantId());
            jdbc.update("UPDATE human_takeover SET status='TRANSFERRED',ended_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND conversation_id=? AND status='ACTIVE'", transfer.tenantId(), transfer.conversationId());
            jdbc.update("INSERT INTO human_takeover (id,tenant_id,conversation_id,staff_id,request_id,status) VALUES (?,?,?,?,?,'ACTIVE')", UUID.randomUUID(), transfer.tenantId(), transfer.conversationId(), transfer.targetStaffId(), request);
            jdbc.update("INSERT INTO conversation_human_state (tenant_id,conversation_id,staff_id,status) VALUES (?,?,?,'HUMAN_ACTIVE') ON CONFLICT (tenant_id,conversation_id) DO UPDATE SET staff_id=EXCLUDED.staff_id,status=EXCLUDED.status,updated_at=CURRENT_TIMESTAMP", transfer.tenantId(), transfer.conversationId(), transfer.targetStaffId());
            return find(transfer.id(), transfer.tenantId());
        }
        @Transactional public Transfer requeueAndTransferAtomically(SkillGroupTransferCommand command) {
            UUID group = UUID.fromString(command.skillGroupId());
            UUID queue = UUID.randomUUID();
            jdbc.update("INSERT INTO human_queue_item (id,tenant_id,conversation_id,skill_group_id,priority,routing_rule_version,priority_rule_version,request_id,status,enqueued_at) VALUES (?,?,?,?,0,'p7-routing-v1','p7-priority-v1',?,'WAITING',CURRENT_TIMESTAMP)", queue, command.tenantId(), command.conversationId(), group, command.requestId());
            jdbc.update("UPDATE human_takeover SET status='TRANSFERRED',ended_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND conversation_id=? AND staff_id=? AND status='ACTIVE'", command.tenantId(), command.conversationId(), command.sourceStaffId());
            jdbc.update("INSERT INTO conversation_human_state (tenant_id,conversation_id,staff_id,status) VALUES (?,?,NULL,'WAITING_HUMAN') ON CONFLICT (tenant_id,conversation_id) DO UPDATE SET staff_id=NULL,status=EXCLUDED.status,updated_at=CURRENT_TIMESTAMP", command.tenantId(), command.conversationId());
            return save(Transfer.skillGroup(command));
        }
        public void auditSupervisorAction(String tenant, UUID conversation, String supervisor, String action, String reason) { audit(jdbc, tenant, conversation, supervisor, action, reason, null); }
        private List<Transfer> transfers(String where, Object... args) { return jdbc.query("SELECT id,tenant_id,conversation_id,source_staff_id,target_staff_id,target_skill_group_id,request_id,status,reason,created_at FROM human_transfer" + where, (rs, n) -> new Transfer(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),rs.getString(6),rs.getObject(7,UUID.class),rs.getString(8),rs.getString(9),rs.getTimestamp(10).toInstant()), args); }
    }

    public static class Handoffs implements HandoffRepository {
        private final JdbcTemplate jdbc;
        public Handoffs(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        public HandoffResult findByRequest(UUID request) { return jdbc.query("SELECT request_id,payload->>'conversationStatus',payload->>'takeoverStatus',reason FROM human_collaboration_audit WHERE request_id=? AND action='HANDOFF'", (rs,n)->new HandoffResult(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),request).stream().findFirst().orElse(null); }
        public boolean currentStaff(String tenant, UUID conversation, String staff) { return staff != null && staff.equals(new Transfers(jdbc).currentAgent(tenant, conversation)); }
        public boolean conversationExists(String tenant, UUID conversation) { return exists(jdbc, tenant, conversation); }
        @Transactional public HandoffResult complete(HandoffCommand command, String next, String takeover) {
            HandoffResult replay=findByRequest(command.requestId()); if(replay!=null)return replay;
            String current=jdbc.query("SELECT status FROM conversation_human_state WHERE tenant_id=? AND conversation_id=?",(rs,n)->rs.getString(1),command.tenantId(),command.conversationId()).stream().findFirst().orElse("AI_ACTIVE");
            if("CLOSED".equals(current)) throw new IllegalStateException("closed");
            jdbc.update("UPDATE human_takeover SET status=?,ended_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND conversation_id=? AND status='ACTIVE'",takeover,command.tenantId(),command.conversationId());
            jdbc.update("INSERT INTO conversation_human_state (tenant_id,conversation_id,staff_id,status) VALUES (?,?,NULL,?) ON CONFLICT (tenant_id,conversation_id) DO UPDATE SET staff_id=NULL,status=EXCLUDED.status,updated_at=CURRENT_TIMESTAMP",command.tenantId(),command.conversationId(),next);
            HandoffResult result=new HandoffResult(command.requestId(),next,takeover,command.reason());
            jdbc.update("INSERT INTO human_collaboration_audit (id,tenant_id,conversation_id,action,reason,request_id,payload) VALUES (?,?,?,'HANDOFF',?,?,jsonb_build_object('conversationStatus',?,'takeoverStatus',?))",UUID.randomUUID(),command.tenantId(),command.conversationId(),command.reason(),command.requestId(),next,takeover);
            return result;
        }
        public void auditSupervisorAction(String tenant, UUID conversation, String supervisor, String action, String reason) { audit(jdbc,tenant,conversation,supervisor,action,reason,null); }
    }

    public static final class StaffMessages implements StaffMessageRepository {
        private final JdbcTemplate jdbc;
        public StaffMessages(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        public ConversationAccess conversation(String tenant, UUID conversation) { return jdbc.query("SELECT status,staff_id FROM conversation_human_state WHERE tenant_id=? AND conversation_id=?",(rs,n)->new ConversationAccess(rs.getString(1),rs.getString(2)),tenant,conversation).stream().findFirst().orElse(null); }
        public StaffMessage findByRequestId(String tenant, UUID request) { return messages(" WHERE tenant_id=? AND request_id=?",tenant,request).stream().findFirst().orElse(null); }
        public StaffMessage findByClientMessageId(String tenant, UUID conversation, String staff, UUID client) { return messages(" WHERE tenant_id=? AND conversation_id=? AND sender_subject_id=? AND client_message_id=?",tenant,conversation,staff,client).stream().findFirst().orElse(null); }
        public StaffMessage appendIfAbsent(StaffMessage value) {
            try { jdbc.update("INSERT INTO message (id,tenant_id,conversation_id,sequence,sender_type,sender_subject_id,message_type,visibility,content,status,request_id,client_message_id,metadata,created_at,updated_at) SELECT ?,?,?,COALESCE(MAX(sequence),0)+1,'STAFF',?,'TEXT','PUBLIC',?,'COMPLETED',?,?, '{}'::jsonb,?,? FROM message WHERE tenant_id=? AND conversation_id=?",value.id(),value.tenantId(),value.conversationId(),value.staffId(),value.content(),value.requestId(),value.clientMessageId(),Timestamp.from(value.createdAt()),Timestamp.from(value.createdAt()),value.tenantId(),value.conversationId()); return value; }
            catch(DuplicateKeyException duplicate){ StaffMessage existing=findByRequestId(value.tenantId(),value.requestId()); return existing!=null?existing:findByClientMessageId(value.tenantId(),value.conversationId(),value.staffId(),value.clientMessageId()); }
        }
        private List<StaffMessage> messages(String where,Object...args){return jdbc.query("SELECT id,tenant_id,conversation_id,sender_subject_id,sender_type,visibility,content,client_message_id,request_id,created_at FROM message"+where,(rs,n)->new StaffMessage(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getObject(8,UUID.class),rs.getObject(9,UUID.class),rs.getTimestamp(10).toInstant()),args);}
    }

    private static boolean exists(JdbcTemplate jdbc,String tenant,UUID conversation){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM conversation WHERE tenant_id=? AND id=? AND deleted_at IS NULL)",Boolean.class,tenant,conversation));}
    private static Takeover takeover(java.sql.ResultSet rs) throws java.sql.SQLException{return new Takeover(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4),rs.getObject(5,UUID.class),rs.getString(6),rs.getTimestamp(7).toInstant());}
    private static void audit(JdbcTemplate jdbc,String tenant,UUID conversation,String actor,String action,String reason,UUID request){jdbc.update("INSERT INTO human_collaboration_audit (id,tenant_id,conversation_id,actor_id,action,reason,request_id) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,conversation,actor,action,reason,request);}
}
