package com.bn.aliagent.insight.aggregate;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*; import java.util.*; import org.junit.jupiter.api.Test;
class AggregationServiceTest {
 @Test void appendsRevisionAndNormalizesDimensions() {
  InMemoryStore store=new InMemoryStore(); HourlyAggregationService service=new HourlyAggregationService(store);
  var first=service.record("test-tenant","refund_rate",1,Instant.parse("2026-06-01T10:30:00Z"),ZoneId.of("Asia/Shanghai"),Map.of("b","2","a","1"),2,4,"f-1");
  var second=service.record("test-tenant","refund_rate",1,Instant.parse("2026-06-01T10:40:00Z"),ZoneId.of("Asia/Shanghai"),Map.of("a","1","b","2"),3,5,"f-2");
  assertEquals(1,first.revision()); assertEquals(2,second.revision()); assertEquals(2,store.history.size()); assertEquals(3,second.numerator());
 }
 @Test void duplicateFactDoesNotCreateRevisionAndDailyFinalizesLatestHours() {
  InMemoryStore store=new InMemoryStore(); HourlyAggregationService hourly=new HourlyAggregationService(store);
  hourly.record("test-tenant","handoff_rate",1,Instant.parse("2026-06-01T00:01:00Z"),ZoneOffset.UTC,Map.of(),1,2,"f-1");
  assertEquals(1,hourly.record("test-tenant","handoff_rate",1,Instant.parse("2026-06-01T00:01:00Z"),ZoneOffset.UTC,Map.of(),1,2,"f-1").revision());
  var daily=new DailyFinalizationService(store).finalizeDay("test-tenant","handoff_rate",1,LocalDate.of(2026,6,1),ZoneOffset.UTC,Map.of());
  assertEquals(WindowGranularity.DAILY,daily.key().granularity()); assertEquals(1,daily.numerator());
 }
 static final class InMemoryStore implements AggregationStore { final List<AggregateRevision> history=new ArrayList<>(); public Optional<AggregateRevision> latest(AggregateKey key){return history.stream().filter(a->a.key().equals(key)).max(Comparator.comparingInt(AggregateRevision::revision));} public AggregateRevision append(AggregateRevision revision,int expected){if(latest(revision.key()).map(AggregateRevision::revision).orElse(0)!=expected)throw new OptimisticConflictException();history.add(revision);return revision;} public List<AggregateRevision> hourly(String tenant,String metric,int version,LocalDate date,ZoneId zone,SortedMap<String,String> dimensions){return history.stream().filter(a->a.key().tenantId().equals(tenant)&&a.key().metric().equals(metric)&&a.key().granularity()==WindowGranularity.HOURLY&&LocalDate.ofInstant(a.key().windowStart(),zone).equals(date)&&a.key().dimensions().equals(dimensions)).toList();}}
}
