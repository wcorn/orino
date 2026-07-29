import { ChevronRight, Pencil } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import type { Flashcard } from "../api/flashcards";
import type { FlashcardRow } from "../grouping";
import { formatNextReview } from "../utils";

interface Props {
  row: FlashcardRow;
  onEdit: (card: Flashcard) => void;
}

/**
 * 목록의 카드 한 행. **기본은 접힘 + 한 줄** — 앞면·복습일이 같은 줄에 오고,
 * 펼쳐야 뒷면/항목이 나온다.
 *
 * 행마다 Card로 감싸지 않는다. Card는 `py-4`가 붙어 내용(한 줄 ~20px)보다 패딩이 더 커지고,
 * 카드 사이 간격까지 더해져 실제 정보 대비 여백이 과해진다. 목록 컨테이너 하나에
 * `divide-y`로 구분선만 두는 편이 같은 화면에 몇 배 더 담긴다.
 */
export function FlashcardItem({ row, onEdit }: Props) {
  const [expanded, setExpanded] = useState(false);
  const head = row.kind === "pair" ? row.cards[0] : row.card;

  return (
    <div className="hover:bg-muted/40 flex flex-col">
      <div className="flex items-center gap-1 pr-1">
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((v) => !v)}
          className="flex min-w-0 flex-1 items-center gap-2 py-1.5 pl-2 text-left"
        >
          <ChevronRight
            aria-hidden
            className={`text-muted-foreground size-3.5 shrink-0 transition-transform ${
              expanded ? "rotate-90" : ""
            }`}
          />
          <TypeBadge row={row} />
          <span className="min-w-0 flex-1 truncate text-sm">{head.front}</span>
          {head.nextReview && (
            <span className="text-muted-foreground hidden shrink-0 text-xs sm:inline">
              {formatNextReview(head.nextReview)}
            </span>
          )}
        </button>

        {row.kind === "single" && (
          <EditButton card={row.card} onEdit={onEdit} />
        )}
      </div>

      {expanded && (
        <div className="flex flex-col gap-2 pt-1 pr-2 pb-2 pl-7">
          {row.kind === "pair" ? (
            row.cards.map((card) => (
              <div key={card.id} className="flex items-start gap-2">
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <p className="text-sm whitespace-pre-line">
                    {card.front} → {card.back}
                  </p>
                  <NextReviewLine card={card} />
                </div>
                <EditButton card={card} onEdit={onEdit} />
              </div>
            ))
          ) : (
            <CardBody card={row.card} />
          )}
        </div>
      )}
    </div>
  );
}

function CardBody({ card }: { card: Flashcard }) {
  return (
    <div className="flex flex-col gap-0.5">
      {card.type === "ORDERING" ? (
        <ol className="text-muted-foreground flex list-decimal flex-col gap-0.5 pl-4 text-xs">
          {card.items?.map((item) => (
            <li key={item.id} className="whitespace-pre-line">
              {item.text}
            </li>
          ))}
        </ol>
      ) : (
        <p className="text-muted-foreground text-xs whitespace-pre-line">
          뒤: {card.back}
        </p>
      )}
      <NextReviewLine card={card} />
    </div>
  );
}

function NextReviewLine({ card }: { card: Flashcard }) {
  if (!card.nextReview) return null;
  return (
    <span className="text-muted-foreground text-xs">
      다음 복습: {formatNextReview(card.nextReview)}
    </span>
  );
}

function EditButton({
  card,
  onEdit,
}: {
  card: Flashcard;
  onEdit: (card: Flashcard) => void;
}) {
  return (
    <Button
      variant="ghost"
      size="icon-sm"
      aria-label={`${card.front} 카드 편집`}
      onClick={() => onEdit(card)}
    >
      <Pencil className="size-4" />
    </Button>
  );
}

function TypeBadge({ row }: { row: FlashcardRow }) {
  if (row.kind === "pair") {
    return (
      <Badge variant="outline" className="shrink-0 px-1.5 py-0">
        ⇄ 양방향
      </Badge>
    );
  }
  if (row.card.type === "ORDERING") {
    return (
      <Badge variant="outline" className="shrink-0 px-1.5 py-0">
        순서
      </Badge>
    );
  }
  return null;
}
