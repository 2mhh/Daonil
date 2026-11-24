package com.sprint.daonil.domain.jobsync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.daonil.domain.company.entity.Company;
import com.sprint.daonil.domain.company.repository.CompanyRepository;
import com.sprint.daonil.domain.jobposting.entity.JobPosting;
import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import com.sprint.daonil.domain.jobsync.client.KeadIdentity;
import com.sprint.daonil.domain.jobsync.dto.KeadRefineResult;
import com.sprint.daonil.domain.jobsync.entity.JobPostingRaw;
import com.sprint.daonil.domain.jobsync.entity.RawStatus;
import com.sprint.daonil.domain.jobsync.refine.KeadJobMapper;
import com.sprint.daonil.domain.jobsync.refine.KeadMappedJob;
import com.sprint.daonil.domain.jobsync.repository.JobPostingRawRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw(job_posting_raw) → 서비스 테이블(company, job_posting) 정제.
 *
 * 설계 포인트
 *  - Raw 는 id 커서(id > 마지막 id)로 읽는다. 처리하면서 상태를 바꾸므로 OFFSET 페이징을 쓰면 일부를 건너뛴다.
 *  - 한 건씩 별도 트랜잭션으로 처리한다. 한 건이 실패해도 나머지는 계속되고, 실패는 raw 에 사유와 함께 남긴다.
 *  - 같은 Raw 를 다시 정제해도 결과가 같다(멱등): 공고는 (source, external_id)로 찾아 갱신한다.
 */
@Slf4j
@Service
public class KeadJobRefineService {

    private static final List<RawStatus> TARGET_STATUSES = List.of(RawStatus.NEW, RawStatus.FAILED);

    private final JobPostingRawRepository rawRepository;
    private final CompanyRepository companyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final KeadSystemAccount systemAccount;
    private final KeadJobMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public KeadJobRefineService(JobPostingRawRepository rawRepository,
                                CompanyRepository companyRepository,
                                JobPostingRepository jobPostingRepository,
                                KeadSystemAccount systemAccount,
                                KeadJobMapper mapper,
                                ObjectMapper objectMapper,
                                TransactionTemplate transactionTemplate) {
        this.rawRepository = rawRepository;
        this.companyRepository = companyRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.systemAccount = systemAccount;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public KeadRefineResult refineAll() {
        int processed = 0;
        int created = 0;
        int updated = 0;
        int failed = 0;
        int withWarnings = 0;

        long lastId = 0L;
        while (true) {
            List<JobPostingRaw> batch = rawRepository
                    .findTop100BySourceAndStatusInAndRawIdGreaterThanOrderByRawIdAsc(
                            KeadJobIngestService.SOURCE, TARGET_STATUSES, lastId);
            if (batch.isEmpty()) {
                break;
            }
            for (JobPostingRaw raw : batch) {
                Long rawId = raw.getRawId();
                lastId = rawId;
                processed++;
                try {
                    Outcome outcome = transactionTemplate.execute(status -> refineOne(rawId));
                    if (outcome.created()) {
                        created++;
                    } else {
                        updated++;
                    }
                    if (outcome.warnings() > 0) {
                        withWarnings++;
                    }
                } catch (Exception e) {
                    failed++;
                    String reason = describe(e);
                    log.warn("KEAD 정제 실패 (rawId={}): {}", rawId, reason);
                    markFailedQuietly(rawId, reason);
                }
            }
        }

        KeadRefineResult result = new KeadRefineResult(processed, created, updated, failed, withWarnings);
        log.info("KEAD 정제: {}", result);
        return result;
    }

    /** 한 건 정제 (호출하는 쪽의 트랜잭션 안에서 실행). 테스트에서 직접 호출할 수 있도록 package-private */
    Outcome refineOne(Long rawId) {
        JobPostingRaw raw = rawRepository.findById(rawId)
                .orElseThrow(() -> new IllegalStateException("Raw 를 찾을 수 없습니다: " + rawId));

        Map<String, String> payload = parsePayload(raw.getPayload());
        KeadMappedJob mapped = mapper.map(payload);
        Company company = findOrCreateCompany(mapped);

        Optional<JobPosting> existing = jobPostingRepository
                .findBySourceAndExternalId(KeadJobIngestService.SOURCE, raw.getExternalId());

        boolean created;
        if (existing.isPresent()) {
            JobPosting posting = existing.get();
            posting.update(mapped.posting());   // 영속 상태 → 더티 체킹으로 UPDATE
            posting.assignExternal(KeadJobIngestService.SOURCE, raw.getExternalId(), mapped.externalContact());
            created = false;
        } else {
            JobPosting posting = mapped.posting().toEntity(company);
            posting.assignExternal(KeadJobIngestService.SOURCE, raw.getExternalId(), mapped.externalContact());
            jobPostingRepository.save(posting);
            created = true;
        }

        raw.markRefined();
        return new Outcome(created, mapped.warnings().size());
    }

    private Company findOrCreateCompany(KeadMappedJob mapped) {
        String businessNumber = KeadIdentity.companyKey(mapped.companyName(), mapped.companyAddress());
        return companyRepository.findFirstByBusinessNumber(businessNumber)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    return companyRepository.save(Company.builder()
                            .member(systemAccount.getOrCreate())
                            .businessNumber(businessNumber)
                            .companyName(mapped.companyName())
                            .address(mapped.companyAddress())
                            .isDeleted(false)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                });
    }

    private Map<String, String> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Raw 원문 JSON 파싱 실패", e);
        }
    }

    /** 실패 표시는 롤백된 트랜잭션과 별개의 새 트랜잭션에서 남긴다 */
    private void markFailedQuietly(Long rawId, String reason) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    rawRepository.findById(rawId).ifPresent(raw -> raw.markFailed(reason)));
        } catch (Exception e) {
            log.error("KEAD 실패 상태 기록 실패 (rawId={}): {}", rawId, e.getClass().getSimpleName());
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        String text = e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() <= 400 ? text : text.substring(0, 400);
    }

    record Outcome(boolean created, int warnings) {
    }
}
