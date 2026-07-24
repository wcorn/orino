import { ImagePlus, Loader2, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

import { cn } from "@/lib/utils";

import type { PhotoRequest } from "../api/types";
import { uploadMomentPhoto } from "../lib/uploadPhoto";

interface PhotoItem {
  key: string;
  previewUrl: string;
  /** object URL이면 정리 대상. */
  ownsPreview: boolean;
  status: "uploading" | "done" | "error";
  request?: PhotoRequest;
}

export interface InitialPhoto {
  previewUrl: string;
  request: PhotoRequest;
}

interface PhotoUploaderProps {
  initial?: InitialPhoto[];
  /** 업로드 완료 사진 목록이 바뀔 때마다(순서=표시 순서) 호출. */
  onChange: (photos: PhotoRequest[]) => void;
  /** 진행 중 업로드 수가 바뀔 때 호출(제출 버튼 비활성 판단용). */
  onUploadingChange?: (count: number) => void;
}

/** 사진 다중 선택 → EXIF·썸네일 처리 + presigned 업로드. 완료분을 부모에 전달한다. */
export function PhotoUploader({
  initial,
  onChange,
  onUploadingChange,
}: PhotoUploaderProps) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [items, setItems] = useState<PhotoItem[]>(() =>
    (initial ?? []).map((p, i) => ({
      key: `initial-${i}`,
      previewUrl: p.previewUrl,
      ownsPreview: false,
      status: "done" as const,
      request: p.request,
    })),
  );

  // 완료분·진행 수를 부모에 알린다.
  useEffect(() => {
    onChange(
      items
        .filter((it) => it.status === "done" && it.request)
        .map((it, index) => ({
          ...(it.request as PhotoRequest),
          sortOrder: index,
        })),
    );
    onUploadingChange?.(items.filter((it) => it.status === "uploading").length);
    // onChange/onUploadingChange는 매 렌더 새 함수일 수 있어 items에만 반응한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items]);

  const setStatus = (key: string, patch: Partial<PhotoItem>) =>
    setItems((prev) =>
      prev.map((it) => (it.key === key ? { ...it, ...patch } : it)),
    );

  const handleFiles = (files: FileList | null) => {
    if (!files) return;
    Array.from(files).forEach((file, i) => {
      const key = `${Date.now()}-${i}-${file.name}`;
      const previewUrl = URL.createObjectURL(file);
      setItems((prev) => [
        ...prev,
        { key, previewUrl, ownsPreview: true, status: "uploading" },
      ]);
      uploadMomentPhoto(file)
        .then((request) => setStatus(key, { status: "done", request }))
        .catch(() => setStatus(key, { status: "error" }));
    });
    if (inputRef.current) inputRef.current.value = "";
  };

  const remove = (key: string) =>
    setItems((prev) => {
      const target = prev.find((it) => it.key === key);
      if (target?.ownsPreview) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((it) => it.key !== key);
    });

  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {items.map((it) => (
          <div
            key={it.key}
            className="border-border relative size-20 overflow-hidden rounded-md border"
          >
            <img
              src={it.previewUrl}
              alt=""
              className="size-full object-cover"
            />
            {it.status === "uploading" && (
              <div className="bg-background/60 absolute inset-0 flex items-center justify-center">
                <Loader2 className="text-primary size-5 animate-spin" />
              </div>
            )}
            {it.status === "error" && (
              <div className="bg-destructive/70 text-destructive-foreground absolute inset-0 flex items-center justify-center text-xs">
                실패
              </div>
            )}
            <button
              type="button"
              aria-label="사진 제거"
              onClick={() => remove(it.key)}
              className="bg-foreground/60 text-background absolute top-1 right-1 flex size-5 items-center justify-center rounded-full"
            >
              <X className="size-3" />
            </button>
          </div>
        ))}
        <label
          htmlFor={inputId}
          className={cn(
            "border-border text-muted-foreground hover:bg-muted flex size-20 cursor-pointer flex-col items-center justify-center gap-1 rounded-md border border-dashed text-xs",
          )}
        >
          <ImagePlus className="size-5" />
          사진 추가
        </label>
        <input
          id={inputId}
          ref={inputRef}
          type="file"
          accept="image/*"
          multiple
          className="sr-only"
          onChange={(e) => handleFiles(e.target.files)}
        />
      </div>
    </div>
  );
}
