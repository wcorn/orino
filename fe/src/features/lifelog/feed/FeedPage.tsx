import { Layers, Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { Button, buttonVariants } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { Modal } from "@/components/ui/modal";

import type { MomentCard as MomentCardType } from "../api/types";
import { MomentEditor } from "../compose/MomentEditor";
import { useFeed } from "../hooks/useFeed";
import { useDeleteMoment } from "../hooks/useMomentMutations";
import { MomentCard } from "./MomentCard";

/** 일상기록 피드 — 역시간순 무한 스크롤 + 작성/수정/삭제. */
export function FeedPage() {
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useFeed();
  const deleteMutation = useDeleteMoment();

  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<MomentCardType | undefined>();
  const [deleting, setDeleting] = useState<MomentCardType | undefined>();

  const moments = data?.pages.flatMap((page) => page.items) ?? [];

  const openCreate = () => {
    setEditing(undefined);
    setEditorOpen(true);
  };
  const openEdit = (moment: MomentCardType) => {
    setEditing(moment);
    setEditorOpen(true);
  };

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-4 p-4">
      <header className="flex items-center justify-between">
        <h1 className="text-heading font-semibold">일상기록</h1>
        <div className="flex items-center gap-2">
          <Link
            to="/lifelog/flows"
            className={buttonVariants({ variant: "outline", size: "default" })}
          >
            <Layers />
            흐름
          </Link>
          <Button onClick={openCreate}>
            <Plus />
            기록
          </Button>
        </div>
      </header>

      {isLoading ? (
        <LoadingText />
      ) : moments.length === 0 ? (
        <p className="text-muted-foreground py-16 text-center text-sm">
          첫 순간을 기록해보세요.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {moments.map((moment) => (
            <MomentCard
              key={moment.id}
              moment={moment}
              onEdit={openEdit}
              onDelete={setDeleting}
            />
          ))}
        </div>
      )}

      {hasNextPage && (
        <Button
          variant="outline"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
        >
          {isFetchingNextPage ? "불러오는 중..." : "더 보기"}
        </Button>
      )}

      <MomentEditor
        open={editorOpen}
        onOpenChange={setEditorOpen}
        moment={editing}
      />

      <Modal
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title="기록 삭제"
        description="이 기록을 삭제하시겠어요? 되돌릴 수 없습니다."
        size="sm"
      >
        <Modal.Footer
          destructive
          submitLabel="삭제"
          pending={deleteMutation.isPending}
          pendingLabel="삭제 중..."
          onSubmit={() => {
            if (!deleting) return;
            deleteMutation.mutate(deleting.id, {
              onSuccess: () => setDeleting(undefined),
            });
          }}
        />
      </Modal>
    </div>
  );
}
