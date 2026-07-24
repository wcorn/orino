import type { MomentPhoto, PhotoRequest } from "../api/types";

/**
 * 공개 URL에서 MinIO object key를 되돌린다. key는 항상 {@code lifelog/}로 시작하므로 URL에서 그
 * 지점부터 잘라낸다. (수정 시 기존 사진을 전체치환 PUT에 다시 실어야 하는데 카드 응답엔 url만 있어
 * key를 유도한다 — BE가 objectKey를 응답에 넣으면 이 유도는 걷어낸다.)
 */
export function keyFromUrl(url: string | null): string | null {
  if (!url) return null;
  const i = url.indexOf("lifelog/");
  return i >= 0 ? url.slice(i) : null;
}

export function photoToRequest(photo: MomentPhoto): PhotoRequest {
  return {
    objectKey: keyFromUrl(photo.url) ?? "",
    thumbKey: keyFromUrl(photo.thumbUrl),
    width: photo.width,
    height: photo.height,
    sortOrder: photo.sortOrder,
  };
}
