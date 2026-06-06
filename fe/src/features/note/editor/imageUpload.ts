import type { Editor } from "@tiptap/react";

import { uploadNoteImage } from "../api/images";

/**
 * 파일을 업로드하고 에디터에 이미지 노드를 삽입한다.
 * 업로드 중에는 임시 placeholder 없이 완료 후 삽입한다(단순화).
 */
export async function uploadAndInsertImage(
  editor: Editor,
  file: File,
): Promise<void> {
  if (!file.type.startsWith("image/")) return;
  try {
    const url = await uploadNoteImage(file);
    editor.chain().focus().setImage({ src: url }).run();
  } catch {
    // 업로드 실패는 조용히 무시 (토스트는 호출부에서 처리 가능)
  }
}

/** 클립보드/드롭 데이터에서 이미지 파일들을 추출한다. */
export function extractImageFiles(dataTransfer: DataTransfer | null): File[] {
  if (!dataTransfer) return [];
  return Array.from(dataTransfer.files).filter((f) =>
    f.type.startsWith("image/"),
  );
}
