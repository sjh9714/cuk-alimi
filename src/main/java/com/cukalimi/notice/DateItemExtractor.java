package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;
import com.cukalimi.dateparse.KoreanDateParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;

/**
 * 공지 한 건에서 날짜 항목을 뽑는다. 세 곳을 순서대로 본다.
 *
 * <ol>
 * <li>제목. 장학 공지는 "(10. 14.(수) ~ 11. 13.(금))"처럼 제목에 기간을 적는 경우가 많다.</li>
 * <li>표. 수강신청 일정처럼 구분·학년·일정 열로 된 표는 행마다 항목이 된다. rowspan으로 묶인 라벨은 아래 행에도 붙인다.
 * 한 행에 날짜 열이 둘 이상이면(신청기간 | 결과발표) 열마다 따로 항목을 내고 머리글을 제목에 붙인다. 기간이 다르면 절대 합치지 않는다.
 * 기간이 같은 열(환불 신청일 | 일정)만 하나로 본다. 라벨은 짧은 칸만 쓰고 설명 칸("▶ 수강신청 인원이 15명 미만인 경우…")은 붙이지 않는다.</li>
 * <li>문단. 제목과 표에서 아무것도 못 찾았을 때만, "기간", "마감", "신청" 같은 말이 있는 줄에서 읽는다.
 * 날짜만 적힌 줄은 바로 윗줄이 "신청기간" 같은 짧은 라벨일 때 그 라벨을 붙여 읽는다.</li>
 * </ol>
 *
 * 못 읽으면 빈 목록을 돌려준다. 추측하지 않는다.
 */
public final class DateItemExtractor {

