import { Download } from "lucide-react";
import { QRCodeCanvas } from "qrcode.react";
import { useRef } from "react";

import { Button } from "@/components/ui/button";
import { toast } from "@/shared/lib/toast";

interface QrPanelProps {
  /** QR에 담을 문자열. 서버가 준 `qrPayload`(=짧은 주소)를 그대로 쓴다. */
  value: string;
  /** 저장 파일명에 쓸 슬러그. */
  slug: string;
  size?: number;
  /** 저장 버튼 라벨. 없으면 버튼을 그리지 않는다(상세 화면은 자체 라벨을 쓴다). */
  saveLabel?: string;
}

/**
 * QR 코드 + PNG 저장.
 *
 * <p><b>QR은 프론트에서만 만든다</b> — 서버는 QR을 모른다(아키텍처 §7). 짧은 주소 문자열만
 * 있으면 그릴 수 있는 것을 굳이 이미지로 내려받을 이유가 없다.
 *
 * <p>canvas로 그려 두는 이유는 저장 때문이다. SVG로 그리면 PNG로 만들 때 직렬화 →
 * 이미지 로드 → 다시 canvas를 거쳐야 한다.
 */
export function QrPanel({ value, slug, size = 168, saveLabel }: QrPanelProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);

  const save = () => {
    const canvas = wrapperRef.current?.querySelector("canvas");
    if (!canvas) {
      return;
    }
    canvas.toBlob((blob) => {
      if (!blob) {
        toast("QR을 저장하지 못했어요.", "error");
        return;
      }
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${slug}.png`;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  };

  return (
    <div className="flex flex-col items-center gap-2.5">
      {/*
        배경을 흰색으로 고정한다. 다크 모드에서 어두운 면 위에 어두운 QR을 그리면
        카메라가 못 읽는다 — 대비가 QR의 동작 조건이다.
      */}
      <div
        ref={wrapperRef}
        className="ring-border grid place-items-center rounded-xl bg-white p-2 ring-1"
      >
        <QRCodeCanvas value={value} size={size} level="M" marginSize={0} />
      </div>
      {saveLabel && (
        <Button type="button" variant="outline" size="sm" onClick={save}>
          <Download className="size-3.5" />
          {saveLabel}
        </Button>
      )}
    </div>
  );
}
