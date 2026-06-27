export interface PlanColor {
  key: string;
  label: string;
  /** 블록 배경 Tailwind 클래스. */
  block: string;
  /** 색 선택 스와치 클래스. */
  swatch: string;
}

export const PLAN_COLORS: PlanColor[] = [
  {
    key: "violet",
    label: "보라",
    block: "bg-violet-500",
    swatch: "bg-violet-500",
  },
  { key: "sky", label: "하늘", block: "bg-sky-500", swatch: "bg-sky-500" },
  {
    key: "amber",
    label: "앰버",
    block: "bg-amber-500",
    swatch: "bg-amber-500",
  },
  {
    key: "emerald",
    label: "초록",
    block: "bg-emerald-500",
    swatch: "bg-emerald-500",
  },
  { key: "rose", label: "분홍", block: "bg-rose-500", swatch: "bg-rose-500" },
  {
    key: "slate",
    label: "회색",
    block: "bg-slate-500",
    swatch: "bg-slate-500",
  },
];

export const DEFAULT_COLOR = "violet";

/** 색 키 → 블록 배경 클래스(미지정/알 수 없으면 primary). */
export function blockColorClass(color: string | null): string {
  return PLAN_COLORS.find((c) => c.key === color)?.block ?? "bg-primary";
}
