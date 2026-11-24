package com.sprint.daonil.domain.jobsync.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 외부 공고 API 원본(Raw).
 * 정제 규칙이 바뀌어도 API를 다시 호출하지 않고 여기서 재정제할 수 있도록 원문(JSON)을 그대로 보관한다.
 */
@Entity
@Table(name = "job_posting_raw")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "raw_id")
    private Long rawId;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 식별 필드의 SHA-256 (공고 고유 ID가 없어 직접 만든 키) */
    @Column(name = "external_id", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String externalId;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    /** 변경 감지용 내용 해시 */
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RawStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    /** 마지막으로 API 목록에서 확인된 시각 (이후 마감 처리의 기준) */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    public static JobPostingRaw create(String source, String externalId, String payload,
                                       String contentHash, LocalDateTime now) {
        JobPostingRaw raw = new JobPostingRaw();
        raw.source = source;
        raw.externalId = externalId;
        raw.payload = payload;
        raw.contentHash = contentHash;
        raw.status = RawStatus.NEW;
        raw.firstSeenAt = now;
        raw.lastSeenAt = now;
        return raw;
    }

    /** 내용이 바뀌지 않은 채 다시 확인됨 */
    public void markSeen(LocalDateTime now) {
        this.lastSeenAt = now;
    }

    /** 정제 성공 */
    public void markRefined() {
        this.status = RawStatus.REFINED;
        this.errorMessage = null;
    }

    /** 정제 실패 (사유는 500자까지 보관) */
    public void markFailed(String reason) {
        this.status = RawStatus.FAILED;
        this.errorMessage = (reason == null || reason.length() <= 500) ? reason : reason.substring(0, 500);
    }

    /** 내용이 바뀜 → 재정제 대상(NEW)으로 되돌린다 */
    public void changePayload(String payload, String contentHash, LocalDateTime now) {
        this.payload = payload;
        this.contentHash = contentHash;
        this.status = RawStatus.NEW;
        this.errorMessage = null;
        this.lastSeenAt = now;
    }
}
