package com.sprint.daonil.domain.jobsync.dto;

import java.util.List;

/** API 한 페이지 응답 */
public record KeadJobPage(
        String resultCode,
        String resultMsg,
        int totalCount,
        int pageNo,
        int numOfRows,
        List<KeadJobItem> items) {

    public static final String SUCCESS_CODE = "0000";

    public boolean success() {
        return SUCCESS_CODE.equals(resultCode);
    }
}
