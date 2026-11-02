package com.cukalimi.schedule;

import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학사일정 한 해치를 date_item에 맞춘다. 한 트랜잭션. 이번에 안 보인 자동 항목은 지운다.
 * 돈이나 학적이 걸린 항목만 남긴다. 미사·교수회의·수업일수 같은 것은 이 서비스의 범위가 아니다.
 * 표에서 읽은 것이라 파싱이 없으니 검수 없이 확인된 것으로 둔다.
 */
@Service
public class ScheduleStore {

    static final java.util.regex.Pattern RELEVANT = java.util.regex.Pattern.compile(
            "수강|등록|휴학|복학|성적확인|오류정정|장학|졸업|계절학기|납부|학위수여|개시일");

    private final DateItemRepository dateItems;

    public ScheduleStore(DateItemRepository dateItems) {
        this.dateItems = dateItems;
    }

    public record Result(int seen, int kept, int inserted, int updated, int removed) {
    }

    @Transactional
    public Result apply(int academicYear, String campus, List<ScheduleEntry> entries) {
        Map<String, Integer> ordinals = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int kept = 0;
        int inserted = 0;
        int updated = 0;
        for (ScheduleEntry entry : entries) {
            if (!entry.campuses().contains(campus) || !RELEVANT.matcher(entry.title()).find()) {
                continue;
            }
            kept++;
            int ordinal = ordinals.merge(entry.title(), 1, Integer::sum);
            DateItem fresh = DateItem.fromSchedule(entry.naturalKey(academicYear, ordinal), entry.title(),
                    entry.startsOn(), entry.endsOn(), String.join("+", entry.campuses()));
            seen.add(fresh.getNaturalKey());
            var existing = dateItems.findByNaturalKey(fresh.getNaturalKey());
            if (existing.isEmpty()) {
                dateItems.save(fresh);
                inserted++;
            } else if (existing.get().refreshFrom(fresh)) {
                updated++;
            }
        }
        // 표가 비어 있으면 학교 페이지가 바뀌었거나 읽기에 실패한 것이다. 있던 일정을 지우지 않는다.
        if (kept == 0) {
            return new Result(entries.size(), 0, inserted, updated, 0);
        }
        int removed = 0;
        for (DateItem old : dateItems.findByNaturalKeyStartingWith("schedule:" + academicYear + ":")) {
            if (!seen.contains(old.getNaturalKey()) && DateItem.SOURCE_SCHEDULE.equals(old.getSourceKind())) {
                dateItems.delete(old);
                removed++;
            }
        }
        return new Result(entries.size(), kept, inserted, updated, removed);
    }
}