    private static final Pattern GRADE = Pattern.compile("(?<!\\d)([1-4])\\s*학년(?!도)");
    private static final Pattern ALL_GRADES = Pattern.compile("전체\\s*학년|전\\s*학년");
    private static final Pattern GRADE_RANGE = Pattern.compile("[1-4]\\s*[~∼〜～\\-–,·]\\s*[1-4]\\s*학년");
    private static final Pattern LINE_KEYWORD = Pattern.compile("신청|접수|제출|마감|기간|까지|모집|납부|등록");
    /** 무언가를 해야 하는 날이 아니라 기록용 날짜를 담는 열. 강의 변경표의 "적용일자" 같은 것. */
    private static final Pattern RECORD_DATE_COLUMN = Pattern.compile("적용\\s*일|시행\\s*일|개정\\s*일|게시\\s*일|작성\\s*일");
    /**
     * 수업이 열리는 때를 담는 열. 과목 목록 표의 "수업교시", "수업기간"은 과목마다 날짜가 달라 마감처럼 읽히지만 할 일이 아니다.
     * 2026-11-06 가톨릭공유대학 계절학기 공지의 과목 표 43행이 이렇게 할 일로 올라왔다.
     */
    private static final Pattern CLASS_TIME_COLUMN = Pattern.compile("교시|(수업|강의|수강|학습|교육|운영)\\s*(기간|일정|일시|시간)");
    /** 표가 있어도 문단에서 읽는 줄. 학생이 할 일의 기간이 분명한 이름만 둔다. */
    private static final Pattern PERIOD_LINE = Pattern.compile("(신청|접수|모집|조사|제출)\\s*기간");
    private static final Pattern EVENT_DAY_LINE = Pattern.compile("((행사|특강|강연)\\s*)?일\\s*시");
    private static final int TITLE_LIMIT = 80;
    /** 표 머리글의 자리 이름. 라벨이나 열 이름으로 쓰면 "구분 · 2/3 환불"처럼 뜻 없는 제목이 된다. */
    private static final Pattern GENERIC_HEADER = Pattern.compile("^(구\\s*분|내\\s*용|일\\s*정|일\\s*자|날\\s*짜|비\\s*고|기\\s*간|순\\s*번|번\\s*호|항\\s*목|no\\.?)$", Pattern.CASE_INSENSITIVE);
    /** 설명 칸. 글머리표나 문장 부호가 있거나 길면 라벨이 아니라 설명이다. 제목에 붙이면 "폐강과목 공고 (1차) 수강신청 인원이…"가 된다. */
    private static final Pattern DESCRIPTION_CELL = Pattern.compile("[▶▷※:：;]|\\. |\\.$|다\\s*$|함\\s*$|음\\s*$|요\\s*$|참조\\s*$|참고\\s*$");
    private static final int LABEL_CELL_LIMIT = 30;
    /** "신청기간", "접수 기간", "마감 기한"처럼 일의 이름이 아니라 자리 이름인 라벨. 공지 제목이 곧 할 일이다. */
    private static final Pattern GENERIC_LABEL = Pattern.compile(
            "^((신청|접수|모집|조사|확인|평가|이벤트|행사|제출|운영|선발|지원)\\s*)?(기간|기한|마감(\\s*기한)?|일정|내용|일자)$|^신청\\s*접수$");
    /** 설명 문장의 앞머리. 날짜 앞에 "명단 발송 및 제출 확인을 위해"처럼 이유가 적힌 줄이다. */
    private static final Pattern DESCRIPTION_LEAD = Pattern.compile("(위해|위하여|바랍니다|주세요|합니다)\\s*$");
    /**
     * 무엇을 하는지만 있고 무엇의 일인지가 없는 라벨의 낱말. "트리니티 신청", "원본서류 제출"은 이 낱말만으로 되어 있어서
     * 행 제목만 훑으면 어느 장학·프로그램인지 알 수 없다. 이런 라벨은 공지 제목에서 대상 이름을 앞에 붙인다.
     */
    private static final Set<String> BARE_WORDS = Set.of("트리니티", "온라인", "오프라인", "방문", "이메일", "우편", "원본", "원본서류",
            "서류", "제출서류", "구비서류", "신청서", "지원서", "참가신청서", "신청", "접수", "제출", "등록", "및", "신청서류", "최종", "1차",
            "2차", "추가", "온라인신청");
    private static final Pattern SUBJECT_WORD = Pattern.compile(
            "(장학생|장학금|장학|인턴|프로그램|교류|대회|캠프|공모전|해커톤|아카데미|전형|과정|멘토단?|봉사단|서포터즈|챌린지)$");
    /** 낱말 하나로는 어느 것인지 모르는 대상 이름. 앞의 이름을 함께 쓴다("두산연강재단 장학생"). */
    private static final Pattern VAGUE_SUBJECT = Pattern.compile("^(장학생|장학금|장학|인턴|프로그램|아카데미|과정|전형|캠프|대회)$");
    private static final Pattern NOT_A_NAME = Pattern.compile("^(대학|대학생|신규|우수|\\d+학기|하반기|상반기|\\d+기|\\d{4}학년도|\\d{4}년도?)$");
    /** 같은 행에 날짜 열이 여럿이고 기간이 같을 때 어느 열 이름을 쓸지. 할 일을 뜻하는 이름이 먼저다. */
    private static final Pattern ACTION_COLUMN = Pattern.compile("신청|접수|제출|납부|등록|모집|변경");
    private static final int LABEL_LINE_LIMIT = 20;
    /**
     * 문장 속 마감. "9월 30일 오전 11시까지 … 자소서를 노진영(…)에게 보내주세요"처럼 날짜 뒤 "까지"에 목적어와 제출 동사가 오는 줄.
     * 목적어(…를)와 동사로 "자소서 제출"을 만든다. 목적어 없이 "까지 이메일 접수"면 날짜 앞 "추천서 작성 후"의 명사를 쓴다.
     */
    private static final Pattern SENTENCE_OBJECT = Pattern.compile("^\\s*[^。]{0,40}?([가-힣A-Za-z]{2,10})\\s*[을를]\\s+[^。]{0,60}?(보내|송부|발송|제출|업로드)");
    private static final Pattern SENTENCE_RECEIVE = Pattern.compile("^\\s*(\\([^)]{0,6}\\))?\\s*(이메일|메일|온라인)?\\s*(접수|제출|신청)");
    private static final Pattern WRITE_BEFORE = Pattern.compile("([가-힣]{2,10})\\s*작성\\s*후\\s*$");

