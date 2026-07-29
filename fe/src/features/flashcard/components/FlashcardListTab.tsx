import { Plus, Search } from "lucide-react";
import { useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Select, type SelectOption } from "@/components/ui/select";
import { toast } from "@/shared/lib/toast";
import { useDebouncedValue } from "@/shared/lib/useDebouncedValue";
import { useInfiniteScroll } from "@/shared/lib/useInfiniteScroll";

import type {
  CardTypeFilter,
  Flashcard,
  FlashcardCreateRequest,
  FlashcardMutationPayload,
  FlashcardSort,
  ReviewStatusFilter,
} from "../api/flashcards";
import { groupFlashcards } from "../grouping";
import { useCreateFlashcard } from "../hooks/useCreateFlashcard";
import { useDeleteFlashcard } from "../hooks/useDeleteFlashcard";
import { useFlashcards } from "../hooks/useFlashcards";
import { useUpdateFlashcard } from "../hooks/useUpdateFlashcard";
import { EmptyFlashcardState } from "./EmptyFlashcardState";
import { FlashcardFormDialog } from "./FlashcardFormDialog";
import { FlashcardItem } from "./FlashcardItem";

interface Props {
  materialId: number;
}

const TYPE_OPTIONS: SelectOption<CardTypeFilter>[] = [
  { value: "all", label: "전체 종류" },
  { value: "basic", label: "기본" },
  { value: "pair", label: "양방향" },
  { value: "order", label: "순서" },
];

const REVIEW_OPTIONS: SelectOption<ReviewStatusFilter>[] = [
  { value: "all", label: "전체 복습" },
  { value: "overdue", label: "밀림" },
  { value: "today", label: "오늘" },
  { value: "upcoming", label: "예정" },
];

const SORT_OPTIONS: SelectOption<FlashcardSort>[] = [
  { value: "created_asc", label: "오래된순" },
  { value: "created_desc", label: "최신순" },
];

