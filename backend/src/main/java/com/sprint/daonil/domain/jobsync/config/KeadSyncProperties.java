package com.sprint.daonil.domain.jobsync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 동기화 설정 (환경변수로 덮어쓴다).
 *  - KEAD_SYNC_ENABLED=true            : 스케줄러 켜기 (기본 꺼짐 — 로컬·테스트에서 실수로 실행되지 않도록)
 *  - KEAD_SYNC_CRON                    : 실행 시각 (기본 매일 03:00 KST)
 *  - KEAD_SYNC_RUN_ON_STARTUP=true     : 앱 시작 시 1회 실행 (수동 확인용)
 *  - KEAD_SYNC_MAX_CLOSE_RATIO         : 한 번에 닫을 수 있는 최대 비율 (기본 0.3)
 *  - KEAD_SYNC_MIN_CLOSE_COUNT         : 비율과 무관하게 항상 허용하는 건수 (기본 10)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kead.sync")
public class KeadSyncProperties {

    /**
     * 마감 안전장치: 사라진 공고가 "열려 있는 공고의 이 비율"을 넘으면 API 장애·부분 응답으로 보고 마감을 건너뛴다.
     */
    private double maxCloseRatio = 0.3;

    /** 소규모에서는 비율이 너무 작아지므로 이 건수까지는 항상 허용한다 */
    private int minCloseCount = 10;

    /**
     * Raw 보관 기간(일). 마지막으로 API 목록에서 확인된 지 이 기간이 지났고 연결된 공고가 마감된 Raw 를 삭제한다.
     * 0 이하이면 삭제하지 않는다. (환경변수 KEAD_SYNC_RAW_RETENTION_DAYS)
     */
    private int rawRetentionDays = 90;

    /** 마감 한도 = max(minCloseCount, ceil(열린 공고 수 × maxCloseRatio)) */
    public long closeLimit(long openCount) {
        return Math.max(minCloseCount, (long) Math.ceil(openCount * maxCloseRatio));
    }
}
