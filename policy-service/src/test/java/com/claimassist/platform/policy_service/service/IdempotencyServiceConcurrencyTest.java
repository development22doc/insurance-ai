package com.claimassist.platform.policy_service.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.claimassist.platform.policy_service.entity.IdempotencyRecord;
import com.claimassist.platform.policy_service.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyServiceConcurrencyTest {

    @Test
    public void concurrentRequests_onlyOneExecutes() throws Exception {
        String key = "itest-key-1";

        // In-memory map to simulate DB
        ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

        IdempotencyRecordRepository repo = Mockito.mock(IdempotencyRecordRepository.class);
        ObjectMapper om = new ObjectMapper();
        PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus dummyStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(tm.getTransaction(Mockito.any())).thenReturn(dummyStatus);
        Mockito.doNothing().when(tm).commit(Mockito.any());
        Mockito.doNothing().when(tm).rollback(Mockito.any());

        // Mock findById
        Mockito.when(repo.findById(Mockito.anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        // Mock deleteAll
        Mockito.doAnswer(invocation -> { store.clear(); return null; }).when(repo).deleteAll();
        // Mock deleteById
        Mockito.doAnswer(invocation -> { store.remove(invocation.getArgument(0)); return null; }).when(repo).deleteById(Mockito.anyString());

        // Mock save: if not present, insert (set createdAt); else update; on race throw DataIntegrityViolationException
        Mockito.when(repo.save(Mockito.any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord r = invocation.getArgument(0);
            String k = r.getKey();
            IdempotencyRecord existing = store.get(k);
            if (existing == null) {
                IdempotencyRecord copy = IdempotencyRecord.builder()
                        .key(r.getKey())
                        .userId(r.getUserId())
                        .operation(r.getOperation())
                        .responseBody(r.getResponseBody())
                        .fingerprint(r.getFingerprint())
                        .createdAt(Instant.now())
                        .build();
                IdempotencyRecord prev = store.putIfAbsent(k, copy);
                if (prev == null) return copy;
                throw new DataIntegrityViolationException("duplicate key");
            } else {
                IdempotencyRecord copy = IdempotencyRecord.builder()
                        .key(r.getKey())
                        .userId(r.getUserId() == null ? existing.getUserId() : r.getUserId())
                        .operation(r.getOperation() == null ? existing.getOperation() : r.getOperation())
                        .responseBody(r.getResponseBody())
                        .fingerprint(r.getFingerprint() == null ? existing.getFingerprint() : r.getFingerprint())
                        .createdAt(existing.getCreatedAt())
                        .build();
                store.put(k, copy);
                return copy;
            }
        });

        IdempotencyService svc = new IdempotencyService(repo, om, tm);

        // Ensure store empty
        repo.deleteAll();

        AtomicInteger execCount = new AtomicInteger(0);
        Supplier<Map> supplier = () -> {
            execCount.incrementAndGet();
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            return Map.of("ok", true);
        };

        ExecutorService es = Executors.newFixedThreadPool(2);

        Future<Map> f1 = es.submit(() -> svc.execute(key, "op", 123L, (Class<Map>)(Class) Map.class, supplier));
        // Slight stagger to increase chance of race
        Thread.sleep(50);
        Future<?> f2 = es.submit(() -> {
            try {
                svc.execute(key, "op", 123L, (Class<Map>)(Class) Map.class, supplier);
                return null;
            } catch (Exception e) {
                throw e;
            }
        });

        Map r1 = f1.get(5, TimeUnit.SECONDS);

        Exception ex = null;
        try {
            f2.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ee) {
            ex = (Exception) ee.getCause();
        }

        // One execution only
        Assertions.assertEquals(1, execCount.get(), "Command should be executed exactly once");

        // Second request should have seen in-progress and failed with IllegalStateException
        Assertions.assertNotNull(ex, "Second concurrent call should have thrown an exception");
        Assertions.assertTrue(ex instanceof DataIntegrityViolationException, "Expected DataIntegrityViolationException when seeing IN_PROGRESS (mapped to 409)");

        // Record persisted with final response
        Optional<IdempotencyRecord> rec = repo.findById(key);
        Assertions.assertTrue(rec.isPresent(), "Idempotency record should exist");
        Assertions.assertTrue(rec.get().getResponseBody().contains("\"ok\":true"), "Persisted response should contain result");

        es.shutdownNow();
    }

    @Test
    public void finalizeFailure_removesClaim() throws Exception {
        String key = "itest-finalize-1";
        ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
        IdempotencyRecordRepository repo = Mockito.mock(IdempotencyRecordRepository.class);
        ObjectMapper om = new ObjectMapper();
        PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus dummyStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(tm.getTransaction(Mockito.any())).thenReturn(dummyStatus);
        Mockito.doNothing().when(tm).commit(Mockito.any());
        Mockito.doNothing().when(tm).rollback(Mockito.any());

        // findById
        Mockito.when(repo.findById(Mockito.anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        Mockito.doAnswer(invocation -> { store.clear(); return null; }).when(repo).deleteAll();
        Mockito.doAnswer(invocation -> { store.remove(invocation.getArgument(0)); return null; }).when(repo).deleteById(Mockito.anyString());

        AtomicInteger saveCount = new AtomicInteger(0);
        Mockito.when(repo.save(Mockito.any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            saveCount.incrementAndGet();
            IdempotencyRecord r = invocation.getArgument(0);
            if (saveCount.get() == 1) {
                // claim insert
                IdempotencyRecord copy = IdempotencyRecord.builder()
                        .key(r.getKey())
                        .userId(r.getUserId())
                        .operation(r.getOperation())
                        .responseBody(r.getResponseBody())
                        .fingerprint(r.getFingerprint())
                        .createdAt(Instant.now())
                        .build();
                store.put(r.getKey(), copy);
                return copy;
            }
            // simulate finalize failing
            throw new RuntimeException("db failure during finalize");
        });

        IdempotencyService svc = new IdempotencyService(repo, om, tm);
        repo.deleteAll();

        Supplier<Map> supplier = () -> Map.of("ok", true);
        // execute - finalize will throw and should trigger removeClaim
        Map result = svc.execute(key, "op", 11L, (Class<Map>)(Class) Map.class, supplier);
        // After finalize failure, removeClaim should have been attempted and record deleted
        Optional<IdempotencyRecord> rec = repo.findById(key);
        Assertions.assertTrue(rec.isEmpty(), "After finalize failure the IN_PROGRESS claim should be removed");
    }

    @Test
    public void sameKeySameFingerprint_returnsCached() throws Exception {
        String key = "itest-key-2";

        ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
        IdempotencyRecordRepository repo = Mockito.mock(IdempotencyRecordRepository.class);
        ObjectMapper om = new ObjectMapper();
        PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus dummyStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(tm.getTransaction(Mockito.any())).thenReturn(dummyStatus);
        Mockito.doNothing().when(tm).commit(Mockito.any());
        Mockito.doNothing().when(tm).rollback(Mockito.any());

        Mockito.when(repo.findById(Mockito.anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        Mockito.doAnswer(invocation -> { store.clear(); return null; }).when(repo).deleteAll();
        Mockito.when(repo.save(Mockito.any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord r = invocation.getArgument(0);
            store.put(r.getKey(), r);
            return r;
        });

        IdempotencyService svc = new IdempotencyService(repo, om, tm);

        repo.deleteAll();

        IdempotencyRecord r = IdempotencyRecord.builder()
                .key(key)
                .userId(111L)
                .operation("create-policy")
                .responseBody("{\"policyId\":10,\"policyNumber\":\"POL-1\"}")
                .fingerprint("abc123")
                .createdAt(Instant.now())
                .build();
        store.put(key, r);

        AtomicInteger execCount = new AtomicInteger(0);
        Supplier<Map> supplier = () -> {
            execCount.incrementAndGet();
            return Map.of("ok", true);
        };

        Map result = svc.execute(key, "create-policy", 111L, "abc123", (Class<Map>)(Class) Map.class, supplier);
        Assertions.assertEquals(10, ((Number)result.get("policyId")).intValue());
        Assertions.assertEquals(0, execCount.get(), "Command should not execute when cached response present");
    }

    @Test
    public void sameKeyDifferentUser_conflict() throws Exception {
        String key = "itest-key-3";

        ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
        IdempotencyRecordRepository repo = Mockito.mock(IdempotencyRecordRepository.class);
        ObjectMapper om = new ObjectMapper();
        PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus dummyStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(tm.getTransaction(Mockito.any())).thenReturn(dummyStatus);
        Mockito.doNothing().when(tm).commit(Mockito.any());
        Mockito.doNothing().when(tm).rollback(Mockito.any());

        Mockito.when(repo.findById(Mockito.anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        Mockito.doAnswer(invocation -> { store.clear(); return null; }).when(repo).deleteAll();
        Mockito.when(repo.save(Mockito.any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord r = invocation.getArgument(0);
            store.put(r.getKey(), r);
            return r;
        });

        IdempotencyService svc = new IdempotencyService(repo, om, tm);

        repo.deleteAll();
        IdempotencyRecord r = IdempotencyRecord.builder()
                .key(key)
                .userId(222L)
                .operation("create-policy")
                .responseBody("{\"cached\":true}")
                .createdAt(Instant.now())
                .build();
        store.put(key, r);

        Supplier<Map> supplier = () -> Map.of("ok", true);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            svc.execute(key, "create-policy", 333L, (Class<Map>)(Class) Map.class, supplier);
        });
    }

    @Test
    public void failureDoesNotPoisonKey() throws Exception {
        String key = "itest-key-4";

        ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
        IdempotencyRecordRepository repo = Mockito.mock(IdempotencyRecordRepository.class);
        ObjectMapper om = new ObjectMapper();
        PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus dummyStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(tm.getTransaction(Mockito.any())).thenReturn(dummyStatus);
        Mockito.doNothing().when(tm).commit(Mockito.any());
        Mockito.doNothing().when(tm).rollback(Mockito.any());

        Mockito.when(repo.findById(Mockito.anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        Mockito.doAnswer(invocation -> { store.clear(); return null; }).when(repo).deleteAll();
        Mockito.doAnswer(invocation -> { store.remove(invocation.getArgument(0)); return null; }).when(repo).deleteById(Mockito.anyString());
        Mockito.when(repo.save(Mockito.any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord r = invocation.getArgument(0);
            IdempotencyRecord existing = store.get(r.getKey());
            if (existing == null) {
                IdempotencyRecord copy = IdempotencyRecord.builder()
                        .key(r.getKey())
                        .userId(r.getUserId())
                        .operation(r.getOperation())
                        .responseBody(r.getResponseBody())
                        .fingerprint(r.getFingerprint())
                        .createdAt(Instant.now())
                        .build();
                IdempotencyRecord prev = store.putIfAbsent(r.getKey(), copy);
                if (prev == null) return copy;
                throw new DataIntegrityViolationException("duplicate key");
            } else {
                IdempotencyRecord copy = IdempotencyRecord.builder()
                        .key(r.getKey())
                        .userId(r.getUserId() == null ? existing.getUserId() : r.getUserId())
                        .operation(r.getOperation() == null ? existing.getOperation() : r.getOperation())
                        .responseBody(r.getResponseBody())
                        .fingerprint(r.getFingerprint() == null ? existing.getFingerprint() : r.getFingerprint())
                        .createdAt(existing.getCreatedAt())
                        .build();
                store.put(r.getKey(), copy);
                return copy;
            }
        });

        IdempotencyService svc = new IdempotencyService(repo, om, tm);

        repo.deleteAll();
        Supplier<Map> supplier = () -> { throw new RuntimeException("boom"); };
        Assertions.assertThrows(RuntimeException.class, () -> {
            svc.execute(key, "create-policy", 444L, (Class<Map>)(Class) Map.class, supplier);
        });
        // Claim should be removed so retries can proceed
        Optional<IdempotencyRecord> rec = repo.findById(key);
        Assertions.assertTrue(rec.isEmpty(), "Failed execution should remove the IN_PROGRESS claim");
    }
}