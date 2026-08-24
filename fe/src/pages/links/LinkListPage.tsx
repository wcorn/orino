import { Plus, Search } from "lucide-react";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Modal } from "@/components/ui/modal";
import type {
  CreatedLink,
  CreateLinkRequest,
  LinkSummary,
} from "@/features/shortlink/api/shortlink";
import { LinkRow } from "@/features/shortlink/components/LinkRow";
import { NewLinkModal } from "@/features/shortlink/components/NewLinkModal";
import { QrPanel } from "@/features/shortlink/components/QrPanel";
import { QuickCreateBar } from "@/features/shortlink/components/QuickCreateBar";
import { useLinkMutations } from "@/features/shortlink/hooks/useLinkMutations";
import { useLinks } from "@/features/shortlink/hooks/useLinks";
import { useShortlinkSummary } from "@/features/shortlink/hooks/useShortlinkSummary";
import { cn } from "@/lib/utils";
import { useIsNarrow } from "@/shared/lib/useIsNarrow";

type StatusFilter = "ALL" | "ACTIVE" | "INACTIVE";

const STATUS_CHIPS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "ACTIVE", label: "활성" },
  { value: "INACTIVE", label: "꺼짐·만료" },
];

/**
 * 링크 목록 `/links`.
 *
 * <p>화면의 중심은 <b>맨 위 입력칸 하나</b>다. URL을 붙여넣고 Enter를 누르면 발급 → 클립보드
 * 복사 → 토스트로 끝난다(명세 §4.1). 모달은 메모·태그·만료를 함께 넣을 때의 <b>옵션</b>이지
 * 기본 경로가 아니다 — 발급이 3초를 넘으면 사람은 이 기능을 안 쓴다.
 *
 * <p>태그·즐겨찾기 필터는 사이드바가 넘겨 주는 <b>쿼리 파라미터</b>로 받는다. 별도 상태를
 * 두면 새로고침·뒤로가기에서 사이드바와 목록이 어긋난다.
 */
