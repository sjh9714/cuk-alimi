package com.cukalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageNoticeReaderTest {

    private static final String BASE = "https://www.catholic.ac.kr";

    @Test
    void onlySchoolImagesAreFetched() {
        assertThat(ImageNoticeReader.onSchoolSite("https://www.catholic.ac.kr/_attach/a.png", BASE)).isTrue();
        assertThat(ImageNoticeReader.onSchoolSite("https://evil.example/a.png", BASE)).isFalse();
        assertThat(ImageNoticeReader.onSchoolSite("http://169.254.169.254/latest/meta-data", BASE)).isFalse();
        assertThat(ImageNoticeReader.onSchoolSite("https://www.catholic.ac.kr:8443/a.png", BASE)).isFalse();
        assertThat(ImageNoticeReader.onSchoolSite("https://x@www.catholic.ac.kr/a.png", BASE)).isFalse();
        assertThat(ImageNoticeReader.onSchoolSite("file:///etc/passwd", BASE)).isFalse();
    }

    @Test
    void mediaTypeFollowsExtension() {
        assertThat(ImageNoticeReader.mediaType("https://www.catholic.ac.kr/a.JPG")).isEqualTo("image/jpeg");
        assertThat(ImageNoticeReader.mediaType("https://www.catholic.ac.kr/a.png")).isEqualTo("image/png");
        assertThat(ImageNoticeReader.mediaType("https://www.catholic.ac.kr/a.bmp")).isNull();
    }
}
