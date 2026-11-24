package com.sprint.daonil.domain.jobsync.refine;

import java.util.Map;

/**
 * 사업장 주소 → 화면 지역 필터가 쓰는 짧은 지역명 + 시·군·구.
 * 예) "서울특별시 종로구 종로 51 ..." → "서울 종로구"
 *
 * 화면의 지역 필터(서울, 경기, 충남 …)는 work_region 에 대한 LIKE 검색이므로 짧은 이름을 앞에 둔다.
 * 같은 지역이 시기에 따라 다르게 표기되는 경우(전라북도/전북특별자치도, 강원도/강원특별자치도 등)를 하나로 통일한다.
 */
public final class KeadRegionNormalizer {

    private static final Map<String, String> SIDO = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("서울", "서울"),
            Map.entry("부산광역시", "부산"), Map.entry("부산", "부산"),
            Map.entry("대구광역시", "대구"), Map.entry("대구", "대구"),
            Map.entry("인천광역시", "인천"), Map.entry("인천", "인천"),
            Map.entry("광주광역시", "광주"), Map.entry("광주", "광주"),
            Map.entry("대전광역시", "대전"), Map.entry("대전", "대전"),
            Map.entry("울산광역시", "울산"), Map.entry("울산", "울산"),
            Map.entry("세종특별자치시", "세종"), Map.entry("세종", "세종"),
            Map.entry("경기도", "경기"), Map.entry("경기", "경기"),
            Map.entry("강원도", "강원"), Map.entry("강원특별자치도", "강원"), Map.entry("강원", "강원"),
            Map.entry("충청북도", "충북"), Map.entry("충북", "충북"),
            Map.entry("충청남도", "충남"), Map.entry("충남", "충남"),
            Map.entry("전라북도", "전북"), Map.entry("전북특별자치도", "전북"), Map.entry("전북", "전북"),
            Map.entry("전라남도", "전남"), Map.entry("전남", "전남"),
            Map.entry("경상북도", "경북"), Map.entry("경북", "경북"),
            Map.entry("경상남도", "경남"), Map.entry("경남", "경남"),
            Map.entry("제주특별자치도", "제주"), Map.entry("제주", "제주"));

    /** 광주와 전남이 합쳐진 행정구역명. 두 번째 단어로 광주(자치구) / 전남(시·군)을 구분한다. */
    private static final String GWANGJU_JEONNAM = "전남광주통합특별시";

    private KeadRegionNormalizer() {
    }

    /** 주소가 비어 있으면 null */
    public static String normalize(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        String first = tokens[0];
        String second = tokens.length > 1 ? tokens[1] : null;

        String sido;
        if (GWANGJU_JEONNAM.equals(first)) {
            sido = (second != null && second.endsWith("구")) ? "광주" : "전남";
        } else {
            sido = SIDO.getOrDefault(first, first);
        }

        // 두 번째 단어가 시·군·구일 때만 붙인다 (도로명 등이 붙는 것을 막는다)
        if (second != null && (second.endsWith("시") || second.endsWith("군") || second.endsWith("구"))) {
            return sido + " " + second;
        }
        return sido;
    }
}
