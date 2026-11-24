package com.sprint.daonil.domain.jobsync.dto;

import java.util.List;

/** 전체 페이지 수집 결과 */
public record KeadFetchResult(int totalCount, List<KeadJobItem> items) {

    /**
     * 수집한 건수가 API가 알려준 전체 건수와 같은가.
     * (이 값이 false이거나 totalCount가 0이면 "완전한 수집"으로 보지 않고, 마감 처리 등에 쓰지 않는다)
     */
    public boolean complete() {
        return totalCount > 0 && items.size() == totalCount;
    }
}
