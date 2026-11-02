package com.campusalimi.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * IP 하나가 한 시간에 요청할 수 있는 로그인 링크 수. 서버가 한 대라 메모리로 충분하고, IP를 저장소에 남기지 않는다.
 * IPv6는 한 가입자가 /64를 통째로 쓰므로 앞 64비트로 센다. 표가 차면 가장 오래 안 쓴 IP부터 잊는다.
 * 잊힌 IP는 다시 셀 뿐이고, 주소당 한도와 하루 상한은 그대로 남는다.
 */
@Component
public class LoginIpLimiter {

    static final int HOURLY_PER_IP = 10;
    static final int MAX_TRACKED = 10_000;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> hits = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    public synchronized boolean allow(String ip, Instant now) {
        Instant from = now.minus(WINDOW);
        Deque<Instant> times = hits.computeIfAbsent(keyOf(ip), k -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst().isBefore(from)) {
            times.removeFirst();
        }
        if (times.size() >= HOURLY_PER_IP) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /** ForwardedHeaderFilter는 IPv6 주소에 대괄호를 씌워 넘긴다. */
    static String keyOf(String ip) {
        String bare = ip.startsWith("[") && ip.endsWith("]") ? ip.substring(1, ip.length() - 1) : ip;
        if (!bare.contains(":") || !bare.matches("[0-9a-fA-F:.]+")) {
            return bare;
        }
        try {
            byte[] address = InetAddress.getByName(bare).getAddress();
            return address.length == 16 ? HexFormat.of().formatHex(Arrays.copyOf(address, 8)) : bare;
        } catch (UnknownHostException e) {
            return bare;
        }
    }
}
