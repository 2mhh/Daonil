package com.sprint.daonil.domain.jobsync.refine;

import com.sprint.daonil.domain.jobposting.dto.JobPostingRequestDto;
import com.sprint.daonil.domain.jobsync.client.KeadJobXmlParser;
import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeadJobMapperTest {

    private final KeadJobMapper mapper = new KeadJobMapper();

    /** 실제 API 응답에서 가져온 샘플 2건 */
    private List<KeadJobItem> sampleItems() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/kead/job_list_env2_sample.xml")) {
            return new KeadJobXmlParser().parse(in.readAllBytes()).items();
        }
    }

    private static Map<String, String> base() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("busplaName", "테스트(주)");
        m.put("jobNm", "사무 보조원(일반사업체)");
        m.put("compAddr", "서울특별시 종로구 종로 1");
        m.put("termDate", "2026-11-01~2026-12-01");
        m.put("empType", "계약직");
        m.put("salary", "1,000,000");
        m.put("salaryType", "월급");
        return m;
    }

    @Test
    void 실제_샘플_공고를_변환한다() throws IOException {
        KeadMappedJob mapped = mapper.map(sampleItems().get(0).fields());
        JobPostingRequestDto p = mapped.posting();

        assertThat(p.getTitle()).isEqualTo("데이터 및 자료 모니터 요원(기사, 뉴스 등 포함)");
        assertThat(mapped.companyName()).startsWith("쿠팡풀필먼트서비스");
        assertThat(p.getWorkRegion()).isEqualTo("서울 송파구");
        assertThat(p.getEmploymentType()).isEqualTo("계약직");
        assertThat(p.getSalary()).isEqualTo(1_080_000);
        assertThat(p.getSalaryType()).isEqualTo("월급");
        assertThat(p.getApplicationStartDate()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(p.getApplicationEndDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(mapped.externalContact()).isEqualTo("한국장애인고용공단 전남지사 1588-1519");
        assertThat(p.getContent()).isEqualTo("[한국장애인고용공단 구인정보] 문의: 한국장애인고용공단 전남지사 1588-1519");
        assertThat(p.getQualification()).isEqualTo("학력: 무관, 경력: 무관, 채용구분: 무관");
        // 대분류는 소분류에서 파생된다
        assertThat(p.getMainCategory()).isEqualTo(KeadMainCategory.of(p.getSubCategory()));
    }

    @Test
    void 작업환경은_있는_것만_채우고_없으면_null이다() throws IOException {
        JobPostingRequestDto withEnv = mapper.map(sampleItems().get(0).fields()).posting();
        assertThat(withEnv.getEnvEyesight()).isEqualTo("아주 작은 글씨를 읽을 수 있음");
        assertThat(withEnv.getEnvLiftPower()).isEqualTo("5Kg 이내의 물건을 다룰 수 있음");
        assertThat(withEnv.getEnvLstnTalk()).isEqualTo("듣고 말하기에 어려움 없음");
        assertThat(withEnv.getEnvStndWalk()).isEqualTo("서거나 걷는 일 어려움");
        assertThat(withEnv.getEnvBothHands()).isNull();   // 이 공고에는 없는 항목
        assertThat(withEnv.getEnvHandWork()).isNull();

        KeadMappedJob withoutEnv = mapper.map(sampleItems().get(1).fields());
        assertThat(withoutEnv.posting().getEnvEyesight()).isNull();
        assertThat(withoutEnv.warnings()).noneMatch(w -> w.startsWith("근무환경"));   // 값이 없는 것은 경고가 아니다
    }

    @Test
    void 청소원_공고는_서비스_종사자로_분류된다() throws IOException {
        JobPostingRequestDto p = mapper.map(sampleItems().get(1).fields()).posting();

        assertThat(p.getSubCategory()).isEqualTo("청소 및 기타 개인서비스직");
        assertThat(p.getMainCategory()).isEqualTo("서비스 종사자");
        assertThat(p.getSalary()).isEqualTo(14_000_000);
        assertThat(p.getSalaryType()).isEqualTo("연봉");
    }

    @Test
    void 사무_보조원은_사무_종사자로_분류된다() {
        JobPostingRequestDto p = mapper.map(base()).posting();

        assertThat(p.getSubCategory()).isEqualTo("경영·행정·사무직");
        assertThat(p.getMainCategory()).isEqualTo("사무 종사자");
    }

    @Test
    void 고용_형태를_화면_선택지에_맞춘다() {
        assertThat(KeadJobMapper.employmentType("상용직")).isEqualTo("정규직");
        assertThat(KeadJobMapper.employmentType("시간제")).isEqualTo("파트타임");
        assertThat(KeadJobMapper.employmentType("계약직")).isEqualTo("계약직");
        assertThat(KeadJobMapper.employmentType("기타형태")).isEqualTo("기타형태");
        assertThat(KeadJobMapper.employmentType(null)).isNull();
    }

    @Test
    void 경력_표기를_정규화한다() {
        assertThat(KeadJobMapper.career("무관")).isEqualTo("무관");
        assertThat(KeadJobMapper.career("0년0개월")).isEqualTo("무관");
        assertThat(KeadJobMapper.career("0년개월")).isEqualTo("무관");
        assertThat(KeadJobMapper.career("3년0개월")).isEqualTo("3년 이상");
        assertThat(KeadJobMapper.career("0년6개월")).isEqualTo("6개월 이상");
        assertThat(KeadJobMapper.career("1년6개월")).isEqualTo("1년 6개월 이상");
        assertThat(KeadJobMapper.career("이해할 수 없는 값")).isEqualTo("이해할 수 없는 값");
        assertThat(KeadJobMapper.career(null)).isNull();
    }

    @Test
    void 자격_조건을_한_문장으로_합친다() {
        Map<String, String> m = base();
        m.put("reqEduc", "고졸");
        m.put("reqCareer", "3년0개월");
        m.put("enterType", "경력");
        m.put("reqMajor", "컴퓨터공학");
        m.put("reqLicens", "미용사(네일)/");

        assertThat(KeadJobMapper.qualification(m))
                .isEqualTo("학력: 고졸, 경력: 3년 이상, 채용구분: 경력, 전공: 컴퓨터공학, 자격증: 미용사(네일)");
    }

    @Test
    void 급여_형식이_이상하면_null이고_경고를_남긴다() {
        Map<String, String> m = base();
        m.put("salary", "협의");

        KeadMappedJob mapped = mapper.map(m);

        assertThat(mapped.posting().getSalary()).isNull();
        assertThat(mapped.warnings()).anyMatch(w -> w.startsWith("급여 형식 이상"));
    }

    @Test
    void 급여가_int_범위를_넘으면_null이고_경고를_남긴다() {
        Map<String, String> m = base();
        m.put("salary", "99,999,999,999");

        KeadMappedJob mapped = mapper.map(m);

        assertThat(mapped.posting().getSalary()).isNull();
        assertThat(mapped.warnings()).anyMatch(w -> w.startsWith("급여 범위 초과"));
    }

    @Test
    void 환경_값이_선택지와_다르면_null이고_경고를_남긴다() {
        Map<String, String> m = base();
        m.put("envEyesight", "알 수 없는 표현");

        KeadMappedJob mapped = mapper.map(m);

        assertThat(mapped.posting().getEnvEyesight()).isNull();
        assertThat(mapped.warnings()).anyMatch(w -> w.contains("envEyesight"));
    }

    @Test
    void 접수기간_형식이_이상하면_날짜는_null이고_경고를_남긴다() {
        Map<String, String> m = base();
        m.put("termDate", "상시채용");

        KeadMappedJob mapped = mapper.map(m);

        assertThat(mapped.posting().getApplicationStartDate()).isNull();
        assertThat(mapped.posting().getApplicationEndDate()).isNull();
        assertThat(mapped.warnings()).anyMatch(w -> w.startsWith("접수기간"));
    }

    @Test
    void 직무명이나_사업장명이_없으면_변환할_수_없다() {
        Map<String, String> noJob = base();
        noJob.remove("jobNm");
        Map<String, String> noCompany = base();
        noCompany.remove("busplaName");

        assertThatThrownBy(() -> mapper.map(noJob)).isInstanceOf(KeadMappingException.class).hasMessageContaining("jobNm");
        assertThatThrownBy(() -> mapper.map(noCompany)).isInstanceOf(KeadMappingException.class).hasMessageContaining("busplaName");
    }

    @Test
    void 긴_값은_컬럼_길이에_맞춰_자른다() {
        Map<String, String> m = base();
        m.put("busplaName", "가".repeat(150));
        m.put("jobNm", "나".repeat(200));

        KeadMappedJob mapped = mapper.map(m);

        assertThat(mapped.companyName()).hasSize(100);
        assertThat(mapped.posting().getTitle()).hasSize(150);
    }
}