    private DateItemExtractor() {
    }

    public static List<ExtractedItem> extract(NoticeView view) {
        KoreanDateParser parser = new KoreanDateParser(view.postedOn());
        List<ExtractedItem> items = new ArrayList<>();
        String cleanTitle = view.cleanTitle();

        // 제목의 날짜는 "(10. 14. ~ 11. 13.)"처럼 기간이거나 "(~10.26)"처럼 마감일일 때만 믿는다.
        // "<2026.09.04. 업데이트>" 같은 단일 날짜는 마감이 아니다.
        for (DateRange r : parser.parse(view.title())) {
            if (r.start() == null || !r.isSingleDay() || view.title().contains("까지")) {
                items.add(new ExtractedItem(cleanTitle, r, gradeOf(view.title()), "title"));
            }
        }

        int tableIndex = 0;
        for (Element table : view.body().select("table")) {
            tableIndex++;
            items.addAll(fromTable(table, parser, cleanTitle, "table" + tableIndex));
        }

        // 게시일보다 한 해 넘게 앞선 날짜는 마감이 아니다. "1996년 10월 15일 이후 출생자" 같은 조건이나 학칙 개정일이다.
        LocalDate oldest = view.postedOn().minusYears(1);
        items.removeIf(i -> i.range().end().isBefore(oldest));
        if (items.isEmpty()) {
            items.addAll(fromLines(view, parser, cleanTitle));
        } else {
            // 표에서 항목이 나와도 "모집기간", "조사기간"처럼 뜻이 분명한 기간 줄은 문단에서 함께 읽는다.
            // 표는 프로그램 일정이고 신청 기간은 문단에만 적힌 공지가 있다. 표에 이미 있는 마감일은 다시 넣지 않는다.
            Set<LocalDate> ends = new java.util.HashSet<>();
            items.forEach(i -> ends.add(i.range().end()));
            fromLines(view, parser, cleanTitle).stream()
                    .filter(i -> (PERIOD_LINE.matcher(i.title()).find() && !ends.contains(i.range().end()))
                            || i.where().startsWith("sentence"))
                    .forEach(items::add);
        }
        items.removeIf(i -> i.range().end().isBefore(oldest));
        // "신청기간"처럼 자리 이름이거나 "명단 발송 및 제출 확인을 위해"처럼 이유 문장이면 공지 제목이 곧 할 일이다.
        // 위의 문단 읽기 규칙(PERIOD_LINE)은 원래 라벨로 걸어야 하므로 맨 마지막에 바꾼다.
        // 공지 제목이 "참가자 모집 공고"처럼 안내로 읽히면(ItemKind) 바꾸지 않는다. 할 일이 안내로 바뀌어 마감에서 빠진다.
        boolean titleIsAction = com.cukalimi.feed.ItemKind.ACTION.equals(com.cukalimi.feed.ItemKind.of(cleanTitle));
        List<ExtractedItem> named = new ArrayList<>();
        for (ExtractedItem i : items) {
            boolean generic = GENERIC_LABEL.matcher(i.title()).find() || DESCRIPTION_LEAD.matcher(i.title()).find();
            // 학점교류는 웹이 학교 이름과 "학점교류"를 붙이므로 여기서 붙이지 않는다.
            String subject = !generic && !cleanTitle.contains("학점교류") && bareLabel(i.title()) ? subjectOf(cleanTitle) : null;
            if (generic && titleIsAction) {
                named.add(new ExtractedItem(cleanTitle, i.range(), i.targetGrade(), i.where()));
            } else if (subject != null) {
                // "트리니티 신청"처럼 무엇의 일인지 빠진 라벨이면 대상 이름을 앞에 붙인다("하나장학생 트리니티 신청").
                named.add(new ExtractedItem(subject + " " + i.title(), i.range(), i.targetGrade(), i.where()));
            } else {
                named.add(i);
            }
        }
        return dedupe(named);
    }

