package com.cukalimi.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시(RFC 8030)를 JDK만으로 보낸다. 본문은 RFC 8291(aes128gcm)로 암호화하고 보낸 사람은 VAPID(RFC 8292)로 서명한다.
 * 라이브러리(web-push)는 비동기 HTTP 클라이언트와 BouncyCastle까지 끌고 와서 대신 150줄로 적었다. 암호화는 RFC 8291 부록 A의
 * 시험 벡터로 검증한다(WebPushTest).
 */
public final class WebPush {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();

    /** 구독 하나. 브라우저의 PushSubscription.toJSON()과 같은 값이다. */
    public record Subscription(String endpoint, String p256dh, String auth) {
    }

    public enum Outcome { SENT, GONE, FAILED }

    /** 브라우저가 주는 푸시 서비스 주소만 받는다. 아무 주소나 받으면 서버가 남의 주소로 요청을 보내는 통로가 된다. */
    public static boolean allowedEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getPort() != -1 || uri.getUserInfo() != null) {
            return false;
        }
        host = host.toLowerCase();
        return host.equals("fcm.googleapis.com") || host.equals("updates.push.services.mozilla.com")
                || host.equals("web.push.apple.com") || host.endsWith(".push.apple.com")
                || host.endsWith(".notify.windows.com") || host.endsWith(".push.samsungosp.com");
    }

    private final ECPrivateKey vapidPrivate;
    private final String vapidPublicRaw;
    private final String subject;
    private final HttpClient http;

    /** 키는 base64url이다. 공개 키는 65바이트(04 || x || y), 비밀 키는 32바이트다. */
    public WebPush(String publicKeyB64, String privateKeyB64, String subject) {
        try {
            publicKey(B64D.decode(publicKeyB64));
            this.vapidPrivate = privateKey(B64D.decode(privateKeyB64));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("VAPID 키가 올바르지 않습니다", e);
        }
        this.vapidPublicRaw = publicKeyB64;
        this.subject = subject;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String publicKey() {
        return vapidPublicRaw;
    }

    /** 푸시 서비스에 보낸다. 404와 410은 구독이 사라진 것이라 GONE이고, 그 밖의 실패는 FAILED다. */
    public Outcome send(Subscription sub, String payloadJson, Duration ttl) {
        try {
            byte[] body = encrypt(sub, payloadJson.getBytes(StandardCharsets.UTF_8));
            URI endpoint = URI.create(sub.endpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "vapid t=" + vapidJwt(audience, Instant.now()) + ", k=" + vapidPublicRaw)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(ttl.toSeconds()))
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            // 응답 본문은 쓰지 않으니 받지 않는다. 푸시 서비스가 큰 본문을 돌려줘도 메모리를 쓰지 않는다.
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            return status >= 200 && status < 300 ? Outcome.SENT : Outcome.FAILED;
        } catch (GeneralSecurityException | IllegalArgumentException | IOException e) {
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
    }

    /** RFC 8291. 보낼 때마다 새 소금과 새 키 쌍을 쓴다. */
    byte[] encrypt(Subscription sub, byte[] plaintext) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(sub, plaintext, salt, generateKeyPair());
    }

    /** 시험 벡터를 재현하려고 소금과 보내는 쪽 키를 밖에서 받는다. */
    static byte[] encrypt(Subscription sub, byte[] plaintext, byte[] salt, KeyPair sender) throws GeneralSecurityException {
        byte[] uaPublic = B64D.decode(sub.p256dh());
        byte[] authSecret = B64D.decode(sub.auth());
        byte[] asPublic = rawPublic((ECPublicKey) sender.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // 레코드 하나. 평문 뒤에 구분자 0x02만 붙이고 채우지 않는다.
        byte[] padded = concat(plaintext, new byte[] { 2 });
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(4096).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** RFC 8292. 12시간짜리 ES256 JWT. 서명은 DER이 아니라 r||s 64바이트다. */
    String vapidJwt(String audience, Instant now) throws GeneralSecurityException {
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + now.plus(Duration.ofHours(12)).getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(vapidPrivate);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + B64.encodeToString(signature.sign());
    }

    /** 운영자가 한 번 만들어 환경 변수에 넣는 키 쌍. [공개, 비밀] base64url. */
    public static String[] generateKeys() throws GeneralSecurityException {
        KeyPair pair = generateKeyPair();
        return new String[] { B64.encodeToString(rawPublic((ECPublicKey) pair.getPublic())),
                B64.encodeToString(rawPrivate((ECPrivateKey) pair.getPrivate())) };
    }

    static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    static KeyPair keyPair(byte[] rawPublic, byte[] rawPrivate) throws GeneralSecurityException {
        return new KeyPair(publicKey(rawPublic), privateKey(rawPrivate));
    }

    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        byte[] okm = mac.doFinal();
        byte[] out = new byte[length];
        System.arraycopy(okm, 0, out, 0, length);
        return out;
    }

    static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("공개 키는 65바이트 비압축 형식이어야 합니다");
        }
        ECPoint point = new ECPoint(new java.math.BigInteger(1, slice(raw, 1, 33)), new java.math.BigInteger(1, slice(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static ECPrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new java.math.BigInteger(1, raw), P256));
    }

    static byte[] rawPublic(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed(key.getW().getAffineX().toByteArray()), 0, out, 1, 32);
        System.arraycopy(fixed(key.getW().getAffineY().toByteArray()), 0, out, 33, 32);
        return out;
    }

    static byte[] rawPrivate(ECPrivateKey key) {
        return fixed(key.getS().toByteArray());
    }

    /** BigInteger는 부호 바이트가 붙거나 앞 0이 빠진다. 32바이트로 맞춘다. */
    private static byte[] fixed(byte[] n) {
        byte[] out = new byte[32];
        int skip = Math.max(0, n.length - 32);
        System.arraycopy(n, skip, out, 32 - (n.length - skip), n.length - skip);
        return out;
    }

    private static byte[] slice(byte[] a, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(a, from, out, 0, out.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(n);
        for (byte[] p : parts) {
            b.put(p);
        }
        return b.array();
    }

    private static ECParameterSpec p256() {
        try {
            java.security.AlgorithmParameters params = java.security.AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}
