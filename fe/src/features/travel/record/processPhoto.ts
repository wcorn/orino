/** 원본으로 올릴 최대 변(px). 폰 사진을 그대로 올리면 현지 회선에서 끝나지 않는다. */
const ORIGINAL_MAX = 2560;

/** 썸네일 최대 변(px). 그리드에서 한 변이 100px 남짓이라 이 정도면 충분하다. */
const THUMB_MAX = 480;

/** 장당 상한(§2.5). 이 크기를 넘는 파일은 고르는 순간 거른다. */
export const MAX_FILE_BYTES = 15 * 1024 * 1024;

export interface ProcessedPhoto {
  /** 재인코딩한 원본(JPEG). EXIF가 떨어진 상태다. */
  originalBlob: Blob;
  thumbBlob: Blob;
  /** 재인코딩 후 크기. DB에 적히는 값이라 실제 올라간 것과 같아야 한다. */
  width: number;
  height: number;
}

/** 최대 변을 max로 맞춘 축소 크기(확대는 하지 않는다). 순수 계산이라 단위 테스트 대상. */
export function fitWithin(
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
 * 사진 한 장을 올릴 수 있는 형태로 만든다 — 원본 재인코딩 + 썸네일.
 *
 * <p><b>원본도 canvas로 다시 그린다.</b> 그래야 EXIF가 떨어진다(§1.6 — 여행 사진의 위치정보를
 * 쓰지 않으므로 애초에 서버로 보내지 않는다). 파일을 그대로 올리면 촬영 위치가 따라간다.
 *
 * <p>EXIF orientation은 <b>버리기 전에 적용</b>한다. 회전 플래그만 떨구면 아이폰 세로 사진이
 * 눕는다 — 정보를 지우는 것과 그림을 망치는 것은 다르다.
 */
export async function processPhoto(file: File): Promise<ProcessedPhoto> {
  const bitmap = await createImageBitmap(file, {
    imageOrientation: "from-image",
  });
  try {
    const original = fitWithin(bitmap.width, bitmap.height, ORIGINAL_MAX);
    const thumb = fitWithin(bitmap.width, bitmap.height, THUMB_MAX);
    const [originalBlob, thumbBlob] = await Promise.all([
      toJpeg(bitmap, original.width, original.height, 0.85),
      toJpeg(bitmap, thumb.width, thumb.height, 0.8),
    ]);
    return { originalBlob, thumbBlob, ...original };
  } finally {
    bitmap.close();
  }
}

async function toJpeg(
  bitmap: ImageBitmap,
  width: number,
  height: number,
  quality: number,
): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("canvas 2d context를 만들 수 없습니다.");
  }
  ctx.drawImage(bitmap, 0, 0, width, height);
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("이미지 변환 실패"))),
      "image/jpeg",
      quality,
    );
  });
}
