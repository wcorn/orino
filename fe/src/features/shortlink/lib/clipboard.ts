/**
 * 짧은 주소를 클립보드에 넣는다.
 *
 * <p><b>여기에 들어오는 값은 반드시 서버가 준 {@code shortUrl}이어야 한다.</b> 낙관적으로
 * 만들어 낸 값으로 복사하면 사용자는 <b>존재하지 않는 주소를 붙여넣는다</b> — 그리고 그 사실을
 * 상대가 링크를 눌러 보고 나서야 알게 된다(명세 §4.1).
 *
 * @returns 복사 성공 여부. 권한이 없거나 보안 컨텍스트가 아니면 false
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // http로 열었거나(보안 컨텍스트 아님) 권한이 없는 경우. 화면은 실패를 알리고
    // 주소는 그대로 보여 준다 — 발급 자체는 이미 끝났다.
    return false;
  }
}