    private static List<ExtractedItem> fromTable(Element table, KoreanDateParser parser, String fallbackTitle,
            String where) {
        List<ExtractedItem> items = new ArrayList<>();
        Map<Integer, Carry> carry = new HashMap<>();
        List<String> header = null;
        int rowIndex = 0;
        for (Element tr : table.select("tr")) {
            rowIndex++;
            List<String> cells = new ArrayList<>();
            int col = 0;
            for (Element td : tr.select("> td, > th")) {
                while (carry.containsKey(col)) {
                    cells.add(carry.get(col).text);
                    if (--carry.get(col).remaining == 0) {
                        carry.remove(col);
                    }
                    col++;
                }
                String text = td.text().trim();
                cells.add(text);
                int rowspan = parseInt(td.attr("rowspan"));
                if (rowspan > 1) {
                    carry.put(col, new Carry(text, rowspan - 1));
                }
                col++;
            }
            while (carry.containsKey(col)) {
                cells.add(carry.get(col).text);
                if (--carry.get(col).remaining == 0) {
                    carry.remove(col);
                }
                col++;
            }

            List<String> labels = new ArrayList<>();
            List<Integer> dateCols = new ArrayList<>();
            Map<Integer, List<DateRange>> rangesByCol = new HashMap<>();
            for (int c = 0; c < cells.size(); c++) {
                String cell = cells.get(c);
                List<DateRange> found = parser.parse(cell);
                if (found.isEmpty()) {
                    String label = cleanLabel(cell);
                    if (isLabel(label)) {
                        labels.add(label);
                    }
                } else {
                    dateCols.add(c);
                    rangesByCol.put(c, found);
                }
            }
            if (dateCols.isEmpty()) {
                // 날짜 없는 첫 행은 머리글이다. "일반휴학원 제출 마감일 | 비고"처럼 날짜 열 이름이 곧 제목인 표가 있다.
                if (header == null && !cells.isEmpty()) {
                    header = cells.stream().map(DateItemExtractor::cleanLabel).toList();
                }
                continue;
            }
            // 한 행에 날짜 열이 여럿인데 기간이 같으면(환불 신청일 | 일정) 하나다. 할 일을 뜻하는 열 이름을 남긴다.
            Set<Integer> skip = new java.util.HashSet<>();
            for (int i = 0; i < dateCols.size(); i++) {
                for (int j = i + 1; j < dateCols.size(); j++) {
                    int a = dateCols.get(i);
                    int b = dateCols.get(j);
                    if (!skip.contains(a) && !skip.contains(b) && sameRange(rangesByCol.get(a), rangesByCol.get(b))) {
                        String nameA = header != null && a < header.size() ? header.get(a) : "";
                        String nameB = header != null && b < header.size() ? header.get(b) : "";
                        skip.add(!ACTION_COLUMN.matcher(nameA).find() && ACTION_COLUMN.matcher(nameB).find() ? a : b);
                    }
                }
            }
            for (int c : dateCols) {
                if (skip.contains(c)) {
                    continue;
                }
                List<String> titleParts = new ArrayList<>();
                String columnName = header != null && c < header.size() ? header.get(c) : "";
                if (RECORD_DATE_COLUMN.matcher(columnName).find() || CLASS_TIME_COLUMN.matcher(columnName).find()) {
                    continue;
                }
                // 날짜 열이 하나뿐이고 그 열이 첫 열이면 머리글이 곧 제목이다. 날짜 열이 여럿이면 열 이름으로 구분한다.
                // "구분", "일정" 같은 자리 이름은 제목이 되지 않는다.
                if ((dateCols.size() - skip.size() > 1 || c == 0) && !columnName.isEmpty() && !GENERIC_HEADER.matcher(columnName).find()) {
                    titleParts.add(columnName);
                }
                labels.stream().filter(l -> !titleParts.contains(l)).forEach(titleParts::add);
                if (dateCols.size() > 1 && header == null) {
                    // 열 이름을 모르면 어느 날짜가 마감인지 알 수 없다. 추측하지 않고 검수로 보낸다.
                    continue;
                }
                // "1차 제출 ~11.10 / 2차 제출 ~12.21"처럼 한 칸에 끝 날짜만 여럿이면 마감이 여럿이다. 합치지 않는다.
                List<DateRange> found = rangesByCol.get(c);
                boolean separate = found.size() > 1 && found.stream().allMatch(r -> r.start() == null);
                List<DateRange> parts = separate ? found : List.of(merge(found));
                for (int k = 0; k < parts.size(); k++) {
                    DateRange range = parts.get(k);
                    String labelText = String.join(" · ", titleParts);
                    // 라벨 칸이 없으면 "일시 : 2026.11.4.(수)"처럼 날짜 칸 안에서 날짜 앞에 적힌 짧은 글이 라벨이다.
                    if (labelText.isEmpty()) {
                        String lead = leadOf(cells.get(c), range);
                        labelText = lead.length() >= 2 && lead.length() <= 40 ? lead : "";
                    }
                    String title = labelText.isEmpty() ? fallbackTitle : clip(labelText);
                    items.add(new ExtractedItem(title, range, gradeOf(String.join(" ", labels)),
                            where + ".r" + rowIndex + ".c" + c + (separate ? "." + k : "")));
                }
            }
        }
        return items;
    }

