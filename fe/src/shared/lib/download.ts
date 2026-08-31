/**
 * 받은 바이트를 파일로 저장시킨다.
 *
 * <p>브라우저에는 「저장」 API가 없어서 임시 링크를 만들어 누르는 것이 유일한 방법이다.
 * objectURL은 붙잡고 있으면 그대로 메모리에 남으므로 누른 직후 놓아준다.
 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/**
 * `Content-Disposition`에서 파일 이름을 꺼낸다.
 *
 * <p>한글 이름은 `filename*=UTF-8''...`(RFC 5987)로 오고 그쪽이 정본이다 — 옛 `filename=`은
 * ASCII 대체본이라 한글이 물음표로 뭉개져 있을 수 있으니 나중에 본다.
 */
export function fileNameFromDisposition(
  disposition: string | undefined | null,
): string | null {
  if (!disposition) return null;

  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      // 서버가 인코딩을 어긴 경우. ASCII 대체본으로 내려간다.
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : null;
}
