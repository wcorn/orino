import { Check, Lock } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

import type { CreatedLink, CreateLinkRequest } from "../api/shortlink";
import { useSlugAvailability } from "../hooks/useSlugAvailability";
import { QrPanel } from "./QrPanel";
import { ShortUrlText } from "./ShortUrlText";

interface NewLinkModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreate: (body: CreateLinkRequest) => void;
  pending: boolean;
  /**
   * 발급이 끝난 링크. 있으면 같은 모달이 성공 화면으로 바뀐다 —
   * 새 창을 띄우지 않는 이유는 방금 만든 주소가 눈앞에서 이어져야 하기 때문이다.
   */
  created: CreatedLink | null;
  /** 서버가 알려 준 공개 base URL(`https://s.orino.dev`). 길이 미리보기에 쓴다. */
  baseUrl: string | undefined;
}

/** 자동 발급 슬러그 길이. 미리보기의 기본값이다(명세 §3). */
const AUTO_SLUG_LENGTH = 5;
const SLUG_PREVIEW = "ab3k9";

export function NewLinkModal({
  open,
  onOpenChange,
  onCreate,
  pending,
  created,
  baseUrl,
}: NewLinkModalProps) {
  const [targetUrl, setTargetUrl] = useState("");
  const [slug, setSlug] = useState("");
  const [memo, setMemo] = useState("");
  const [tags, setTags] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const { taken, invalid, checkedSlug } = useSlugAvailability(slug);

  const reset = () => {
    setTargetUrl("");
    setSlug("");
    setMemo("");
    setTags("");
    setExpiresAt("");
  };

  const close = (next: boolean) => {
    if (!next) {
      reset();
    }
    onOpenChange(next);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!targetUrl.trim() || taken || invalid) {
      return;
    }
    onCreate({
      targetUrl: targetUrl.trim(),
      slug: slug.trim() || undefined,
      memo: memo.trim() || undefined,
      tags: splitTags(tags),
      // 날짜만 받아 그날 끝으로 잡는다 — "12월 31일까지"는 그날 자정까지라는 뜻이다.
      expiresAt: expiresAt ? `${expiresAt}T23:59:59+09:00` : undefined,
    });
  };

  if (created) {
    return (
      <Modal
        open={open}
        onOpenChange={close}
        title="만들었어요"
        size="sm"
        className="max-w-[380px]"
      >
        <div className="mt-4 flex flex-col items-center gap-3">
          <span className="bg-success/14 text-success flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold">
            <Check className="size-3.5" />
            클립보드에 복사했어요
          </span>
          <ShortUrlText
            shortUrl={created.shortUrl}
            slug={created.slug}
            className="text-2xl tracking-[-0.02em]"
            slugClassName="font-bold"
          />
          <QrPanel
            value={created.qrPayload}
            slug={created.slug}
            saveLabel="QR 저장"
          />
          <p className="text-muted-foreground text-center text-xs">
            목적지는 나중에 바꿔도 이 주소는 그대로예요.
          </p>
        </div>
        <Modal.Footer>
          <Button type="button" className="flex-1" onClick={() => close(false)}>
            완료
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }

  const previewSlug = slug.trim() || SLUG_PREVIEW;
  const totalLength = baseUrl
    ? baseUrl.replace(/^https?:\/\//, "").length + 1 + previewSlug.length
    : null;

  return (
    <Modal
      open={open}
      onOpenChange={close}
      title="새 링크"
      description="붙여넣고 만들기. 나머지는 나중에 고쳐도 됩니다."
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-3.5">
        <FormField
          label={
            <span className="flex w-full items-center justify-between gap-2">
              목적지 URL
              {/* 명세 §3의 「17자 고정」을 화면이 계속 보여 주는 장치다. */}
              {totalLength !== null && (
                <span className="text-muted-foreground text-xs font-normal">
                  <ShortUrlText
                    shortUrl={`${baseUrl}/${previewSlug}`}
                    slug={previewSlug}
                  />
                  <span className="bg-muted ml-1.5 rounded-full px-1.5 py-0.5 tabular-nums">
                    {totalLength}자
                  </span>
                </span>
              )}
            </span>
          }
          htmlFor="targetUrl"
        >
          <Input
            id="targetUrl"
            value={targetUrl}
            onChange={(event) => setTargetUrl(event.target.value)}
            placeholder="https://…"
            autoFocus
          />
        </FormField>

        <FormField label="커스텀 슬러그 (선택)" htmlFor="slug">
          <Input
            id="slug"
            value={slug}
            onChange={(event) => setSlug(event.target.value)}
            placeholder={`비우면 ${AUTO_SLUG_LENGTH}자 자동`}
            aria-invalid={taken || invalid}
          />
          {taken && checkedSlug === slug.trim() && (
            <FieldError>이미 사용 중이에요</FieldError>
          )}
          {invalid && checkedSlug === slug.trim() && (
            <FieldError>사용할 수 없는 문자가 있어요</FieldError>
          )}
        </FormField>

        <FormField label="만료 (선택)" htmlFor="expiresAt">
          <Input
            id="expiresAt"
            type="date"
            value={expiresAt}
            onChange={(event) => setExpiresAt(event.target.value)}
          />
        </FormField>

        <FormField label="메모 (선택)" htmlFor="memo">
          <Textarea
            id="memo"
            rows={2}
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </FormField>

        <FormField label="태그 (선택)" htmlFor="tags">
          <Input
            id="tags"
            value={tags}
            onChange={(event) => setTags(event.target.value)}
            placeholder="쉼표로 구분 — 가족, 여행"
          />
        </FormField>

        {/*
          비밀번호는 켜는 순간 방문자가 확인 화면을 거쳐야 하는데, 그 화면이 아직 없다(#1244).
          지금 켤 수 있게 두면 사용자가 자기 링크를 조용히 못 열게 만든다 — 그래서 자리는
          두되 잠가 둔다.
        */}
        <div className="bg-muted flex items-center justify-between gap-3 rounded-lg px-3 py-2.5">
          <span className="text-muted-foreground flex items-center gap-2 text-[13px]">
            <Lock className="size-3.5" />
            비밀번호 보호 — 켜면 확인 화면을 한 번 거칩니다
          </span>
          <Switch checked={false} disabled onCheckedChange={() => {}} />
        </div>
        <p className="text-muted-foreground -mt-2 text-xs">
          확인 화면이 붙기 전까지는 켤 수 없어요.
        </p>

        <Modal.Footer
          submitLabel="만들기"
          pending={pending}
          pendingLabel="만드는 중..."
          submitDisabled={
            pending || targetUrl.trim() === "" || taken || invalid
          }
        />
      </form>
    </Modal>
  );
}

function splitTags(value: string): string[] | undefined {
  const tags = value
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
  return tags.length > 0 ? tags : undefined;
}
