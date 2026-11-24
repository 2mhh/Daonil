package com.sprint.daonil.domain.jobsync.refine;

import com.sprint.daonil.domain.jobposting.dto.JobPostingRequestDto;

import java.util.List;

/**
 * Raw → 서비스 데이터 변환 결과.
 * 공고 필드는 기존 JobPostingRequestDto 를 그대로 사용해 toEntity()/update()를 재사용한다.
 *
 * @param warnings 변환은 됐지만 값이 비거나 어긋나 보정한 항목(직군 미분류, 환경 값 불일치 등)
 */
public record KeadMappedJob(
        JobPostingRequestDto posting,
        String companyName,
        String companyAddress,
        String externalContact,
        List<String> warnings) {
}
