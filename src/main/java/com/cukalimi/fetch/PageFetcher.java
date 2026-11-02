package com.cukalimi.fetch;

public interface PageFetcher {

    /** 페이지를 읽어 HTML을 돌려준다. 실패하면 예외를 던진다. */
    String get(String url);

    /** 그림 같은 바이너리를 읽는다. 실패하면 예외를 던진다. */
    default byte[] getBytes(String url) {
        throw new UnsupportedOperationException("binary fetch not supported");
    }
}
