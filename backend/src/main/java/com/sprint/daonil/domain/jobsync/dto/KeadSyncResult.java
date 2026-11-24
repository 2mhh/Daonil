package com.sprint.daonil.domain.jobsync.dto;

/** 수집(Raw 적재) → 정제 → 마감 → Raw 정리 한 번의 결과 */
public record KeadSyncResult(KeadIngestResult ingest, KeadRefineResult refine,
                             KeadCloseResult close, KeadPurgeResult purge) {
}
