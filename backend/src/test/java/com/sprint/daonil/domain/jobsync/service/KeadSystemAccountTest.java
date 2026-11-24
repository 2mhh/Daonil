package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.member.entity.Member;
import com.sprint.daonil.domain.member.enumtype.Role;
import com.sprint.daonil.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadSystemAccountTest {

    @Mock MemberRepository memberRepository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void 무작위_비밀번호는_BCrypt_허용_길이_이내이고_실제로_인코딩된다() {
        // 모의 인코더로는 잡지 못했던 문제: BCrypt 는 72바이트 초과 입력을 거부한다
        String password = KeadSystemAccount.randomPassword();
        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(72);
        assertThat(password).isNotEqualTo(KeadSystemAccount.randomPassword());   // 호출마다 다르다

        PasswordEncoder real = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        assertThat(real.encode(password)).startsWith("$2");
    }

    @Test
    void 이미_있으면_새로_만들지_않는다() {
        Member existing = Member.builder().loginId(KeadSystemAccount.LOGIN_ID).build();
        when(memberRepository.findByLoginId(KeadSystemAccount.LOGIN_ID)).thenReturn(Optional.of(existing));

        Member result = new KeadSystemAccount(memberRepository, passwordEncoder).getOrCreate();

        assertThat(result).isSameAs(existing);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void 없으면_로그인할_수_없는_계정을_만든다() {
        when(memberRepository.findByLoginId(KeadSystemAccount.LOGIN_ID)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member created = new KeadSystemAccount(memberRepository, passwordEncoder).getOrCreate();

        assertThat(created.getLoginId()).isEqualTo("system_kead");
        assertThat(created.getRole()).isEqualTo(Role.COMPANY);
        assertThat(created.getIsDeleted()).isFalse();
        assertThat(created.getPassword()).isEqualTo("HASHED");           // 평문이 아니라 해시만 저장

        // 비밀번호로 쓴 무작위 값은 호출마다 달라야 한다 (고정 비밀번호가 아니다)
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(raw.capture());
        assertThat(raw.getValue()).hasSizeGreaterThan(30);
        assertThat(created.getName()).hasSizeLessThanOrEqualTo(30);      // member.name 은 30자 제한
    }
}
