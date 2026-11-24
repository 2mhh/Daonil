package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * 공고 식별·변경 감지용 해시.
 *
 * API에는 공고 고유 ID가 없고 rno/rnum은 응답 안의 순번(호출마다 달라질 수 있음)이라 키로 쓸 수 없다.
 *  - externalId : 잘 바뀌지 않는 식별 필드로 만든 키 (같은 공고의 "갱신"을 같은 행으로 이어 준다)
 *  - contentHash: 순번을 제외한 모든 필드로 만든 해시 (내용 변경 감지)
 */
public final class KeadIdentity {

    /** 식별에 쓰는 필드: 사업장명, 직무명, 접수기간, 사업장 주소 */
    private static final List<String> IDENTITY_FIELDS = List.of("busplaName", "jobNm", "termDate", "compAddr");

    /** 변경 감지에서 제외할 필드: 응답 안의 순번은 내용이 아니다 */
    private static final Set<String> VOLATILE_FIELDS = Set.of("rno", "rnum");

    private KeadIdentity() {
    }

    public static String externalId(KeadJobItem item) {
        StringBuilder sb = new StringBuilder();
        for (String field : IDENTITY_FIELDS) {
            sb.append(normalize(item.get(field))).append('|');
        }
        return sha256(sb.toString());
    }

    public static String contentHash(KeadJobItem item) {
        TreeMap<String, String> sorted = new TreeMap<>();   // 필드 순서와 무관하게 같은 해시
        item.fields().forEach((name, value) -> {
            if (!VOLATILE_FIELDS.contains(name)) {
                sorted.put(name, normalize(value));
            }
        });
        StringBuilder sb = new StringBuilder();
        sorted.forEach((name, value) -> sb.append(name).append('=').append(value).append('\n'));
        return sha256(sb.toString());
    }

    /**
     * 외부 사업장용 사업자번호 자리표시 값 (API에 사업자번호가 없다).
     * 같은 이름·주소면 같은 값 → 같은 company 행을 재사용한다. 길이는 30자 이내("EXT-" + 12자리).
     */
    public static String companyKey(String companyName, String companyAddress) {
        return "EXT-" + sha256(normalize(companyName) + "|" + normalize(companyAddress)).substring(0, 12);
    }

    /** null → "", 앞뒤 공백 제거, 연속 공백은 하나로 */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
