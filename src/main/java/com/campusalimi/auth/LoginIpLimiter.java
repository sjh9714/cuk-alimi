package com.campusalimi.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * IP 하나가 한 시간에 요청할 수 있는 로그인 링크 수. 서버가 한 대라 메모리로 충분하고, IP를 저장소에 남기지 않는다.
 * IPv6는 한 가입자가 /64를 통째로 쓰므로 앞 64비트로 센다. 표가 가득 차면 새 IP를 받지 않아 메모리가 늘지 않는다.
 */
@Component
public class LoginIpLimiter {

    static final int HOURLY_PER_IP = 10;
    static final int MAX_TRACKED = 10_000;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final Duration PRUNE_EVERY = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> hits = new HashMap<>();
    private Instant lastPrune = Instant.EPOCH;

    public synchronized boolean allow(String ip, Instant now) {
        Instant from = now.minus(WINDOW);
        if (lastPrune.plus(PRUNE_EVERY).isBefore(now) || hits.size() >= MAX_TRACKED) {
            prune(from);
            lastPrune = now;
        }
        String key = keyOf(ip);
        Deque<Instant> times = hits.get(key);
        if (times == null) {
            if (hits.size() >= MAX_TRACKED) {
                return false;
            }
            times = new ArrayDeque<>();
            hits.put(key, times);
        }
        while (!times.isEmpty() && times.peekFirst().isBefore(from)) {
            times.removeFirst();
        }
        if (times.size() >= HOURLY_PER_IP) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    private void prune(Instant from) {
        hits.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(from));
    }

    static String keyOf(String ip) {
        if (!ip.contains(":") || !ip.matches("[0-9a-fA-F:.]+")) {
            return ip;
        }
        try {
            byte[] address = InetAddress.getByName(ip).getAddress();
            return address.length == 16 ? HexFormat.of().formatHex(Arrays.copyOf(address, 8)) : ip;
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}
