package com.macro.mall.portal.aftersale.persistence;

import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.api.CreateAfterSaleDraft;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import com.macro.mall.portal.aftersale.core.AfterSaleStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class AfterSaleJdbcRepository {
    private final JdbcTemplate jdbc;
    public AfterSaleJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<AfterSaleView> findByCommand(String commandId) { return find("SELECT id, order_id, case_no, rule_version_id, status, requested_amount FROM after_sale_case WHERE command_id=?", commandId); }
    public Optional<AfterSaleView> findByIdempotency(String tenantId, String commandType, String key) { return find("SELECT id, order_id, case_no, rule_version_id, status, requested_amount FROM after_sale_case WHERE tenant_id=? AND command_type=? AND idempotency_key=?", tenantId, commandType, key); }
    public Optional<AfterSaleView> findById(String tenantId, Long caseId) { return find("SELECT id, order_id, case_no, rule_version_id, status, requested_amount FROM after_sale_case WHERE tenant_id=? AND id=?", tenantId, caseId); }
    public boolean activeItemExists(String tenantId, Long orderItemId) { return jdbc.queryForObject("SELECT COUNT(*) FROM after_sale_case_item WHERE tenant_id=? AND active_order_item_id=?", Integer.class, tenantId, orderItemId) > 0; }
    public long insertCase(TrustedAfterSaleContext context, CreateAfterSaleDraft command, String caseNo, String ruleVersionId, AfterSaleStatus status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement statement = connection.prepareStatement("INSERT INTO after_sale_case(case_no,tenant_id,order_id,member_id,case_type,status,requested_amount,rule_version_id,authorization_snapshot_id,command_id,command_type,idempotency_key,trace_id,request_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, caseNo); statement.setString(2, context.tenantId()); statement.setLong(3, command.orderId()); statement.setLong(4, context.actorId()); statement.setString(5, command.commandType()); statement.setString(6, status.name()); statement.setBigDecimal(7, command.requestedAmount()); statement.setString(8, ruleVersionId); statement.setString(9, context.authorizationSnapshotId()); statement.setString(10, command.commandId()); statement.setString(11, command.commandType()); statement.setString(12, command.idempotencyKey()); statement.setString(13, context.traceId()); statement.setString(14, context.requestId()); return statement; }, key);
        return key.getKey().longValue();
    }
    public void insertItem(long caseId, TrustedAfterSaleContext context, CreateAfterSaleDraft command, AfterSaleStatus status) { jdbc.update("INSERT INTO after_sale_case_item(case_id,tenant_id,order_id,order_item_id,product_id,quantity,case_status) VALUES (?,?,?,?,?,?,?)", caseId, context.tenantId(), command.orderId(), command.orderItemId(), command.productId(), command.quantity(), status.name()); }
    public long insertSaga(long caseId, TrustedAfterSaleContext context) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement statement = connection.prepareStatement("INSERT INTO after_sale_saga(case_id,tenant_id,status,current_step) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS); statement.setLong(1, caseId); statement.setString(2, context.tenantId()); statement.setString(3, "PENDING"); statement.setString(4, "AFTERSALE_CREATED"); return statement; }, key);
        return key.getKey().longValue();
    }
    public void insertSagaStep(long sagaId, String step, String key) { jdbc.update("INSERT INTO after_sale_saga_step(saga_id,step_type,status,idempotency_key) VALUES (?,?,?,?)", sagaId, step, "PENDING", key); }
    public void ensureSagaStep(long caseId, String step, String idempotencyKey) { jdbc.update("INSERT IGNORE INTO after_sale_saga_step(saga_id,step_type,status,idempotency_key) SELECT id, ?, 'PENDING', ? FROM after_sale_saga WHERE case_id=?", step, idempotencyKey, caseId); }
    public boolean updateSagaStep(long caseId, String step, String status, String idempotencyKey, String error) {
        return jdbc.update("UPDATE after_sale_saga_step s JOIN after_sale_saga g ON s.saga_id=g.id SET s.status=?, s.error_message=?, s.completed_at=CASE WHEN ?='SUCCEEDED' THEN CURRENT_TIMESTAMP(6) ELSE s.completed_at END, s.attempt_count=s.attempt_count+1 WHERE g.case_id=? AND s.step_type=? AND s.idempotency_key=?", status, error, status, caseId, step, idempotencyKey) == 1;
    }
    public void updateSaga(long caseId, String status, String step) { jdbc.update("UPDATE after_sale_saga SET status=?, current_step=?, version=version+1 WHERE case_id=?", status, step, caseId); }
    public void insertApproval(long caseId, TrustedAfterSaleContext context, String type) { jdbc.update("INSERT INTO after_sale_approval(case_id,tenant_id,approval_type,status,authorization_snapshot_id) VALUES (?,?,?,?,?)", caseId, context.tenantId(), type, "PENDING", context.authorizationSnapshotId()); }
    public void decideApproval(long caseId, TrustedAfterSaleContext context, String type, String status) { jdbc.update("UPDATE after_sale_approval SET status=?, approver_id=?, decided_at=CURRENT_TIMESTAMP(6) WHERE case_id=? AND approval_type=? AND tenant_id=?", status, String.valueOf(context.actorId()), caseId, type, context.tenantId()); }
    public void outbox(long caseId, TrustedAfterSaleContext context, String eventId, String eventType) { jdbc.update("INSERT INTO after_sale_outbox(event_id,aggregate_id,event_type,event_version,tenant_id,trace_id,payload,status,next_attempt_at,occurred_at) VALUES (?,?,?,?,?,?,JSON_OBJECT('caseId', ?),?,?,?)", eventId, caseId, eventType, 1, context.tenantId(), context.traceId(), caseId, "PENDING", Timestamp.from(Instant.now()), Timestamp.from(Instant.now())); }
    public boolean claimInbox(String eventId, String consumer, String tenantId) { return jdbc.update("INSERT IGNORE INTO after_sale_inbox(event_id,consumer_name,tenant_id,status) VALUES (?,?,?,?)", eventId, consumer, tenantId, "RECEIVED") == 1; }
    public void updateStatus(long caseId, AfterSaleStatus status) { jdbc.update("UPDATE after_sale_case SET status=? WHERE id=?", status.name(), caseId); jdbc.update("UPDATE after_sale_case_item SET case_status=? WHERE case_id=?", status.name(), caseId); }
    public void audit(long caseId, TrustedAfterSaleContext context, String action, String commandId) { jdbc.update("INSERT INTO after_sale_audit(case_id,tenant_id,action_type,actor_id,actor_type,authorization_snapshot_id,command_id,trace_id,request_id,detail) VALUES (?,?,?,?,?,?,?,?,?,JSON_OBJECT('action',?))", caseId, context.tenantId(), action, String.valueOf(context.actorId()), context.actorType(), context.authorizationSnapshotId(), commandId, context.traceId(), context.requestId(), action); }
    private Optional<AfterSaleView> find(String sql, Object... args) { return jdbc.query(sql, args, (result, row) -> new AfterSaleView(result.getLong("id"), result.getLong("order_id"), result.getString("case_no"), result.getString("rule_version_id"), AfterSaleStatus.valueOf(result.getString("status")), result.getBigDecimal("requested_amount"))).stream().findFirst(); }
}
