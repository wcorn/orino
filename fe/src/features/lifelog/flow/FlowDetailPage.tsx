import { ArrowLeft, MoreHorizontal, Plus } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { Button, buttonVariants } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { Menu, MenuItem } from "@/components/ui/menu";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/lib/utils";

import {
  useDeleteFlow,
  useRemoveMoment,
  useReorderMoments,
} from "../hooks/useFlowMutations";
import { useFlow } from "../hooks/useFlows";
import { formatFlowPeriod } from "../lib/datetime";
import { AddMomentsModal } from "./AddMomentsModal";
import { FlowEditModal } from "./FlowEditModal";
import { FlowMap } from "./FlowMap";
import { FlowTimeline } from "./FlowTimeline";

type View = "timeline" | "map";

/** 흐름 상세 — 헤더 + [타임라인|지도] 토글 + 담기. (지도 뷰는 #958에서 채운다) */
export function FlowDetailPage() {
  const { id } = useParams<{ id: string }>();
  const flowId = Number(id);
  const navigate = useNavigate();

  const { data: flow, isLoading } = useFlow(flowId);
  const removeMutation = useRemoveMoment(flowId);
  const reorderMutation = useReorderMoments(flowId);
  const deleteMutation = useDeleteFlow();

  const [view, setView] = useState<View>("timeline");
  const [addOpen, setAddOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  if (isLoading) {
    return (
      <div className="p-8">
        <LoadingText />
      </div>
    );
  }
  if (!flow) {
    return (
      <p className="text-muted-foreground p-8 text-center text-sm">
        흐름을 찾을 수 없습니다.
      </p>
    );
  }

  const period = formatFlowPeriod(flow.startedAt, flow.endedAt);

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-4 p-4">
      <header className="flex items-start gap-2">
        <Link
          to="/lifelog/flows"
          aria-label="흐름 목록"
          className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
        >
          <ArrowLeft />
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="text-heading truncate font-semibold">{flow.title}</h1>
          <p className="text-muted-foreground text-xs">
            {flow.description && <span>{flow.description} · </span>}
            {period && <span>{period} · </span>}기록 {flow.moments.length}
          </p>
        </div>
        <Button onClick={() => setAddOpen(true)} size="sm">
          <Plus />
          담기
        </Button>
        <Menu
          trigger={
            <Button size="icon-sm" variant="ghost" aria-label="흐름 메뉴">
              <MoreHorizontal />
            </Button>
          }
        >
          <MenuItem onClick={() => setEditOpen(true)}>편집</MenuItem>
          <MenuItem variant="destructive" onClick={() => setDeleteOpen(true)}>
            삭제
          </MenuItem>
        </Menu>
      </header>

      <div
        className="bg-muted flex w-fit gap-0.5 rounded-lg p-0.5"
        role="tablist"
        aria-label="뷰 전환"
      >
        {(["timeline", "map"] as View[]).map((v) => (
          <button
            key={v}
            type="button"
            role="tab"
            aria-selected={view === v}
            onClick={() => setView(v)}
            className={cn(
              "rounded-md px-3 py-1 text-sm transition-colors",
              view === v
                ? "bg-background shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            {v === "timeline" ? "타임라인" : "지도"}
          </button>
        ))}
      </div>

      {view === "timeline" ? (
        <FlowTimeline
          moments={flow.moments}
          onRemove={(momentId) => removeMutation.mutate(momentId)}
          onReorder={(momentIds) => reorderMutation.mutate(momentIds)}
        />
      ) : (
        <FlowMap moments={flow.moments} />
      )}

      <AddMomentsModal
        open={addOpen}
        onOpenChange={setAddOpen}
        flowId={flowId}
        existingIds={flow.moments.map((m) => m.id)}
      />
      <FlowEditModal open={editOpen} onOpenChange={setEditOpen} flow={flow} />
      <Modal
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="흐름 삭제"
        description="흐름을 삭제합니다. 담겼던 기록 자체는 남습니다."
        size="sm"
      >
        <Modal.Footer
          destructive
          submitLabel="삭제"
          pending={deleteMutation.isPending}
          pendingLabel="삭제 중..."
          onSubmit={() =>
            deleteMutation.mutate(flowId, {
              onSuccess: () => navigate("/lifelog/flows"),
            })
          }
        />
      </Modal>
    </div>
  );
}
