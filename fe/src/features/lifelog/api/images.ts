import axios from "axios";

import { client } from "@/shared/api";

export type ImageKind = "ORIGINAL" | "THUMB";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  publicUrl: string;
  objectKey: string;
}

/** 일상기록 사진용 presigned PUT URL을 발급받는다(원본/썸네일). */
export async function createUploadUrl(
  contentType: string,
  kind: ImageKind,
): Promise<UploadUrlResponse> {
  const { data } = await client.post<ApiEnvelope<UploadUrlResponse>>(
    "/lifelog/images/upload-url",
    { contentType, kind },
  );
  return data.data;
}

/**
 * presigned URL로 바이너리를 MinIO에 직접 PUT 한다(BE 미경유). 인증 헤더 없이 순수 axios로 보낸다.
 */
export async function putToPresigned(
  uploadUrl: string,
  body: Blob,
  contentType: string,
): Promise<void> {
  await axios.put(uploadUrl, body, {
    headers: { "Content-Type": contentType },
  });
}
