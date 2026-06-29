import { type ClassValue, clsx } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

// 커스텀 타이포 스케일 토큰(text-title 등)을 font-size 그룹으로 등록한다.
// 없으면 tailwind-merge가 text-title을 text-색과 같은 text-* 그룹으로 오인해
// `cn("text-muted-foreground text-caption")`에서 색이 드롭된다.
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [
        { text: ["display", "title", "heading", "body", "label", "caption"] },
      ],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
