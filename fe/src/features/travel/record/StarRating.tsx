import { Star } from "lucide-react";

/** 별 5개(§S-07). 서버 검증(1~5)과 같은 값이다. */
export const MAX_RATING = 5;

interface StarRatingProps {
  /** null이면 아직 매기지 않은 것이다. 0이 아니다 — 0점 평가는 없다. */
  value: number | null;
  onChange: (value: number | null) => void;
  disabled?: boolean;
}

/**
 * 별점.
 *
 * <p><b>누른 별을 다시 누르면 해제된다.</b> 되돌릴 방법이 없으면 별점은 함정이 된다 —
 * 잘못 눌러 1점이 박힌 일정을 "안 매김"으로 되돌릴 길이 있어야 한다.
 */
export function StarRating({ value, onChange, disabled }: StarRatingProps) {
  return (
    <div className="flex items-center gap-0.5" role="group" aria-label="평점">
      {Array.from({ length: MAX_RATING }, (_, i) => i + 1).map((star) => {
        const filled = value !== null && star <= value;
        return (
          <button
            key={star}
            type="button"
            disabled={disabled}
            // 같은 별을 다시 누르면 해제.
            onClick={() => onChange(value === star ? null : star)}
            aria-label={`${star}점`}
            aria-pressed={filled}
            className="disabled:opacity-50"
          >
            <Star
              className={`size-7 ${
                filled ? "fill-warning text-warning" : "text-muted-foreground"
              }`}
            />
          </button>
        );
      })}
    </div>
  );
}
