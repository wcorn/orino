import axios from "axios";

import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export type ImageKind = "ORIGINAL" | "THUMB";

/** 서버가 URL로 조립해 내려준다 — 화면이 호스트를 알 필요가 없다. */
export interface ActivityPhoto {
  id: number;
  url: string;
  /** 썸네일만 실패할 수 있다. null이면 원본을 줄여 쓴다. */
  thumbUrl: string | null;
  width: number | null;
  height: number | null;
}

export interface PhotoUploadUrl {
  uploadUrl: string;
  publicUrl: string;
  objectKey: string;
}

export async function createPhotoUploadUrl(
  activityId: number,
  kind: ImageKind,
): Promise<PhotoUploadUrl> {
  const { data } = await client.post<ApiEnvelope<PhotoUploadUrl>>(
    `/travel/activities/${activityId}/photos/upload-url`,
    // 항상 JPEG이다 — canvas 재인코딩으로 EXIF를 떨군 결과가 JPEG이다.
    { contentType: "image/jpeg", kind },
  );
  return data.data;
}

/**
 * presigned URL로 바이너리를 MinIO에 직접 PUT 한다.
 *
 * <p>인증 헤더 없이 순수 axios로 보낸다 — 우리 API의 Authorization 헤더가 붙으면 S3 서명
 * 검증이 깨진다.
 */
export async function putToPresigned(
  uploadUrl: string,
  body: Blob,
): Promise<void> {
  await axios.put(uploadUrl, body, {
    headers: { "Content-Type": "image/jpeg" },
  });
}

export interface PhotoMeta {
  objectKey: string;
  thumbKey: string | null;
  width: number;
  height: number;
}

/** 업로드가 끝난 사진만 등록한다. 실패한 장은 화면이 재시도 목록에 남긴다. */
export async function registerPhotos(
  activityId: number,
  photos: PhotoMeta[],
): Promise<ActivityPhoto[]> {
  const { data } = await client.post<ApiEnvelope<ActivityPhoto[]>>(
    `/travel/activities/${activityId}/photos`,
    { photos },
  );
  return data.data;
}

export async function deletePhoto(photoId: number): Promise<void> {
  await client.delete(`/travel/photos/${photoId}`);
}
