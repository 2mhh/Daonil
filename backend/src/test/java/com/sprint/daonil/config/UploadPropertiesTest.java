package com.sprint.daonil.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadPropertiesTest {

    @Test
    void 기본값은_기존_동작과_같은_Windows_경로다() {
        assertThat(new UploadProperties().baseDir()).isEqualTo("C:/daonil/");
    }

    @Test
    void 끝에_슬래시가_없으면_붙인다() {
        UploadProperties p = new UploadProperties();
        p.setDir("/data/uploads");

        assertThat(p.baseDir()).isEqualTo("/data/uploads/");
    }

    @Test
    void 슬래시로_끝나면_그대로_둔다() {
        UploadProperties p = new UploadProperties();
        p.setDir("/data/uploads/");

        assertThat(p.baseDir()).isEqualTo("/data/uploads/");
    }

    @Test
    void 역슬래시는_슬래시로_바꾼다() {
        UploadProperties p = new UploadProperties();
        p.setDir("D:\\files\\daonil");

        assertThat(p.baseDir()).isEqualTo("D:/files/daonil/");
    }
}
