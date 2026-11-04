package com.cukalimi.push;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** VAPID 키 쌍을 한 번 만든다. VAPID_GEN=1 ./gradlew test --tests '*VapidKeyRun' 로 돌리고 출력을 환경 변수에 넣는다. */
class VapidKeyRun {

    @Test
    @EnabledIfEnvironmentVariable(named = "VAPID_GEN", matches = "1")
    void 키를_만든다() throws Exception {
        String[] keys = WebPush.generateKeys();
        System.out.println("VAPID_PUBLIC_KEY=" + keys[0]);
        System.out.println("VAPID_PRIVATE_KEY=" + keys[1]);
    }
}
