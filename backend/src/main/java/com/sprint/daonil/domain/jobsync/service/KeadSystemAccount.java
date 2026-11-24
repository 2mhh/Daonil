package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.member.entity.Member;
import com.sprint.daonil.domain.member.enumtype.Role;
import com.sprint.daonil.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 외부 공고를 소유하는 시스템 계정.
 *
 * 외부 사업장은 가입한 기업 회원이 없으므로 모든 외부 company 가 이 계정 한 개에 귀속된다.
 * 비밀번호는 무작위 값을 해시만 저장하고 어디에도 기록하지 않는다 → 아무도 로그인할 수 없고,
 * 따라서 외부 공고는 수정·마감 권한 검증을 통과할 수 없다(읽기 전용).
 */
@Component
@RequiredArgsConstructor
public class KeadSystemAccount {

    public static final String LOGIN_ID = "system_kead";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /** 호출하는 쪽의 트랜잭션 안에서 실행된다 */
    public Member getOrCreate() {
        return memberRepository.findByLoginId(LOGIN_ID).orElseGet(this::create);
    }

    private Member create() {
        LocalDateTime now = LocalDateTime.now();
        Member member = Member.builder()
                .email("system-kead@daonil.invalid")
                .loginId(LOGIN_ID)
                .password(passwordEncoder.encode(randomPassword()))   // 평문은 즉시 버린다
                .name("시스템(한국장애인고용공단)")
                .role(Role.COMPANY)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return memberRepository.save(member);
    }

    /**
     * 추측할 수 없는 무작위 비밀번호(64자).
     * BCrypt 는 72바이트까지만 허용하므로 그 이하로 맞춘다. (UUID 2개를 하이픈 없이 이어 붙임)
     */
    static String randomPassword() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
