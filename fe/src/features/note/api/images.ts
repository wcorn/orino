import axios from "axios";

import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

interface UploadUrlResponse {
  uploadUrl: string;
  publicUrl: string;
}

/**
 * 이미지를 MinIO에 업로드하고 공개 URL을 반환한다.
 *
 * 1) BE에 presigned PUT URL 요청 (인증 필요 → client 사용)
 * 2) presigned URL로 이미지 바이너리를 직접 PUT (BE 안 거침, 순수 axios로
 *    Authorization 헤더 없이 전송)
 * 3) 노트에 삽입할 공개 URL 반환
 */
export async function uploadNoteImage(file: File): Promise<string> {
  const { data } = await client.post<ApiEnvelope<UploadUrlResponse>>(
    "/planner/images/upload-url",
    { contentType: file.type },
  );
  const { uploadUrl, publicUrl } = data.data;

  await axios.put(uploadUrl, file, {
    headers: { "Content-Type": file.type },
  });

  return publicUrl;
}
