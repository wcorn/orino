export type Mood = "HAPPY" | "CALM" | "EXCITED" | "TIRED" | "SAD";

export interface MomentPhoto {
  id: number;
  url: string;
  thumbUrl: string | null;
  width: number | null;
  height: number | null;
  sortOrder: number;
}

export interface FlowRef {
  id: number;
  title: string;
}

export interface MomentCard {
  id: number;
  occurredAt: string;
  body: string | null;
  mood: Mood | null;
  lat: number | null;
  lng: number | null;
  placeName: string | null;
  tags: string[];
  photos: MomentPhoto[];
  flows: FlowRef[];
  createdAt: string;
}

export interface FeedResponse {
  items: MomentCard[];
  nextCursor: string | null;
}

/** 생성/수정 시 사진 한 장(사전 업로드된 MinIO object key + EXIF). */
export interface PhotoRequest {
  objectKey: string;
  thumbKey?: string | null;
  width?: number | null;
  height?: number | null;
  exifTakenAt?: string | null;
  exifLat?: number | null;
  exifLng?: number | null;
  sortOrder?: number;
}

export interface MomentWriteRequest {
  occurredAt?: string | null;
  body?: string | null;
  mood?: Mood | null;
  lat?: number | null;
  lng?: number | null;
  placeName?: string | null;
  tags?: string[];
  photos?: PhotoRequest[];
}

export interface FeedFilters {
  tag?: string;
}