    private static List<ExtractedItem> fromLines(NoticeView view, KoreanDateParser parser, String fallbackTitle) {
        List<ExtractedItem> items = new ArrayList<>();
        int lineIndex = 0;
        String above = "";
        // 제목 태그도 줄로 본다. "<h3>3. 신청기간</h3>" 아래에 날짜만 적힌 공지가 있다.
        for (Element e : view.body().select("p, li, h1, h2, h3, h4, h5, h6")) {
            if (!e.select("p, li").isEmpty() && e.children().stream().anyMatch(c -> c.is("p, li"))) {
                continue;
            }
            String line = e.text().trim();
            lineIndex++;
            if (line.isEmpty()) {
                continue;
            }
            String previous = above;
            above = line;
            List<DateRange> ranges = parser.parse(line);
            if (ranges.isEmpty()) {
                continue;
            }
            DateRange merged = merge(ranges);
            String lead = leadOf(line, merged);
            // "신청기간" 줄 아래에 날짜만 적힌 줄이 오는 공지가 있다. 날짜 앞에 글이 없고 바로 윗줄이 날짜 없는 짧은 라벨이면 그 라벨을 쓴다.
            if (lead.isEmpty() && previous.length() <= LABEL_LINE_LIMIT && parser.parse(previous).isEmpty()) {
                lead = cleanLabel(previous);
            }
            // 날짜 앞의 글이 "접수기간", "원본서류 제출"처럼 짧고 그 안에 핵심어가 있을 때만 항목으로 본다.
            // "학생 본인, 부 또는 모 중 1명 이상이 ... 거주" 같은 자격 조건의 날짜는 마감이 아니다.
            // "일시"는 마감이 아니라 행사 날이다. ItemKind가 안내로 분류해 마감 목록에는 들어가지 않는다.
            boolean eventDay = EVENT_DAY_LINE.matcher(lead).matches();
            if (lead.length() < 2 || lead.length() > 40 || !(eventDay || LINE_KEYWORD.matcher(lead).find())) {
                // 앞의 글로는 일 이름을 못 찾았다. 문장 속 마감인지 본다.
                for (DateRange r : ranges) {
                    String name = sentenceName(line, r);
                    if (name != null) {
                        items.add(new ExtractedItem(name, r, gradeOf(line), "sentence" + lineIndex));
                    }
                }
                continue;
            }
            // 앞의 글이 "명단 발송 및 제출 확인을 위해"처럼 이유 문장이면 문장 속 이름("자소서 제출")이 더 낫다.
            if (DESCRIPTION_LEAD.matcher(lead).find()) {
                String name = sentenceName(line, merged);
                if (name != null) {
                    items.add(new ExtractedItem(name, merged, gradeOf(line), "sentence" + lineIndex));
                    continue;
                }
            }
            items.add(new ExtractedItem(lead, merged, gradeOf(line), "line" + lineIndex));
        }
        return items;
    }

