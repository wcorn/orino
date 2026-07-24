export interface ProcessedImage {
  /** 썸네일(JPEG) 바이너리. */
  thumbBlob: Blob;
  /** 원본 픽셀 크기. */
  width: number;
  height: number;
}

/** 최대 변을 max로 맞춘 축소 크기(확대는 안 함). 순수 계산이라 단위 테스트 대상. */
export function computeThumbSize(
  width: number,
  height: number,
  max: number,
): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= max) {
    return { width, height };
  }
  const scale = max / longest;
  return {
    width: Math.round(width * scale),
    height: Math.round(height * scale),
  };
}

/**
 * 원본에서 썸네일(JPEG)을 만든다. 원본 크기도 함께 반환한다. 브라우저 canvas를 쓴다
 * (테스트 환경(jsdom)에선 동작하지 않으므로 통합 테스트는 사진 없는 경로로 검증한다).
 */
export async function processImage(
  file: File,
  max = 480,
): Promise<ProcessedImage> {
  const bitmap = await createImageBitmap(file);
  const { width, height } = bitmap;
  const thumb = computeThumbSize(width, height, max);

  const canvas = document.createElement("canvas");
  canvas.width = thumb.width;
  canvas.height = thumb.height;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    bitmap.close();
    throw new Error("canvas 2d context를 만들 수 없습니다.");
  }
  ctx.drawImage(bitmap, 0, 0, thumb.width, thumb.height);
  bitmap.close();

  const thumbBlob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("썸네일 생성 실패"))),
      "image/jpeg",
      0.8,
    );
  });
  return { thumbBlob, width, height };
}
