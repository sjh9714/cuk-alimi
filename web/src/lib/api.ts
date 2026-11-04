// API 호출. 쿠키 세션이라 credentials를 항상 보낸다.
// 실패하면 서버의 ProblemDetail detail을 메시지로 던진다.

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "";

/** 캘린더 앱에 넣는 구독 주소. API가 같은 origin이면 지금 주소에 붙인다. */
export function calendarUrl(token: string): string {
  const base = API_BASE || (typeof window === "undefined" ? "" : window.location.origin);
  return `${base}/api/calendar/${token}.ics`;
}

export type NoticeRef = {
  articleNo: number;
  title: string;
  url: string;
  category: string;
  reviewReason: "PARSED" | "IMAGE_ONLY" | "NO_DATE" | "PARSE_FAILED";
  status: "NEEDS_REVIEW" | "REVIEWED";
};

export type Item = {
  id: number;
  title: string;
  startsOn: string | null;
  endsOn: string;
  startsAt: string | null;
  endsAt: string | null;
  sourceKind: "SCHEDULE" | "NOTICE" | "MANUAL";
  reviewed: boolean;
  targetGrade: string | null;
  targetDept: string | null;
  targetNote: string | null;
  campus: string | null;
  notice: NoticeRef | null;
  bucket: 0 | 1 | 2;
  saved: boolean;
  /** 그림을 받아 적은 글에서 읽은 후보. 검수 화면에만 온다. */
  fromImage: boolean;
};

export type Benefit = {
  id: number;
  slug: string;
  name: string;
  category: string;
  summary: string;
  condition: string;
  howTo: string;
  price: string | null;
  sourceName: string;
  sourceUrl: string;
  verifiedOn: string;
  renewalRule: string;
  endsOn: string | null;
  stale: boolean;
  checked: boolean;
};

export type Me = {
  email: string;
  entryYear: number | null;
  major: string | null;
  secondMajor: string | null;
  homeRegion: string | null;
  /** 이번 학기에서 나온 학년. */
  grade: number | null;
  /** 이번 학기(1~8, 9는 초과학기). */
  semester: number | null;
  /** 입학년도로만 센 학기. */
  autoSemester: number | null;
  semesterManual: boolean;
  admin: boolean;
  digestEnabled: boolean;
  /** 캘린더 구독 주소의 토큰. 다시 만들면 이전 주소는 끊긴다. */
  calendarToken: string;
};

/** 푸시가 켜져 있는지, 구독에 쓰는 VAPID 공개 키, 이 계정에 등록된 기기 수. */
export type PushState = { enabled: boolean; publicKey: string | null; devices: number };

export type Scope = "GENERAL" | "REGION" | "GROUP" | "FIELD" | "RESULT";

/** 카드 안의 날짜 한 줄. label이 없으면 공지 제목 그 자체의 마감이다. */
export type Line = {
  id: number;
  label: string | null;
  startsOn: string | null;
  endsOn: string;
  startsAt: string | null;
  endsAt: string | null;
  kind: "ACTION" | "INFO";
  saved: boolean;
  /** 로그인한 학생이 "끝냈어요"로 체크한 줄. 로그인 전에는 항상 false다. */
  done: boolean;
};

/** deadline은 날짜가 그림에만 있어 확인 중인 카드(undated 구간)에서만 null이다. */
export type Card = {
  key: string;
  kind: "schedule" | "scholarship" | "academic" | "career" | "dept";
  title: string;
  sourceTitle: string;
  url: string;
  /** grade는 제목·자격 문단에 적힌 학년("2~4학년", "졸업예정자") */
  audience: { scope: Scope; label: string | null; excerpt: string | null; grade: string | null };
  mine: boolean;
  /** 내게 맞는 이유("3학년·컴퓨터정보공학부 대상"). mine일 때만 있다 */
  mineReason: string | null;
  deadline: Line | null;
  next: Line[];
};

export type Section = {
  key: "must" | "mine" | "open" | "undated" | "career" | "region" | "group" | "dept" | "events";
  title: string;
  note: string;
  collapsed: boolean;
  cards: Card[];
};

export type Feed = { sections: Section[]; signedIn: boolean; hasHomeRegion: boolean };

export type Sido = { name: string; sigungu: string[] };

export type ReviewNotice = {
  id: number;
  articleNo: number;
  category: string;
  title: string;
  postedOn: string;
  url: string;
  shortTitle: string;
  reviewReason: NoticeRef["reviewReason"];
  hasImages: boolean;
  attachments: string;
  status: NoticeRef["status"];
  audienceScope: Scope;
  audienceLabel: string | null;
  audienceExcerpt: string | null;
  audienceOverridden: boolean;
  /** 0 = 모든 재학생, 1 = 일반, 2 = 지역·특정 대상 */
  priority: 0 | 1 | 2;
  items: Item[];
};

export type ReviewQueue = {
  notices: ReviewNotice[];
  closed: { PAST: number; RESULT: number; STALE: number };
};

export type FeedbackEntry = { id: number; message: string; createdAt: string };

export type BacktestScore = {
  notices: number;
  exactNotices: number;
  truePositive: number;
  falsePositive: number;
  falseNegative: number;
  precision: number;
  recall: number;
};

