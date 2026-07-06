import { Plus } from "lucide-react";
import { useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { toast } from "@/shared/lib/toast";

import type {
  Flashcard,
  FlashcardCreateRequest,
  FlashcardMutationPayload,
} from "../api/flashcards";
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

export function FlashcardListTab({ materialId }: Props) {
  const flashcardsQuery = useFlashcards(materialId);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Flashcard | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  const createMutation = useCreateFlashcard(materialId);
  const updateMutation = useUpdateFlashcard(materialId, editing?.id ?? 0);
  const deleteMutation = useDeleteFlashcard(materialId);

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

  if (flashcardsQuery.isLoading) {
    return <LoadingText />;
  }
  if (flashcardsQuery.isError || !flashcardsQuery.data) {
    return <FieldError>카드를 불러오지 못했어요.</FieldError>;
  }

  const flashcards = flashcardsQuery.data;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <p className="text-muted-foreground text-sm">
          총 {flashcards.length}장
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" /> 카드 추가
        </Button>
      </div>

      {flashcards.length === 0 ? (
        <EmptyFlashcardState onAdd={() => setCreateOpen(true)} />
      ) : (
        <ul className="flex flex-col gap-3">
          {flashcards.map((card, idx) => (
            <li key={card.id}>
              <FlashcardItem
                index={idx}
                flashcard={card}
                onEdit={() => setEditing(card)}
              />
            </li>
          ))}
        </ul>
      )}

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
