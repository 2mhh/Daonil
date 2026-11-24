package com.sprint.daonil.domain.jobsync.refine;

import com.sprint.daonil.domain.ai.util.JobCategoryClassifier;
import com.sprint.daonil.domain.jobposting.dto.JobPostingRequestDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KEAD API 원본(필드 이름→값) → 다온일 공고 데이터.
 * 순수 변환 로직만 담아 DB 없이 테스트할 수 있다.
 */
@Component
public class KeadJobMapper {

    private static final Set<String> SALARY_TYPES = Set.of("연봉", "월급", "일급", "시급");

    /** 화면(공고 등록·필터)이 쓰는 근무 환경 선택지. API 값과 같으면 그대로 저장한다. */
    private static final Map<String, Set<String>> ENV_VOCAB = Map.of(
            "envBothHands", Set.of("양손작업 가능", "한손작업 가능", "한손보조작업 가능"),
            "envEyesight", Set.of("아주 작은 글씨를 읽을 수 있음", "일상적 활동 가능", "비교적 큰 인쇄물을 읽을 수 있음"),
            "envHandWork", Set.of("정밀한 작업가능", "작은 물품 조립가능", "큰 물품 조립가능"),
            "envLiftPower", Set.of("20Kg 이상의 물건을 다룰 수 있음", "5Kg 이내의 물건을 다룰 수 있음", "5~20Kg의 물건을 다룰 수 있음"),
            "envLstnTalk", Set.of("듣고 말하기에 어려움 없음", "간단한 듣고 말하기 가능", "듣고 말하는 작업 어려움"),
            "envStndWalk", Set.of("오랫동안 가능", "일부 서서하는 작업 가능", "서거나 걷는 일 어려움"));

    private static final Pattern CAREER = Pattern.compile("^(\\d*)년(\\d*)개월?$");

    public KeadMappedJob map(Map<String, String> payload) {
        List<String> warnings = new ArrayList<>();

        String jobNm = value(payload, "jobNm");
        String companyName = value(payload, "busplaName");
        if (jobNm == null) {
            throw new KeadMappingException("직무명(jobNm)이 없습니다.");
        }
        if (companyName == null) {
            throw new KeadMappingException("사업장명(busplaName)이 없습니다.");
        }

        // 직군: 직무명으로 33개 직업군 분류 → 가장 높은 가중치
        String subCategory = classify(jobNm);
        String mainCategory = KeadMainCategory.of(subCategory);
        if (subCategory == null) {
            warnings.add("직군 미분류");
        }

        // 접수 기간 "2026-11-17~2026-12-01"
        LocalDate start = null;
        LocalDate end = null;
        String termDate = value(payload, "termDate");
        if (termDate != null) {
            String[] range = termDate.split("~", -1);
            start = parseDate(range[0]);
            end = range.length > 1 ? parseDate(range[1]) : null;
            if (start == null || end == null) {
                warnings.add("접수기간 형식 이상: " + termDate);
            }
        } else {
            warnings.add("접수기간 없음");
        }

        // 급여
        Integer salary = parseSalary(value(payload, "salary"), warnings);
        String salaryType = value(payload, "salaryType");
        if (salaryType != null && !SALARY_TYPES.contains(salaryType)) {
            warnings.add("급여 유형 미지원: " + salaryType);
            salaryType = null;
        }

        String contact = joinNonNull(" ", value(payload, "regagnName"), value(payload, "cntctNo"));
        contact = truncate(contact, 100);

        String content = "[한국장애인고용공단 구인정보]" + (contact != null ? " 문의: " + contact : "");

        JobPostingRequestDto posting = JobPostingRequestDto.builder()
                .title(truncate(jobNm, 150))
                .mainCategory(mainCategory)
                .subCategory(subCategory)
                .employmentType(employmentType(value(payload, "empType")))
                .workRegion(truncate(KeadRegionNormalizer.normalize(value(payload, "compAddr")), 100))
                .salary(salary)
                .salaryType(salaryType)
                .recruitCount(null)
                .qualification(qualification(payload))
                .applicationStartDate(start)
                .applicationEndDate(end)
                .content(truncate(content, 225))
                .workHours(null)
                .envBothHands(env(payload, "envBothHands", warnings))
                .envEyesight(env(payload, "envEyesight", warnings))
                .envHandWork(env(payload, "envHandWork", warnings))
                .envLiftPower(env(payload, "envLiftPower", warnings))
                .envLstnTalk(env(payload, "envLstnTalk", warnings))
                .envStndWalk(env(payload, "envStndWalk", warnings))
                .build();

        return new KeadMappedJob(posting,
                truncate(companyName, 100),
                truncate(value(payload, "compAddr"), 255),
                contact,
                List.copyOf(warnings));
    }

