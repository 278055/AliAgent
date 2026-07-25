package com.bn.aliagent.conversation.persistence;

import com.bn.aliagent.conversation.assignment.AssignmentModels;
import com.bn.aliagent.conversation.assignment.AssignmentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 分配邀请与接管的条件写入，重复提交由数据库约束回放既有事实。 */
public final class JdbcAssignmentRepository implements AssignmentRepository {
    private final JdbcTemplate jdbc;

    public JdbcAssignmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<AssignmentModels.Offer> findOffer(UUID offerId) { return offers(" WHERE id = ?", offerId).stream().findFirst(); }
    @Override public void saveOffer(AssignmentModels.Offer offer) {
        int changed = jdbc.update("UPDATE human_assignment_offer SET status = ?, takeover_id = ?, version = version + 1 WHERE id = ?", offer.status().name(), offer.takeoverId(), offer.id());
        if (changed == 0) jdbc.update("INSERT INTO human_assignment_offer (id, tenant_id, queue_item_id, conversation_id, staff_id, expires_at, status, takeover_id, request_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", offer.id(), offer.tenantId(), offer.queueItemId(), offer.conversationId(), offer.staffId(), Timestamp.from(offer.expiresAt()), offer.status().name(), offer.takeoverId(), offer.id());
    }
    @Override public Optional<AssignmentModels.Takeover> activeTakeover(String tenantId, UUID conversationId) { return takeovers(" WHERE tenant_id = ? AND conversation_id = ? AND status = 'ACTIVE'", tenantId, conversationId).stream().findFirst(); }
    @Override
    public AssignmentModels.Takeover createTakeoverIfAbsent(AssignmentModels.Takeover takeover) {
        try {
            jdbc.update("INSERT INTO human_takeover (id, tenant_id, conversation_id, staff_id, source_offer_id, request_id, status) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')", takeover.id(), takeover.tenantId(), takeover.conversationId(), takeover.staffId(), takeover.sourceOfferId(), takeover.id());
            jdbc.update("INSERT INTO conversation_human_state (tenant_id, conversation_id, staff_id, status) VALUES (?, ?, ?, 'HUMAN_ACTIVE') ON CONFLICT (tenant_id, conversation_id) DO UPDATE SET staff_id = EXCLUDED.staff_id, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP", takeover.tenantId(), takeover.conversationId(), takeover.staffId());
            return takeover;
        } catch (DuplicateKeyException ignored) {
            return activeTakeover(takeover.tenantId(), takeover.conversationId()).orElseThrow(() -> ignored);
        }
    }
    @Override public int activeTakeoversForStaff(String tenantId, String staffId) { return jdbc.queryForObject("SELECT COUNT(*) FROM human_takeover WHERE tenant_id = ? AND staff_id = ? AND status = 'ACTIVE'", Integer.class, tenantId, staffId); }
    @Override public Optional<AssignmentModels.AssignmentResult> findResult(String tenantId, UUID requestId) {
        return jdbc.query("SELECT accepted, takeover_id FROM human_assignment_result WHERE tenant_id = ? AND request_id = ?", (rs, row) -> new AssignmentModels.AssignmentResult(rs.getBoolean(1), rs.getObject(2, UUID.class)), tenantId, requestId).stream().findFirst();
    }
    @Override public void saveResult(String tenantId, UUID requestId, AssignmentModels.AssignmentResult result) {
        try { jdbc.update("INSERT INTO human_assignment_result (tenant_id, request_id, accepted, takeover_id) VALUES (?, ?, ?, ?)", tenantId, requestId, result.accepted(), result.takeoverId()); }
        catch (DuplicateKeyException ignored) { }
    }
    @Override public List<AssignmentModels.Offer> offersFor(String tenantId, UUID queueItemId) { return offers(" WHERE tenant_id = ? AND queue_item_id = ?", tenantId, queueItemId); }

    private List<AssignmentModels.Offer> offers(String where, Object... args) {
        return jdbc.query("SELECT id, tenant_id, queue_item_id, conversation_id, staff_id, expires_at, status, takeover_id FROM human_assignment_offer" + where,
                (rs, row) -> new AssignmentModels.Offer(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getTimestamp(6).toInstant(), AssignmentModels.OfferStatus.valueOf(rs.getString(7)), rs.getObject(8, UUID.class)), args);
    }
    private List<AssignmentModels.Takeover> takeovers(String where, Object... args) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, staff_id, source_offer_id FROM human_takeover" + where,
                (rs, row) -> new AssignmentModels.Takeover(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, UUID.class)), args);
    }
}
