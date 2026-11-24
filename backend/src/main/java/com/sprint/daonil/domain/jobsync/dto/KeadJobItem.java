package com.sprint.daonil.domain.jobsync.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 응답의 item 하나. 필드를 이름→값 문자열로 그대로 보관한다(원본 보존).
 * 알 수 없는 필드도 잃지 않으므로 이후 Raw 저장(JSON)에 그대로 쓸 수 있다.
 */
public record KeadJobItem(Map<String, String> fields) {

    public KeadJobItem {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** 값이 없거나 공백이면 null */
    public String get(String name) {
        String value = fields.get(name);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
