package com.sprint.daonil.domain.jobsync.refine;

import com.sprint.daonil.domain.ai.util.JobCategoryClassifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeadMainCategoryTest {

    @Test
    void 분류기의_모든_직업군이_대분류에_매핑된다() {
        assertThat(JobCategoryClassifier.getAllCategories()).isNotEmpty();
        for (String sub : JobCategoryClassifier.getAllCategories()) {
            assertThat(KeadMainCategory.of(sub)).as("대분류 매핑: " + sub).isNotNull();
        }
    }

    @Test
    void 직업군은_33개다() {
        assertThat(KeadMainCategory.size()).isEqualTo(33);
        assertThat(JobCategoryClassifier.getAllCategories()).hasSize(33);
    }

    @Test
    void 몇_가지_대표_매핑을_확인한다() {
        assertThat(KeadMainCategory.of("경영·행정·사무직")).isEqualTo("사무 종사자");
        assertThat(KeadMainCategory.of("청소 및 기타 개인서비스직")).isEqualTo("서비스 종사자");
        assertThat(KeadMainCategory.of("제조 단순직")).isEqualTo("단순노무 종사자");
        assertThat(KeadMainCategory.of("없는 직군")).isNull();
        assertThat(KeadMainCategory.of(null)).isNull();
    }
}
