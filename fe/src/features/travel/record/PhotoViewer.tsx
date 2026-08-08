import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import type { ActivityPhoto } from "@/features/travel/api/photos";

/** 이 픽셀 이상 끌어야 넘긴다. 세로 스크롤하다 손가락이 흔들린 것과 구분한다. */
const SWIPE_THRESHOLD = 50;

interface PhotoViewerProps {
  photos: ActivityPhoto[];
  startIndex: number;
  onClose: () => void;
}

/**
 * 전체화면 사진 뷰어(§S-07). 좌우로 넘긴다.
 *
 * <p>썸네일이 아니라 <b>원본</b>을 띄운다 — 크게 보려고 연 화면이다.
 */
export function PhotoViewer({ photos, startIndex, onClose }: PhotoViewerProps) {
  const [index, setIndex] = useState(startIndex);
  const touchStartX = useRef<number | null>(null);

  const clamp = (next: number) =>
    setIndex(Math.min(photos.length - 1, Math.max(0, next)));

  // 키보드로도 넘긴다 — 데스크톱에서 열어볼 수 있어야 한다.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowLeft") setIndex((i) => Math.max(0, i - 1));
      if (e.key === "ArrowRight")
        setIndex((i) => Math.min(photos.length - 1, i + 1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [photos.length, onClose]);

  if (photos.length === 0) return null;
  const photo = photos[Math.min(index, photos.length - 1)];

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-black/95"
      role="dialog"
      aria-modal="true"
      aria-label="사진 보기"
      onTouchStart={(e) => {
        touchStartX.current = e.touches[0].clientX;
      }}
      onTouchEnd={(e) => {
        const start = touchStartX.current;
        touchStartX.current = null;
        if (start === null) return;
        const dx = e.changedTouches[0].clientX - start;
        if (Math.abs(dx) < SWIPE_THRESHOLD) return;
        clamp(index + (dx < 0 ? 1 : -1));
      }}
    >
      <div className="flex items-center justify-between p-3 text-white">
        <span className="text-sm tabular-nums">
          {index + 1} / {photos.length}
        </span>
        <button type="button" aria-label="닫기" onClick={onClose}>
          <X className="size-5" />
        </button>
      </div>

      <div className="flex min-h-0 flex-1 items-center justify-center px-2">
        {/* 그리드의 썸네일과 달리 여기서는 사진이 내용 자체다 — 이름을 준다. */}
        <img
          src={photo.url}
          alt={`사진 ${index + 1}`}
          className="max-h-full max-w-full object-contain"
        />
      </div>

      <div className="flex items-center justify-between p-4 text-white">
        <button
          type="button"
          aria-label="이전 사진"
          disabled={index === 0}
          onClick={() => clamp(index - 1)}
          className="disabled:opacity-30"
        >
          <ChevronLeft className="size-7" />
        </button>
        <button
          type="button"
          aria-label="다음 사진"
          disabled={index === photos.length - 1}
          onClick={() => clamp(index + 1)}
          className="disabled:opacity-30"
        >
          <ChevronRight className="size-7" />
        </button>
      </div>
    </div>
  );
}