    // ----- 변환 규칙 -----

    /** 상용직 → 정규직, 시간제 → 파트타임 (화면의 고용 형태 선택지에 맞춤). 그 외는 원문 유지 */
    static String employmentType(String empType) {
        if (empType == null) {
            return null;
        }
        return switch (empType) {
            case "상용직" -> "정규직";
            case "시간제" -> "파트타임";
            default -> truncate(empType, 50);
        };
    }

    static String qualification(Map<String, String> payload) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "학력", value(payload, "reqEduc"));
        addPart(parts, "경력", career(value(payload, "reqCareer")));
        addPart(parts, "채용구분", value(payload, "enterType"));
        addPart(parts, "전공", value(payload, "reqMajor"));
        String license = value(payload, "reqLicens");
        if (license != null) {
            license = license.replaceAll("[/\\s]+$", "");   // 끝의 구분자 "/" 제거
        }
        addPart(parts, "자격증", license);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** "무관", "0년0개월", "0년개월" → 무관 / "3년0개월" → "3년 이상" / "0년6개월" → "6개월 이상" */
    static String career(String raw) {
        if (raw == null || "무관".equals(raw)) {
            return raw;
        }
        Matcher m = CAREER.matcher(raw.replace(" ", ""));
        if (!m.matches()) {
            return raw;
        }
        int years = m.group(1).isEmpty() ? 0 : Integer.parseInt(m.group(1));
        int months = m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2));
        if (years == 0 && months == 0) {
            return "무관";
        }
        StringBuilder sb = new StringBuilder();
        if (years > 0) {
            sb.append(years).append("년");
        }
        if (months > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(months).append("개월");
        }
        return sb.append(" 이상").toString();
    }

    private static String env(Map<String, String> payload, String field, List<String> warnings) {
        String v = value(payload, field);
        if (v == null) {
            return null;
        }
        if (ENV_VOCAB.get(field).contains(v)) {
            return v;
        }
        warnings.add("근무환경 값 불일치(" + field + "): " + v);
        return null;
    }

    private static String classify(String jobNm) {
        Map<String, Double> scores = JobCategoryClassifier.classifyTitle(jobNm);
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() != null && e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
            }
        }
        return best;
    }

    private static Integer parseSalary(String raw, List<String> warnings) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            warnings.add("급여 형식 이상: " + raw);
            return null;
        }
        try {
            long v = Long.parseLong(digits);
            if (v > Integer.MAX_VALUE) {
                warnings.add("급여 범위 초과: " + raw);
                return null;
            }
            return (int) v;
        } catch (NumberFormatException e) {
            warnings.add("급여 형식 이상: " + raw);
            return null;
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    // ----- 유틸 -----

    private static String value(Map<String, String> payload, String name) {
        String v = payload.get(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static void addPart(List<String> parts, String label, String value) {
        if (value != null) {
            parts.add(label + ": " + value);
        }
    }

    private static String joinNonNull(String delimiter, String... values) {
        List<String> present = new ArrayList<>();
        for (String v : values) {
            if (v != null) {
                present.add(v);
            }
        }
        return present.isEmpty() ? null : String.join(delimiter, present);
    }

    private static String truncate(String text, int max) {
        return (text == null || text.length() <= max) ? text : text.substring(0, max);
    }
}
