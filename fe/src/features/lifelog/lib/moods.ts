import type { Mood } from "../api/types";

export const MOODS: { value: Mood; emoji: string; label: string }[] = [
  { value: "HAPPY", emoji: "😊", label: "기쁨" },
  { value: "CALM", emoji: "😌", label: "평온" },
  { value: "EXCITED", emoji: "😆", label: "신남" },
  { value: "TIRED", emoji: "😴", label: "피곤" },
  { value: "SAD", emoji: "😢", label: "슬픔" },
];

export function moodEmoji(mood: Mood | null): string | null {
  return MOODS.find((m) => m.value === mood)?.emoji ?? null;
}
