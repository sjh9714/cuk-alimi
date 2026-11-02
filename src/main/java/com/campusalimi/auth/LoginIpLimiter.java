package com.campusalimi.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** IP 하나가 한 시간에 요청할 수 있는 로그인 링크 수. 서버가 한 대라 메모리로 충분하고, IP를 저장소에 남기지 않는다. */
@Component
public class LoginIpLimiter {

    static final int HOURLY_PER_IP = 10;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    public synchronized boolean allow(String ip, Instant now) {
        Instant from = now.minus(WINDOW);
        hits.values().forEach(times -> {
            while (!times.isEmpty() && times.peekFirst().isBefore(from)) {
                times.removeFirst();
            }
        });
        hits.values().removeIf(Deque::isEmpty);
        Deque<Instant> times = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        if (times.size() >= HOURLY_PER_IP) {
            return false;
        }
        times.addLast(now);
        return true;
    }
}
