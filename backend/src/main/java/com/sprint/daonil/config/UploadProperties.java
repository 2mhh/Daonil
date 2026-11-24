package com.sprint.daonil.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업로드 파일(프로필 사진, 게시글 첨부 등)을 저장하는 서버 폴더.
 *
 * 환경변수 APP_UPLOAD_DIR 로 바꾼다. 기본값은 기존 동작(Windows 개발 PC)과 같은 C:/daonil/ 이다.
 * 리눅스 서버에서는 반드시 지정한다. (예: /data/uploads/) — 컨테이너라면 볼륨을 이 경로에 연결해야 재배포해도 파일이 남는다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private String dir = "C:/daonil/";

    /** 역슬래시를 슬래시로 바꾸고 끝에 '/'를 보장한 저장 루트 */
    public String baseDir() {
        String normalized = dir.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
