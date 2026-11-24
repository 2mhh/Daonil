package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeadIdentityTest {

    private static KeadJobItem item(String busplaName, String jobNm, String termDate, String compAddr,
                                    String salary, String rno) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("busplaName", busplaName);
        fields.put("jobNm", jobNm);
        fields.put("termDate", termDate);
        fields.put("compAddr", compAddr);
        fields.put("salary", salary);
        fields.put("rno", rno);
        fields.put("rnum", rno);
        return new KeadJobItem(fields);
    }

    @Test
    void 해시는_64자리_16진수다() {
        KeadJobItem a = item("A사", "청소원", "2026-11-01~2026-12-01", "서울", "1,000", "1");

        assertThat(KeadIdentity.externalId(a)).matches("[0-9a-f]{64}");
        assertThat(KeadIdentity.contentHash(a)).matches("[0-9a-f]{64}");
    }

    @Test
    void 급여가_바뀌어도_같은_공고로_식별되고_내용_해시는_달라진다() {
        KeadJobItem before = item("A사", "청소원", "2026-11-01~2026-12-01", "서울", "1,000", "1");
        KeadJobItem after = item("A사", "청소원", "2026-11-01~2026-12-01", "서울", "2,000", "1");

        assertThat(KeadIdentity.externalId(after)).isEqualTo(KeadIdentity.externalId(before));
        assertThat(KeadIdentity.contentHash(after)).isNotEqualTo(KeadIdentity.contentHash(before));
    }

    @Test
    void 응답_내_순번만_달라지면_내용_해시는_같다() {
        KeadJobItem first = item("A사", "청소원", "2026-11-01~2026-12-01", "서울", "1,000", "1");
        KeadJobItem moved = item("A사", "청소원", "2026-11-01~2026-12-01", "서울", "1,000", "57");

        assertThat(KeadIdentity.contentHash(moved)).isEqualTo(KeadIdentity.contentHash(first));
    }

    @Test
    void 식별_필드가_하나라도_다르면_다른_공고다() {
        KeadJobItem a = item("A사", "청소원", "2026-11-01~2026-12-01", "서울", "1,000", "1");
        KeadJobItem otherPeriod = item("A사", "청소원", "2026-11-01~2026-12-31", "서울", "1,000", "1");
        KeadJobItem otherJob = item("A사", "사무 보조원", "2026-11-01~2026-12-01", "서울", "1,000", "1");

        assertThat(KeadIdentity.externalId(otherPeriod)).isNotEqualTo(KeadIdentity.externalId(a));
        assertThat(KeadIdentity.externalId(otherJob)).isNotEqualTo(KeadIdentity.externalId(a));
    }

    @Test
    void 앞뒤_공백과_연속_공백은_같은_값으로_취급한다() {
        KeadJobItem clean = item("A사", "건물 청소원", "2026-11-01~2026-12-01", "서울 송파구", "1,000", "1");
        KeadJobItem spaced = item("  A사 ", "건물   청소원", "2026-11-01~2026-12-01", "서울  송파구", "1,000", "1");

        assertThat(KeadIdentity.externalId(spaced)).isEqualTo(KeadIdentity.externalId(clean));
        assertThat(KeadIdentity.contentHash(spaced)).isEqualTo(KeadIdentity.contentHash(clean));
    }
}
