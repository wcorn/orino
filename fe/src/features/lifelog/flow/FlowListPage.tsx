import { ArrowLeft, Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { Button, buttonVariants } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";

import type { FlowStatus } from "../api/flows";
import { useFlows } from "../hooks/useFlows";
import { FlowCard } from "./FlowCard";
import { FlowCreateModal } from "./FlowCreateModal";

const FILTERS: { label: string; value: FlowStatus | undefined }[] = [
  { label: "진행중", value: "ACTIVE" },
  { label: "보관", value: "ARCHIVED" },
  { label: "전체", value: undefined },
];

/** 흐름 목록 — 상태 필터 + 새 흐름 생성. */
export function FlowListPage() {
  const [status, setStatus] = useState<FlowStatus | undefined>("ACTIVE");
  const [createOpen, setCreateOpen] = useState(false);
  const { data: flows, isLoading } = useFlows(status);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Link
            to="/lifelog"
            aria-label="피드로"
            className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
          >
            <ArrowLeft />
          </Link>
          <h1 className="text-heading font-semibold">흐름</h1>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus />새 흐름
        </Button>
      </header>

      <div className="flex gap-1.5">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            onClick={() => setStatus(f.value)}
            className={cn(
              "rounded-full px-3 py-1 text-sm transition-colors",
              status === f.value
                ? "bg-primary/10 text-primary"
                : "text-foreground/70 hover:bg-muted",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <LoadingText />
      ) : (flows ?? []).length === 0 ? (
        <p className="text-muted-foreground py-16 text-center text-sm">
          아직 흐름이 없어요. 여행이나 하루를 하나로 엮어보세요.
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {(flows ?? []).map((flow) => (
            <FlowCard key={flow.id} flow={flow} />
          ))}
        </div>
      )}

      <FlowCreateModal open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}