/** 공개 품질 페이지 수치 (ADR 011). */
export type Quality = {
  backtest: {
    measuredOn: string;
    notices: number;
    deadlines: number;
    all: BacktestScore;
    text: BacktestScore;
    image: BacktestScore;
    remaining: { type: string; description: string; missed: number; invented: number }[];
  };
  review: {
    judged: number;
    approvedAsIs: number;
    edited: number;
    deleted: number;
    added: number;
    fromImageJudged: number;
    fromImageApprovedAsIs: number;
    noticesReviewed: number;
  };
  transcription: { images: number; skipped: number; inputTokens: number; outputTokens: number; costUsd: number };
  autoPublish: { published: number; waiting: number; reports: number };
  /** 형광펜 백테스트. 아직 재지 않았으면 null */
  audienceBacktest: AudienceBacktest | null;
  audienceRead: { enabled: boolean; notices: number; agreed: number; inputTokens: number; outputTokens: number; costUsd: number };
};

export type AudienceScore = {
  precision: number;
  recall: number;
  expected: number;
  predicted: number;
  hit: number;
  exactNotices: number;
};

export type AudienceBacktest = {
  measuredOn: string;
  /** holdout이면 지시문을 고칠 때 보지 않은 시험용 공지로 잰 값이다. */
  set: "holdout" | "dev";
  notices: number;
  students: number;
  goldModel: string;
  humanChecked: number;
  runModel: string;
  runSource: string;
  agreed: number;
  regex: AudienceScore;
  ai: AudienceScore;
};

export type ItemEdit = {
  title: string;
  startsOn: string | null;
  endsOn: string;
  startsAt: string | null;
  endsAt: string | null;
  targetGrade: string | null;
  targetDept: string | null;
  targetNote: string | null;
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(API_BASE + path, {
      method,
      credentials: "include",
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
  }
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const problem = await res.json();
      if (problem && problem.detail) detail = problem.detail;
    } catch {
      // 본문 없음
    }
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  feed: () => call<Feed>("GET", "/api/feed"),
  regions: () => call<Sido[]>("GET", "/api/regions"),
  benefits: () => call<Benefit[]>("GET", "/api/benefits"),
  me: async (): Promise<Me | null> => {
    try {
      return await call<Me>("GET", "/api/me");
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return null;
      throw e;
    }
  },
  requestLogin: (email: string) => call<{ message: string }>("POST", "/api/auth/request", { email }),
  logout: () => call<void>("POST", "/api/auth/logout"),
  setDigest: (enabled: boolean) => call<Me>("PUT", "/api/me/digest", { enabled }),
  rotateCalendar: () => call<Me>("POST", "/api/me/calendar/rotate"),
  pushState: () => call<PushState>("GET", "/api/push"),
  subscribePush: (sub: PushSubscriptionJSON) => call<void>("POST", "/api/me/push", sub),
  unsubscribePush: (endpoint: string) => call<void>("DELETE", "/api/me/push", { endpoint }),
  deleteAccount: () => call<void>("DELETE", "/api/me"),
  leaveFeedback: (message: string) => call<void>("POST", "/api/feedback", { message }),
  feedback: () => call<FeedbackEntry[]>("GET", "/api/admin/feedback"),
  meta: () => call<{ contactEmail: string }>("GET", "/api/meta"),
  quality: () => call<Quality>("GET", "/api/quality"),
  updateProfile: (p: { entryYear: number | null; semester: number | null; major: string; secondMajor: string; homeRegion: string }) =>
    call<Me>("PUT", "/api/me/profile", p),
  save: (id: number) => call<void>("POST", `/api/me/saved/${id}`),
  unsave: (id: number) => call<void>("DELETE", `/api/me/saved/${id}`),
  markDone: (id: number) => call<void>("POST", `/api/me/done/${id}`),
  unmarkDone: (id: number) => call<void>("DELETE", `/api/me/done/${id}`),
  reportDate: (id: number) => call<void>("POST", `/api/items/${id}/report`),
  check: (id: number) => call<void>("POST", `/api/me/benefits/${id}`),
  uncheck: (id: number) => call<void>("DELETE", `/api/me/benefits/${id}`),
  review: () => call<ReviewQueue>("GET", "/api/admin/review"),
  editAudience: (noticeId: number, scope: Scope | null, label: string) =>
    call<ReviewNotice>("PUT", `/api/admin/notices/${noticeId}/audience`, { scope, label }),
  markReviewed: (noticeId: number) => call<ReviewNotice>("POST", `/api/admin/notices/${noticeId}/reviewed`),
  editItem: (itemId: number, edit: ItemEdit) => call<Item>("PUT", `/api/admin/items/${itemId}`, edit),
  addItem: (noticeId: number, edit: ItemEdit) => call<Item>("POST", `/api/admin/notices/${noticeId}/items`, edit),
  deleteItem: (itemId: number) => call<void>("DELETE", `/api/admin/items/${itemId}`),
};

export function dday(endsOn: string, today = new Date()): number {
  const end = new Date(endsOn + "T00:00:00");
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((end.getTime() - base.getTime()) / 86_400_000);
}

export function formatDate(d: string | null): string {
  if (!d) return "";
  const [y, m, day] = d.split("-");
  return `${y}.${m}.${day}`;
}

export function formatRange(item: Pick<Item, "startsOn" | "endsOn" | "startsAt" | "endsAt">): string {
  const start = item.startsOn && item.startsOn !== item.endsOn ? formatDate(item.startsOn) : "";
  const end = formatDate(item.endsOn);
  const time = item.endsAt ? ` ${item.endsAt.slice(0, 5)}` : "";
  const startTime = item.startsAt && !start ? `${item.startsAt.slice(0, 5)}~` : "";
  if (start) return `${start} ~ ${end}${time}`;
  return `${end} ${startTime}${time}`.trim();
}
