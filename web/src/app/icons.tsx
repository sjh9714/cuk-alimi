/** 화면에 쓰는 아이콘. 선 굵기 1.75, 24 격자로 직접 그렸다. 글자처럼 currentColor를 따른다. */

type Props = { size?: number };

function Svg({ size = 16, children }: Props & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      focusable="false"
    >
      {children}
    </svg>
  );
}

/** 새 창으로 여는 바깥 링크 */
export function ExternalIcon(p: Props) {
  return (
    <Svg {...p}>
      <path d="M14 5h5v5" />
      <path d="M19 5l-8 8" />
      <path d="M17 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h4" />
    </Svg>
  );
}

/** 저장(책갈피). 저장된 상태는 속을 채운다. */
export function BookmarkIcon({ filled, ...p }: Props & { filled?: boolean }) {
  return (
    <Svg {...p}>
      <path d="M7 4h10a1 1 0 0 1 1 1v15l-6-4-6 4V5a1 1 0 0 1 1-1z" fill={filled ? "currentColor" : "none"} />
    </Svg>
  );
}

export function ChevronIcon({ dir = "down", ...p }: Props & { dir?: "down" | "left" | "right" }) {
  const d = dir === "down" ? "M6 9l6 6 6-6" : dir === "left" ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6";
  return (
    <Svg {...p}>
      <path d={d} />
    </Svg>
  );
}

/** 아래 탭의 일정 */
export function CalendarIcon(p: Props) {
  return (
    <Svg {...p}>
      <rect x="4" y="5" width="16" height="15" rx="3.5" />
      <path d="M4 10h16M8.5 3v3.5M15.5 3v3.5" />
    </Svg>
  );
}

/** 아래 탭의 혜택 */
export function TagIcon(p: Props) {
  return (
    <Svg {...p}>
      <path d="M4 12.2V5.5A1.5 1.5 0 0 1 5.5 4h6.7l8 8-7.9 8.2z" />
      <circle cx="8.6" cy="8.6" r="1.3" />
    </Svg>
  );
}

/** 아래 탭의 설정 */
export function SlidersIcon(p: Props) {
  return (
    <Svg {...p}>
      <path d="M5 7h9M18 7h1M5 12h2M11 12h8M5 17h9M18 17h1" />
      <circle cx="16" cy="7" r="2" />
      <circle cx="9" cy="12" r="2" />
      <circle cx="16" cy="17" r="2" />
    </Svg>
  );
}

export function PlusIcon(p: Props) {
  return (
    <Svg {...p}>
      <path d="M12 5v14M5 12h14" />
    </Svg>
  );
}

export function CheckIcon(p: Props) {
  return (
    <Svg {...p}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </Svg>
  );
}
