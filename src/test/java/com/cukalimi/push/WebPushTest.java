package com.cukalimi.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** RFC 8291 부록 A의 시험 벡터를 그대로 재현해야 한다. 소금과 보내는 쪽 키를 고정하면 암호문이 바이트까지 같다. */
class WebPushTest {

    static final Base64.Decoder B64D = Base64.getUrlDecoder();
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @Test
    void rfc8291_부록A_시험_벡터를_재현한다() throws Exception {
        WebPush.Subscription sub = new WebPush.Subscription("https://push.example/1",
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                "BTBZMqHH6r4Tts7J_aSIgg");
        KeyPair sender = WebPush.keyPair(
                B64D.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"),
                B64D.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        byte[] salt = B64D.decode("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] body = WebPush.encrypt(sub, "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8), salt, sender);
        assertThat(B64.encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void vapid_jwt는_공개_키로_검증된다() throws Exception {
        String[] keys = WebPush.generateKeys();
        WebPush push = new WebPush(keys[0], keys[1], "mailto:test@example");
        String jwt = push.vapidJwt("https://push.example", Instant.ofEpochSecond(1_800_000_000L));
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(new String(B64D.decode(parts[0]), StandardCharsets.UTF_8)).isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        assertThat(new String(B64D.decode(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://push.example\",\"exp\":1800043200,\"sub\":\"mailto:test@example\"}");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPush.publicKey(B64D.decode(keys[0])));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(B64D.decode(parts[2]))).isTrue();
        assertThat(B64D.decode(parts[2])).hasSize(64);
    }

    @Test
    void 알려진_푸시_서비스_주소만_받는다() {
        assertThat(WebPush.allowedEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(WebPush.allowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(WebPush.allowedEndpoint("https://web.push.apple.com/abc")).isTrue();
        assertThat(WebPush.allowedEndpoint("https://db5p.notify.windows.com/w/?token=x")).isTrue();
        assertThat(WebPush.allowedEndpoint("http://fcm.googleapis.com/x")).isFalse();
        assertThat(WebPush.allowedEndpoint("https://fcm.googleapis.com.evil.example/x")).isFalse();
        assertThat(WebPush.allowedEndpoint("https://127.0.0.1/x")).isFalse();
        assertThat(WebPush.allowedEndpoint("https://fcm.googleapis.com:8443/x")).isFalse();
        assertThat(WebPush.allowedEndpoint("not a url")).isFalse();
    }

    @Test
    void 만든_키는_65바이트_공개_키와_32바이트_비밀_키다() throws Exception {
        String[] keys = WebPush.generateKeys();
        assertThat(B64D.decode(keys[0])).hasSize(65);
        assertThat(B64D.decode(keys[1])).hasSize(32);
    }
}