export function LinkListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tag = searchParams.get("tag") ?? undefined;
  const favoriteOnly = searchParams.get("favorite") === "1";

  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [modalOpen, setModalOpen] = useState(false);
  const [created, setCreated] = useState<CreatedLink | null>(null);
  const [qrTarget, setQrTarget] = useState<LinkSummary | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<LinkSummary | null>(null);

  const { data, isPending, isError } = useLinks({ query, status, tag });
  const { data: summary } = useShortlinkSummary();
  // 모바일 발급 빈도가 이 모듈의 성패다(명세 §5.4). 좁은 화면에서는 목록을 줄이고
  // 빠른 발급 바를 제목 바로 아래에 그대로 둔다(화면 설계 §6).
  const narrow = useIsNarrow();
  const { create, toggle, favorite, remove } = useLinkMutations();

  const submitCreate = (body: CreateLinkRequest, fromModal: boolean) => {
    create.mutate(body, {
      onSuccess: (link) => {
        if (fromModal) {
          setCreated(link);
        }
      },
    });
  };

  const closeModal = (open: boolean) => {
    setModalOpen(open);
    if (!open) {
      setCreated(null);
    }
  };

  const favorites = data?.favorites ?? [];
  // 사이드바의 「즐겨찾기」는 별도 화면이 아니라 이 목록의 필터다 — 최근 발급 섹션만 접는다.
  const recent = favoriteOnly ? [] : (data?.recent ?? []);
  const empty = favorites.length === 0 && recent.length === 0;

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-5">
      <PageHeader
        // 좁은 화면에서는 제목을 28px로 줄이고, 설명 자리에 실제 숫자를 둔다.
        title={narrow ? <span className="text-[28px]">링크</span> : "링크"}
        description={
          narrow
            ? summary &&
              `${summary.total}개 · 이번 주 방문 ${summary.visitsThisWeek}`
            : "s.orino.dev · 자동 발급 5자, 최종 17자"
        }
        actions={
          // 좁은 화면에서는 빠른 발급 바가 바로 아래 있다 — 같은 일을 하는 버튼을 겹쳐 두지 않는다.
          narrow ? undefined : (
            <Button type="button" onClick={() => setModalOpen(true)}>
              <Plus className="size-4" />새 링크
            </Button>
          )
        }
      />

      <QuickCreateBar
        compact={narrow}
        pending={create.isPending}
        onCreate={(targetUrl) => submitCreate({ targetUrl }, false)}
      />

      {/* 칩은 접지 않고 가로로 흘린다 — 좁은 화면에서 줄바꿈되면 목록이 아래로 밀린다. */}
      <div className="flex items-center gap-2 overflow-x-auto md:flex-wrap md:overflow-visible">
        <div className="relative min-w-[200px] flex-1">
          <Search className="text-muted-foreground pointer-events-none absolute top-2.5 left-2.5 size-3.5" />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            aria-label="링크 검색"
            placeholder="슬러그·목적지·메모 검색"
            className="pl-7"
          />
        </div>
        {STATUS_CHIPS.map((chip) => (
          <button
            key={chip.value}
            type="button"
            aria-pressed={status === chip.value}
            onClick={() => setStatus(chip.value)}
            className={cn(
              "h-8 shrink-0 rounded-lg px-3 text-[13px] transition-colors",
              status === chip.value
                ? "bg-secondary text-secondary-foreground"
                : "border-border text-muted-foreground hover:bg-muted border",
            )}
          >
            {chip.label}
            <span className="ml-1 tabular-nums opacity-70">
              {countFor(chip.value, data?.counts)}
            </span>
          </button>
        ))}
      </div>

      {tag && (
        <div className="text-muted-foreground flex items-center gap-2 text-[13px]">
          태그 <span className="text-foreground font-medium">{tag}</span> 로
          좁혀 보는 중
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={() => {
              searchParams.delete("tag");
              setSearchParams(searchParams);
            }}
          >
            해제
          </Button>
        </div>
      )}

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">링크를 불러오지 못했어요.</Alert>
      )}

      {!isPending && !isError && empty && (
        <EmptyState>
          <p className="text-muted-foreground text-sm">
            아직 만든 링크가 없어요.
          </p>
          <Button type="button" onClick={() => setModalOpen(true)}>
            <Plus className="size-4" />새 링크
          </Button>
        </EmptyState>
      )}

      {favorites.length > 0 && (
        <Section title="즐겨찾기">
          {favorites.map((link) => (
            <LinkRow
              key={link.slug}
              link={link}
              compact={narrow}
              onShowQr={setQrTarget}
              onToggle={(target) => toggle.mutate(target.slug)}
              onFavorite={(target) => favorite.mutate(target.slug)}
              onDelete={setDeleteTarget}
            />
          ))}
        </Section>
      )}

      {recent.length > 0 && (
        <Section title="최근 발급">
          {recent.map((link) => (
            <LinkRow
              key={link.slug}
              link={link}
              compact={narrow}
              onShowQr={setQrTarget}
              onToggle={(target) => toggle.mutate(target.slug)}
              onFavorite={(target) => favorite.mutate(target.slug)}
              onDelete={setDeleteTarget}
            />
          ))}
        </Section>
      )}

      <NewLinkModal
        open={modalOpen}
        onOpenChange={closeModal}
        onCreate={(body) => submitCreate(body, true)}
        pending={create.isPending}
        created={created}
        baseUrl={summary?.baseUrl}
      />

      <Modal
        open={qrTarget !== null}
        onOpenChange={(open) => !open && setQrTarget(null)}
        title="QR"
        description={qrTarget?.shortUrl}
        size="sm"
      >
        {qrTarget && (
          <div className="mt-4 flex justify-center">
            <QrPanel
              value={qrTarget.shortUrl}
              slug={qrTarget.slug}
              saveLabel="PNG 저장"
            />
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="이 링크를 지울까요?"
        // 슬러그 영구 점유(명세 §3.1)는 되돌릴 수 없다. 지우기 전에 그 사실을 알려 준다.
        description="지운 주소는 다시 만들 수 없어요. 이미 뿌린 링크는 열리지 않게 됩니다."
        confirmLabel="삭제"
        destructive
        pending={remove.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            remove.mutate(deleteTarget.slug);
            setDeleteTarget(null);
          }
        }}
      />
    </div>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-caption text-muted-foreground font-semibold">
        {title}
      </h2>
      {children}
    </section>
  );
}

function countFor(
  value: StatusFilter,
  counts: { all: number; active: number; inactive: number } | undefined,
): number {
  if (!counts) {
    return 0;
  }
  return value === "ALL"
    ? counts.all
    : value === "ACTIVE"
      ? counts.active
      : counts.inactive;
}
