import type { Editor } from "@tiptap/react";

import { uploadNoteImage } from "../api/images";

/**
 * 이미지를 즉시 미리보기로 삽입하고, 백그라운드 업로드 완료 후 실제 URL로 교체한다.
 *
 * 1) 로컬 blob URL을 만들어 바로 <img>로 삽입 → 업로드를 기다리지 않고 즉시 보인다.
 * 2) presigned URL로 업로드 → 완료되면 해당 노드의 src를 공개 URL로 교체.
 * 3) blob URL은 해제.
 *
 * 업로드 실패 시 임시 이미지 노드를 제거한다.
 */
export async function uploadAndInsertImage(
  editor: Editor,
  file: File,
): Promise<void> {
  if (!file.type.startsWith("image/")) return;

  const blobUrl = URL.createObjectURL(file);
  editor.chain().focus().setImage({ src: blobUrl }).run();

  try {
    const url = await uploadNoteImage(file);
    replaceImageSrc(editor, blobUrl, url);
  } catch {
    removeImageBySrc(editor, blobUrl);
  } finally {
    URL.revokeObjectURL(blobUrl);
  }
}

/** 문서에서 src가 일치하는 image 노드를 찾아 새 src로 교체한다. */
function replaceImageSrc(editor: Editor, fromSrc: string, toSrc: string): void {
  const { state, view } = editor;
  state.doc.descendants((node, pos) => {
    if (node.type.name === "image" && node.attrs.src === fromSrc) {
      const tr = view.state.tr.setNodeMarkup(pos, undefined, {
        ...node.attrs,
        src: toSrc,
      });
      view.dispatch(tr);
      return false;
    }
    return true;
  });
}

/** 업로드 실패 시 임시 blob src 이미지 노드를 제거한다. */
function removeImageBySrc(editor: Editor, src: string): void {
  const { state, view } = editor;
  state.doc.descendants((node, pos) => {
    if (node.type.name === "image" && node.attrs.src === src) {
      const tr = view.state.tr.delete(pos, pos + node.nodeSize);
      view.dispatch(tr);
      return false;
    }
    return true;
  });
}

/** 클립보드/드롭 데이터에서 이미지 파일들을 추출한다. */
export function extractImageFiles(dataTransfer: DataTransfer | null): File[] {
  if (!dataTransfer) return [];
  return Array.from(dataTransfer.files).filter((f) =>
    f.type.startsWith("image/"),
  );
}
