import { ChevronRight, Pencil } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

import type { Flashcard } from "../api/flashcards";
import type { FlashcardRow } from "../grouping";
import { formatNextReview } from "../utils";

interface Props {
  row: FlashcardRow;
  onEdit: (card: Flashcard) => void;
}

/**
 * 목록의 카드 한 행. **기본은 접힘** — 앞면 한 줄만 보이고 펼쳐야 뒷면/항목이 나온다.
 * 항상 펼쳐 두면 한 장이 100~200px를 먹어 수십 장만으로도 스크롤이 감당 불가가 된다.
 */
export function FlashcardItem({ row, onEdit }: Props) {
  const [expanded, setExpanded] = useState(false);
  const head = row.kind === "pair" ? row.cards[0] : row.card;

  return (
    <Card>
      <CardContent className="flex items-start gap-2 py-3">
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((v) => !v)}
          className="flex min-w-0 flex-1 items-start gap-2 text-left"
        >
          <ChevronRight
            aria-hidden
            className={`text-muted-foreground mt-0.5 size-4 shrink-0 transition-transform ${
              expanded ? "rotate-90" : ""
            }`}
          />
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <div className="flex min-w-0 items-center gap-2">
              <TypeBadge row={row} />
              <span
                className={`min-w-0 text-sm font-medium ${
                  expanded ? "whitespace-pre-line" : "truncate"
                }`}
              >
                {head.front}
              </span>
            </div>
            {!expanded && head.nextReview && (
              <span className="text-muted-foreground text-xs">
                다음 복습: {formatNextReview(head.nextReview)}
              </span>
            )}
          </div>
        </button>

        {row.kind === "single" && (
          <EditButton card={row.card} onEdit={onEdit} />
        )}
      </CardContent>

      {expanded && (
        <CardContent className="flex flex-col gap-3 pt-0 pl-8">
          {row.kind === "pair" ? (
            row.cards.map((card) => (
              <div key={card.id} className="flex items-start gap-2">
                <div className="flex min-w-0 flex-1 flex-col gap-1">
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
        </CardContent>
      )}
    </Card>
  );
}

function CardBody({ card }: { card: Flashcard }) {
  return (
    <div className="flex flex-col gap-1">
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
      <Badge variant="outline" className="shrink-0">
        ⇄ 양방향
      </Badge>
    );
  }
  if (row.card.type === "ORDERING") {
    return (
      <Badge variant="outline" className="shrink-0">
        순서
      </Badge>
    );
  }
  return null;
}