export function FlashcardListTab({ materialId }: Props) {
  const [search, setSearch] = useState("");
  const [type, setType] = useState<CardTypeFilter>("all");
  const [review, setReview] = useState<ReviewStatusFilter>("all");
  const [sort, setSort] = useState<FlashcardSort>("created_asc");
  const debouncedSearch = useDebouncedValue(search.trim(), 300);
  const filtered = debouncedSearch !== "" || type !== "all" || review !== "all";

  const flashcardsQuery = useFlashcards(materialId, {
    q: debouncedSearch || undefined,
    type,
    review,
    sort,
  });

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Flashcard | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  const createMutation = useCreateFlashcard(materialId);
  const updateMutation = useUpdateFlashcard(materialId, editing?.id ?? 0);
  const deleteMutation = useDeleteFlashcard(materialId);

  const sentinelRef = useInfiniteScroll(
    () => flashcardsQuery.fetchNextPage(),
    Boolean(flashcardsQuery.hasNextPage) && !flashcardsQuery.isFetchingNextPage,
  );

  const handleCreate = (
    values: FlashcardMutationPayload,
    { bidirectional }: { bidirectional: boolean },
  ) => {
    const request: FlashcardCreateRequest =
      bidirectional && values.type === "BASIC"
        ? { ...values, bidirectional: true }
        : values;
    createMutation.mutate(request, {
      onSuccess: () => {
        setCreateOpen(false);
        toast(
          bidirectional
            ? "양방향 카드 2장이 추가되었어요."
            : "카드가 추가되었어요. 첫 복습은 내일.",
          "success",
        );
      },
      onError: () =>
        toast("카드 추가에 실패했어요. 잠시 후 다시 시도해주세요.", "error"),
    });
  };

  const handleUpdate = (values: FlashcardMutationPayload) => {
    if (!editing) return;
    updateMutation.mutate(values, {
      onSuccess: () => setEditing(null),
      onError: () =>
        toast("저장에 실패했어요. 잠시 후 다시 시도해주세요.", "error"),
    });
  };

  const handleConfirmDelete = () => {
    if (!editing) return;
    deleteMutation.mutate(editing.id, {
      onSuccess: () => {
        setDeleteConfirmOpen(false);
        setEditing(null);
      },
    });
  };

  const resetFilters = () => {
    setSearch("");
    setType("all");
    setReview("all");
  };

  const pages = flashcardsQuery.data?.pages ?? [];
  const cards = pages.flatMap((p) => p.flashcards);
  const totalCount = pages[0]?.totalCount ?? 0;
  const rows = groupFlashcards(cards);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-muted-foreground text-sm">
          {filtered ? `${totalCount}장 찾음` : `총 ${totalCount}장`}
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" /> 카드 추가
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-40 flex-1">
          <Search
            aria-hidden
            className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2"
          />
          <Input
            aria-label="카드 검색"
            placeholder="앞면·뒷면 검색"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>
        <Select value={type} onValueChange={setType} options={TYPE_OPTIONS} />
        <Select
          value={review}
          onValueChange={setReview}
          options={REVIEW_OPTIONS}
        />
        <Select value={sort} onValueChange={setSort} options={SORT_OPTIONS} />
      </div>

      <FlashcardList
        query={flashcardsQuery}
        rows={rows}
        filtered={filtered}
        onAdd={() => setCreateOpen(true)}
        onReset={resetFilters}
        onEdit={setEditing}
        sentinelRef={sentinelRef}
      />

      <FlashcardFormDialog
        mode="create"
        open={createOpen}
        onOpenChange={(o) => {
          setCreateOpen(o);
          if (!o) createMutation.reset();
        }}
        pending={createMutation.isPending}
        onSubmit={handleCreate}
      />

      <FlashcardFormDialog
        mode="edit"
        open={editing !== null}
        onOpenChange={(o) => {
          if (!o) {
            setEditing(null);
            updateMutation.reset();
          }
        }}
        initialType={editing?.type}
        initialFront={editing?.front}
        initialBack={editing?.back}
        initialItems={editing?.items}
        pending={updateMutation.isPending}
        onSubmit={handleUpdate}
        onDelete={() => setDeleteConfirmOpen(true)}
      />

      <ConfirmDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title="카드를 삭제할까요?"
        description="이 카드의 복습 일정도 함께 삭제됩니다. 되돌릴 수 없어요."
        confirmLabel="삭제"
        destructive
        onConfirm={handleConfirmDelete}
        pending={deleteMutation.isPending}
      />
    </div>
  );
}

interface ListProps {
  query: ReturnType<typeof useFlashcards>;
  rows: ReturnType<typeof groupFlashcards>;
  filtered: boolean;
  onAdd: () => void;
  onReset: () => void;
  onEdit: (card: Flashcard) => void;
  sentinelRef: React.RefObject<HTMLDivElement | null>;
}

function FlashcardList({
  query,
  rows,
  filtered,
  onAdd,
  onReset,
  onEdit,
  sentinelRef,
}: ListProps) {
  if (query.isLoading) return <LoadingText />;
  if (query.isError || !query.data) {
    return <FieldError>카드를 불러오지 못했어요.</FieldError>;
  }
  if (rows.length === 0) {
    // 필터 때문에 빈 것과 카드가 아예 없는 것은 다른 상황이다
    return filtered ? (
      <EmptyState className="min-h-[30svh]">
        <p className="text-muted-foreground text-sm">
          조건에 맞는 카드가 없어요.
        </p>
        <Button variant="outline" onClick={onReset}>
          필터 초기화
        </Button>
      </EmptyState>
    ) : (
      <EmptyFlashcardState onAdd={onAdd} />
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {/* 행마다 Card로 감싸는 대신 컨테이너 하나 + divide-y — 여백이 내용보다 커지는 걸 막는다 */}
      <ul className="divide-border bg-card ring-foreground/10 divide-y overflow-hidden rounded-xl ring-1">
        {rows.map((row) => (
          <li key={row.key}>
            <FlashcardItem row={row} onEdit={onEdit} />
          </li>
        ))}
      </ul>
      <div ref={sentinelRef} aria-hidden />
      {query.hasNextPage && (
        <p className="text-muted-foreground py-2 text-center text-xs">
          스크롤하면 계속 불러옵니다
        </p>
      )}
    </div>
  );
}
