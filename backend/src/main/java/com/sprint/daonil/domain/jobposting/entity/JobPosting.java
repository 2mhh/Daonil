package com.sprint.daonil.domain.jobposting.entity;

import com.sprint.daonil.domain.company.entity.Company;
import com.sprint.daonil.domain.jobposting.dto.JobPostingRequestDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_posting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 객체 생성 방지
@AllArgsConstructor
@Builder
public class JobPosting {

    public static final String SOURCE_INTERNAL = "INTERNAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "main_category", length = 100)
    private String mainCategory;

    @Column(name = "sub_category", length = 100)
    private String subCategory;

    @Column(name = "employment_type", length = 50)
    private String employmentType;

    @Column(name = "work_region", length = 100)
    private String workRegion;

    @Column(name = "salary")
    private Integer salary;

    @Column(name = "salary_type", length = 50)
    private String salaryType;

    @Column(name = "recruit_count")
    private Integer recruitCount;

    @Column(name = "qualification", columnDefinition = "TEXT")
    private String qualification;

    @Column(name = "application_start_date")
    private LocalDate applicationStartDate;

    @Column(name = "application_end_date")
    private LocalDate applicationEndDate;

    @Builder.Default
    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false) // 생성일은 업데이트 불가
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "content", length = 225)
    private String content;

    @Column(name = "work_hours", length = 100)
    private String workHours;

    @Column(name = "env_both_hands", length = 50)
    private String envBothHands;

    @Column(name = "env_eyesight", length = 50)
    private String envEyesight;

    @Column(name = "env_hand_work", length = 50)
    private String envHandWork;

    @Column(name = "env_lift_power", length = 50)
    private String envLiftPower;

    @Column(name = "env_lstn_talk", length = 50)
    private String envLstnTalk;

    @Column(name = "env_stnd_walk", length = 50)
    private String envStndWalk;

    /** 공고 출처: INTERNAL(기업 회원이 등록) / KEAD_JOB2(한국장애인고용공단 API) 등 */
    @Builder.Default
    @Column(name = "source", nullable = false, length = 30)
    private String source = SOURCE_INTERNAL;

    /** 외부 출처 공고의 식별키 (내부 공고는 null) */
    @Column(name = "external_id", length = 64, columnDefinition = "CHAR(64)")
    private String externalId;

    /** 외부 출처 공고의 문의처 (지사명·전화) */
    @Column(name = "external_contact", length = 100)
    private String externalContact;



    public void update(JobPostingRequestDto requestDto) {
        this.title = requestDto.getTitle();
        this.mainCategory = requestDto.getMainCategory();
        this.subCategory = requestDto.getSubCategory();
        this.employmentType = requestDto.getEmploymentType();
        this.workRegion = requestDto.getWorkRegion();
        this.salary = requestDto.getSalary();
        this.salaryType = requestDto.getSalaryType();
        this.recruitCount = requestDto.getRecruitCount();
        this.qualification = requestDto.getQualification();
        this.applicationStartDate = requestDto.getApplicationStartDate();
        this.applicationEndDate = requestDto.getApplicationEndDate();
        this.content = requestDto.getContent();
        this.workHours = requestDto.getWorkHours();

        // 환경 정보 업데이트
        this.envBothHands = requestDto.getEnvBothHands();
        this.envEyesight = requestDto.getEnvEyesight();
        this.envHandWork = requestDto.getEnvHandWork();
        this.envLiftPower = requestDto.getEnvLiftPower();
        this.envLstnTalk = requestDto.getEnvLstnTalk();
        this.envStndWalk = requestDto.getEnvStndWalk();
    }


    public void close() {
        this.isClosed = true;
    }

    /** 외부 API 출처 공고인가 (수정·마감·앱 내 지원 대상이 아님) */
    public boolean isExternal() {
        return source != null && !SOURCE_INTERNAL.equals(source);
    }

    /** 외부 출처 정보 지정 */
    public void assignExternal(String source, String externalId, String externalContact) {
        this.source = source;
        this.externalId = externalId;
        this.externalContact = externalContact;
    }


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}