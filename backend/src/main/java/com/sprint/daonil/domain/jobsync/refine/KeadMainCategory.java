package com.sprint.daonil.domain.jobsync.refine;

import java.util.Map;

/**
 * 소분류(33개 직업군) → 대분류(9개). daon-il.sql 의 main_category 일괄 UPDATE 규칙과 같다.
 * 소분류 이름은 JobCategoryClassifier 의 키와 동일하다.
 */
public final class KeadMainCategory {

    private static final Map<String, String> SUB_TO_MAIN = Map.ofEntries(
            Map.entry("관리직(임원·부서장)", "관리자"),

            Map.entry("경영·행정·사무직", "사무 종사자"),

            Map.entry("돌봄 서비스직(간병·육아)", "서비스 종사자"),
            Map.entry("미용·예식 서비스직", "서비스 종사자"),
            Map.entry("스포츠·레크리에이션직", "서비스 종사자"),
            Map.entry("여행·숙박·오락 서비스직", "서비스 종사자"),
            Map.entry("음식 서비스직", "서비스 종사자"),
            Map.entry("경호·경비직", "서비스 종사자"),
            Map.entry("청소 및 기타 개인서비스직", "서비스 종사자"),

            Map.entry("영업·판매직", "판매 종사자"),

            Map.entry("정보통신 연구개발직 및 공학기술직", "전문가 및 관련 종사자"),
            Map.entry("제조 연구개발직 및 공학기술직", "전문가 및 관련 종사자"),
            Map.entry("건설·채굴 연구개발직 및 공학기술직", "전문가 및 관련 종사자"),
            Map.entry("자연·생명과학 연구직", "전문가 및 관련 종사자"),
            Map.entry("인문·사회과학 연구직", "전문가 및 관련 종사자"),
            Map.entry("법률직", "전문가 및 관련 종사자"),
            Map.entry("사회복지·종교직", "전문가 및 관련 종사자"),
            Map.entry("교육직", "전문가 및 관련 종사자"),
            Map.entry("금융·보험직", "전문가 및 관련 종사자"),
            Map.entry("보건·의료직", "전문가 및 관련 종사자"),
            Map.entry("예술·디자인·방송직", "전문가 및 관련 종사자"),

            Map.entry("건설·채굴직", "기능원 및 관련 기능 종사자"),
            Map.entry("기계 설치·정비·생산직", "기능원 및 관련 기능 종사자"),
            Map.entry("금속·재료 설치·정비·생산직(판금·단조·주조·용접·도장 등)", "기능원 및 관련 기능 종사자"),
            Map.entry("전기·전자 설치·정비·생산직", "기능원 및 관련 기능 종사자"),
            Map.entry("정보통신 설치·정비직", "기능원 및 관련 기능 종사자"),
            Map.entry("화학·환경 설치·정비·생산직", "기능원 및 관련 기능 종사자"),
            Map.entry("인쇄·목재·공예 및 기타 설치·정비·생산직", "기능원 및 관련 기능 종사자"),

            Map.entry("식품 가공·생산직", "장치·기계조작 및 조립 종사자"),
            Map.entry("섬유·의복 생산직", "장치·기계조작 및 조립 종사자"),
            Map.entry("운전·운송직", "장치·기계조작 및 조립 종사자"),

            Map.entry("농림어업직", "농림어업 숙련 종사자"),

            Map.entry("제조 단순직", "단순노무 종사자"));

    private KeadMainCategory() {
    }

    /** 알 수 없는 소분류면 null */
    public static String of(String subCategory) {
        return subCategory == null ? null : SUB_TO_MAIN.get(subCategory);
    }

    public static int size() {
        return SUB_TO_MAIN.size();
    }
}