    /** 라벨로 쓸 수 있는 칸인가. 짧고, 숫자만은 아니고, 자리 이름이 아니고, 설명 문장이 아니어야 한다. */
    static boolean bareLabel(String label) {
        boolean any = false;
        for (String w : label.split("[\\s·,/]+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (!BARE_WORDS.contains(w)) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /** 공지 제목에서 대상 이름. "하나금융나눔재단 하나장학생 선발"이면 "하나장학생"이다. 장학·인턴·대회 같은 낱말이 없으면 null. */
    static String subjectOf(String cleanTitle) {
        List<String> words = new ArrayList<>();
        for (String w : cleanTitle.replaceAll("\\([^)]*\\)", " ").split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        for (int i = words.size() - 1; i >= 0; i--) {
            String w = words.get(i);
            if (!SUBJECT_WORD.matcher(w).find()) {
                continue;
            }
            if (VAGUE_SUBJECT.matcher(w).matches()) {
                for (int j = i - 1; j >= 0; j--) {
                    if (!NOT_A_NAME.matcher(words.get(j)).matches()) {
                        return words.get(j) + " " + w;
                    }
                }
            }
            return w;
        }
        // 대상 이름이 없으면 붙이지 않는다. 앞 낱말을 아무거나 붙이면 "테스트 서류 제출"처럼 더 이상해진다.
        return null;
    }

    static boolean isLabel(String cleaned) {
        if (cleaned.isEmpty() || cleaned.length() > LABEL_CELL_LIMIT) {
            return false;
        }
        if (cleaned.matches("[\\d\\s.,/-]+") || GENERIC_HEADER.matcher(cleaned).find()) {
            return false;
        }
        return !DESCRIPTION_CELL.matcher(cleaned).find();
    }

    private static boolean sameRange(List<DateRange> a, List<DateRange> b) {
        DateRange x = merge(a);
        DateRange y = merge(b);
        return java.util.Objects.equals(x.start(), y.start()) && x.end().equals(y.end());
    }

    /** 문장 속 마감의 이름. 날짜 바로 뒤가 "까지"이고 그 뒤에 목적어와 제출 동사가 있을 때만. 없으면 null. */
    static String sentenceName(String line, DateRange r) {
        String matched = r.matchedText().replaceFirst("^~", "");
        int at = line.indexOf(matched);
        if (at < 0) {
            return null;
        }
        String rest = line.substring(at + matched.length());
        String after;
        if (matched.endsWith("까지")) {
            after = rest;
        } else {
            // 날짜 뒤 "(수)", "오전 11시" 같은 말을 건너뛰고 "까지"가 와야 한다.
            Matcher until = Pattern.compile("^\\s*(\\([^)]{0,6}\\))?\\s*((오전|오후)\\s*)?(\\d{1,2}\\s*시(\\s*\\d{1,2}\\s*분)?)?\\s*까지").matcher(rest);
            if (!until.find()) {
                return null;
            }
            after = rest.substring(until.end());
        }
        Matcher obj = SENTENCE_OBJECT.matcher(after);
        if (obj.find()) {
            return obj.group(1) + " 제출";
        }
        Matcher recv = SENTENCE_RECEIVE.matcher(after);
        if (recv.find()) {
            Matcher before = WRITE_BEFORE.matcher(line.substring(0, at));
            if (before.find()) {
                return before.group(1) + (recv.group(2) == null ? " " : " " + recv.group(2) + " ") + recv.group(3);
            }
        }
        return null;
    }

    /** 날짜 앞에 적힌 글. "접수기간 : 10. 1.(수)"에서 "접수기간". */
    private static String leadOf(String text, DateRange merged) {
        int cut = text.indexOf(merged.matchedText().replaceFirst("^~", ""));
        String lead = cleanLabel(cut > 0 ? text.substring(0, cut) : "");
        lead = lead.replaceAll("[\\s:：\\-–—·~∼〜～(\\[\\d.]+$", "").replaceAll("\\s*(안내는|안내|입니다|합니다)$", "").trim();
        return tidyLead(lead);
    }

    /**
     * "1기: 1기 신청 바로가기 (신청기간"처럼 날짜가 괄호 안에 있으면 괄호 앞이 일의 이름이다. 링크 글자("바로가기", "클릭")는 뗀다.
     * "1기: 1기 신청"처럼 쌍점 앞뒤가 겹치면 뒤만 남긴다. 2026-11-04 점검(`RUN_ONCE=audit`)에서 나온 꼴이다.
     */
    static String tidyLead(String lead) {
        int open = lead.lastIndexOf('(');
        if (open > 0 && lead.indexOf(')', open) < 0) {
            lead = lead.substring(0, open).trim();
        }
        lead = lead.replaceAll("\\s*(바로\\s*가기|클릭|링크|☞|>>)\\s*", " ").replaceAll("\\s+", " ").trim();
        int colon = lead.indexOf(':');
        if (colon > 0) {
            String before = lead.substring(0, colon).trim();
            String after = lead.substring(colon + 1).trim();
            if (!before.isEmpty() && after.startsWith(before)) {
                lead = after;
            }
        }
        return lead.replaceAll("[\\s:：\\-–—·]+$", "").trim();
    }

    /** 한 칸 안에 날짜가 여럿이면(변경기간 2차처럼 날마다 한 줄) 처음부터 끝까지 한 기간으로 합친다. 칸을 넘어 합치지는 않는다. */
    private static DateRange merge(List<DateRange> ranges) {
        if (ranges.size() == 1) {
            return ranges.get(0);
        }
        LocalDate start = null;
        LocalDate end = null;
        for (DateRange r : ranges) {
            if (r.start() != null && (start == null || r.start().isBefore(start))) {
                start = r.start();
            }
            if (end == null || r.end().isAfter(end)) {
                end = r.end();
            }
        }
        DateRange first = ranges.get(0);
        DateRange last = ranges.get(ranges.size() - 1);
        return new DateRange(start, end, first.startsAt(), last.endsAt() != null ? last.endsAt() : first.endsAt(),
                first.matchedText());
    }

    private static List<ExtractedItem> dedupe(List<ExtractedItem> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<ExtractedItem> out = new ArrayList<>();
        for (ExtractedItem item : items) {
            String key = item.title() + "|" + item.range().start() + "|" + item.range().end();
            if (seen.add(key)) {
                out.add(item);
            }
        }
        return out;
    }

    static String gradeOf(String text) {
        if (text == null) {
            return null;
        }
        if (ALL_GRADES.matcher(text).find()) {
            return "ALL";
        }
        // "2~4학년"처럼 범위로 적힌 것은 한 학년이 아니다. 모름으로 두고 검수에서 정한다.
        if (GRADE_RANGE.matcher(text).find()) {
            return null;
        }
        // "4학년 (약대 4학년 이상)" 처럼 같은 학년이 두 번 나오면 그 학년, 다른 학년이 섞이면 모름. "2024학년도"는 학년이 아니다.
        Set<String> grades = new LinkedHashSet<>();
        Matcher m = GRADE.matcher(text);
        while (m.find()) {
            grades.add(m.group(1));
        }
        return grades.size() == 1 ? grades.iterator().next() : null;
    }

    /** 라벨에서 주소, 글머리표, "1." "가." 같은 번호를 뗀다. */
    static String cleanLabel(String s) {
        String t = s.replaceAll("https?://\\S+", " ");
        // 글머리표, 이모지, 쌍점처럼 글자가 아닌 앞머리는 뗀다. 괄호는 "(1차)"처럼 뜻이 있어 남긴다.
        t = t.replaceFirst("^[^\\p{L}\\p{N}(\\[]+", "");
        t = t.replaceFirst("^(\\d{1,2}|[가-힣]|[①-⑳]|[a-zA-Z])\\s*[.)]\\s*", "");
        t = t.replaceFirst("^[\\s\\-–—·※▶▷•○●■□◦‣▪◆◇☞✔]+", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String clip(String s) {
        return s.length() <= TITLE_LIMIT ? s : s.substring(0, TITLE_LIMIT - 1) + "…";
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static final class Carry {
        final String text;
        int remaining;

        Carry(String text, int remaining) {
            this.text = text;
            this.remaining = remaining;
        }
    }
}
