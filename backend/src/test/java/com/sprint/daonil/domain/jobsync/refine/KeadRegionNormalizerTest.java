package com.sprint.daonil.domain.jobsync.refine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class KeadRegionNormalizerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "서울특별시 종로구 종로 51 한국맥도날드 | 서울 종로구",
            "경기도 안양시 만안구 덕천로 45 3층 | 경기 안양시",
            "충청남도 천안시 서북구 공단로 1 | 충남 천안시",
            "충청북도 청주시 흥덕구 1산단로 | 충북 청주시",
            "부산광역시 해운대구 센텀로 1 | 부산 해운대구",
            "제주특별자치도 제주시 연동 1 | 제주 제주시",
            "경상북도 구미시 산단로 3 | 경북 구미시",
            "경상남도 창원시 성산구 1 | 경남 창원시",
            "세종특별자치시 한솔동 1 | 세종",
            "인천광역시 남동구 1 | 인천 남동구",
    })
    void 시도는_짧은_이름으로_통일하고_시군구를_붙인다(String address, String expected) {
        assertThat(KeadRegionNormalizer.normalize(address)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "전라북도 익산시 1 | 전북 익산시",
            "전북특별자치도 전주시 완산구 1 | 전북 전주시",
            "강원도 원주시 1 | 강원 원주시",
            "강원특별자치도 춘천시 1 | 강원 춘천시",
    })
    void 표기가_바뀐_지역은_같은_이름으로_합친다(String address, String expected) {
        assertThat(KeadRegionNormalizer.normalize(address)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "전남광주통합특별시 광산구 동곡로 | 광주 광산구",
            "전남광주통합특별시 서구 천변우하로 | 광주 서구",
            "전남광주통합특별시 화순군 화순읍 | 전남 화순군",
            "전남광주통합특별시 목포시 하당로205번길 | 전남 목포시",
    })
    void 전남광주통합특별시는_자치구면_광주_시군이면_전남이다(String address, String expected) {
        assertThat(KeadRegionNormalizer.normalize(address)).isEqualTo(expected);
    }

    @Test
    void 비어_있으면_null이다() {
        assertThat(KeadRegionNormalizer.normalize(null)).isNull();
        assertThat(KeadRegionNormalizer.normalize("   ")).isNull();
    }

    @Test
    void 알_수_없는_지역은_첫_단어를_그대로_쓴다() {
        assertThat(KeadRegionNormalizer.normalize("해외 어딘가 1")).isEqualTo("해외");
    }
}
