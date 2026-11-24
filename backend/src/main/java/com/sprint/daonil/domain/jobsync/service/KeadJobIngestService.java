package com.sprint.daonil.domain.jobsync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.daonil.domain.jobsync.client.KeadIdentity;
import com.sprint.daonil.domain.jobsync.client.KeadJobApiClient;
import com.sprint.daonil.domain.jobsync.dto.KeadFetchResult;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import com.sprint.daonil.domain.jobsync.entity.JobPostingRaw;
import com.sprint.daonil.domain.jobsync.repository.JobPostingRawRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 공고 API → job_posting_raw 적재.
 *
 * 설계 포인트
 *  - HTTP 호출은 트랜잭션 밖에서 한다. (느린 외부 호출 동안 DB 커넥션을 쥐지 않기 위해)
 *  - 같은 데이터를 다시 적재해도 결과가 같다(멱등): 식별키 기준 upsert, 내용이 같으면 확인 시각만 갱신.
 *  - 내용이 바뀌면 status를 NEW로 되돌려 다음 정제 때 반영한다.
 */
@Slf4j
@Service
public class KeadJobIngestService {

    public static final String SOURCE = "KEAD_JOB2";

    private static final int IN_CHUNK = 500;

    private final KeadJobApiClient client;
    private final JobPostingRawRepository rawRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public KeadJobIngestService(KeadJobApiClient client,
                                JobPostingRawRepository rawRepository,
                                ObjectMapper objectMapper,
                                TransactionTemplate transactionTemplate) {
        this.client = client;
        this.rawRepository = rawRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** API 호출(트랜잭션 밖) → 저장(트랜잭션 안) */
    public KeadIngestResult ingest() {
        KeadFetchResult fetched = client.fetchAll();
        // 초 단위로 맞춘다: DB(DATETIME)는 소수 초를 반올림해 저장하므로, 그대로 비교하면
        // "이번에 확인한 행"이 "이번 실행보다 오래됨"으로 잘못 판단될 수 있다 (마감 판단의 기준 시각)
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        KeadIngestResult result = transactionTemplate.execute(status -> persist(fetched, startedAt));
        log.info("KEAD Raw 적재: {}", result);
        return result;
    }

    /** 테스트에서 직접 호출할 수 있도록 package-private */
    KeadIngestResult persist(KeadFetchResult fetched, LocalDateTime now) {
        List<KeadJobItem> items = fetched.items();

        // 1) 식별키 기준으로 정리: 같은 공고가 두 번 내려오면 하나로 합친다
        Map<String, Prepared> unique = new LinkedHashMap<>();
        int duplicatesMerged = 0;
        int conflicts = 0;
        for (KeadJobItem item : items) {
            String externalId = KeadIdentity.externalId(item);
            String contentHash = KeadIdentity.contentHash(item);
            Prepared existing = unique.get(externalId);
            if (existing == null) {
                unique.put(externalId, new Prepared(item, contentHash));
            } else if (existing.contentHash().equals(contentHash)) {
                duplicatesMerged++;
            } else {
                conflicts++;   // 식별키는 같은데 내용이 다름: 첫 항목만 유지하고 기록
                log.warn("KEAD 식별키는 같지만 내용이 다른 항목 발견 (externalId={})", externalId);
            }
        }

        // 2) 기존 행 조회 (IN 조건은 청크로 나눠서)
        Map<String, JobPostingRaw> existing = new HashMap<>();
        List<String> ids = new ArrayList<>(unique.keySet());
        for (int from = 0; from < ids.size(); from += IN_CHUNK) {
            List<String> chunk = ids.subList(from, Math.min(from + IN_CHUNK, ids.size()));
            for (JobPostingRaw row : rawRepository.findBySourceAndExternalIdIn(SOURCE, chunk)) {
                existing.put(row.getExternalId(), row);
            }
        }

        // 3) upsert
        List<JobPostingRaw> toInsert = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (Map.Entry<String, Prepared> entry : unique.entrySet()) {
            String externalId = entry.getKey();
            Prepared prepared = entry.getValue();
            JobPostingRaw row = existing.get(externalId);
            if (row == null) {
                toInsert.add(JobPostingRaw.create(SOURCE, externalId, toJson(prepared.item()), prepared.contentHash(), now));
                inserted++;
            } else if (!row.getContentHash().equals(prepared.contentHash())) {
                row.changePayload(toJson(prepared.item()), prepared.contentHash(), now);   // 영속 상태라 더티 체킹으로 UPDATE
                updated++;
            } else {
                row.markSeen(now);
                unchanged++;
            }
        }
        if (!toInsert.isEmpty()) {
            rawRepository.saveAll(toInsert);
        }

        return new KeadIngestResult(now, fetched.totalCount(), items.size(),
                duplicatesMerged, conflicts, inserted, updated, unchanged, fetched.complete());
    }

    private String toJson(KeadJobItem item) {
        try {
            return objectMapper.writeValueAsString(item.fields());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("공고 원문을 JSON으로 변환하지 못했습니다.", e);
        }
    }

    private record Prepared(KeadJobItem item, String contentHash) {
    }
}
