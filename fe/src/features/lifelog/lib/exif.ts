import exifr from "exifr";

export interface PhotoExif {
  /** ISO-8601 촬영시각(EXIF DateTimeOriginal). */
  takenAt?: string;
  lat?: number;
  lng?: number;
}

/**
 * 사진 EXIF에서 촬영시각·GPS를 읽는다. EXIF가 없거나 파싱 실패해도 조용히 빈 값을 돌려준다
 * (위치·시각 자동채움은 편의 기능이라 실패가 업로드를 막지 않는다).
 */
export async function readExif(file: File): Promise<PhotoExif> {
  try {
    const parsed = await exifr.parse(file, {
      gps: true,
      pick: ["DateTimeOriginal", "CreateDate", "latitude", "longitude"],
    });
    if (!parsed) return {};
    const takenAtDate: Date | undefined =
      parsed.DateTimeOriginal ?? parsed.CreateDate;
    return {
      takenAt:
        takenAtDate instanceof Date && !Number.isNaN(takenAtDate.getTime())
          ? takenAtDate.toISOString()
          : undefined,
      lat: typeof parsed.latitude === "number" ? parsed.latitude : undefined,
      lng: typeof parsed.longitude === "number" ? parsed.longitude : undefined,
    };
  } catch {
    return {};
  }
}
