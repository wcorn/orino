import { Extension } from "@tiptap/core";
import type { LinkOptions } from "@tiptap/extension-link";

/**
 * 사용자가 입력한 링크 문자열을 안전한 href로 정규화한다.
 * - 위험 스킴(javascript:/data:/vbscript:)은 거부(null).
 * - 스킴이 있으면 그대로(mailto:·tel: 등 포함).
 * - 이메일처럼 보이면 mailto: 프리픽스.
 * - 그 외에는 https:// 프리픽스(Notion처럼 "example.com"만 쳐도 링크가 되게).
 * 반환 null = 유효하지 않음(링크 걸지 않음/해제).
 */
export function normalizeUrl(input: string): string | null {
  const raw = input.trim();
  if (!raw) return null;
  if (/^(javascript|data|vbscript):/i.test(raw)) return null;
  if (/^[a-z][a-z0-9+.-]*:/i.test(raw)) return raw; // 이미 스킴 있음
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(raw)) return `mailto:${raw}`;
  return `https://${raw}`;
}

/** StarterKit에 포함된 Link 확장 옵션. 클릭으로 바로 열지 않고(버블메뉴로 열기·편집·해제) 새 탭·nofollow. */
export const noteLinkOptions: Partial<LinkOptions> = {
  openOnClick: false,
  autolink: true,
  linkOnPaste: true,
  defaultProtocol: "https",
  HTMLAttributes: {
    class: "note-link",
    rel: "noopener noreferrer nofollow",
    target: "_blank",
  },
};

export interface LinkShortcutOptions {
  /** Cmd/Ctrl+K가 눌렸을 때 호출(링크 편집 UI 열기). */
  onTrigger: () => void;
}

/** Cmd/Ctrl+K로 링크 편집 UI를 여는 단축키만 담당하는 확장. UI는 React(LinkMenu)가 처리한다. */
export const LinkShortcut = Extension.create<LinkShortcutOptions>({
  name: "linkShortcut",
  addOptions() {
    return { onTrigger: () => {} };
  },
  addKeyboardShortcuts() {
    return {
      "Mod-k": () => {
        this.options.onTrigger();
        return true;
      },
    };
  },
});
