import { createUploadUrl, putToPresigned } from "../api/images";
import type { PhotoRequest } from "../api/types";
import { readExif } from "./exif";
import { processImage } from "./thumbnail";

/**
 * 사진 한 장을 처리해 moment 생성에 넣을 {@link PhotoRequest}로 만든다:
 * EXIF 추출 → 썸네일 생성 → 원본·썸네일을 presigned URL로 MinIO에 직접 업로드.
 */
export async function uploadMomentPhoto(file: File): Promise<PhotoRequest> {
  const [exif, processed] = await Promise.all([
    readExif(file),
    processImage(file),
  ]);

  const original = await createUploadUrl(file.type, "ORIGINAL");
  await putToPresigned(original.uploadUrl, file, file.type);

  const thumb = await createUploadUrl("image/jpeg", "THUMB");
  await putToPresigned(thumb.uploadUrl, processed.thumbBlob, "image/jpeg");

  return {
    objectKey: original.objectKey,
    thumbKey: thumb.objectKey,
    width: processed.width,
    height: processed.height,
    exifTakenAt: exif.takenAt ?? null,
    exifLat: exif.lat ?? null,
    exifLng: exif.lng ?? null,
  };
}
