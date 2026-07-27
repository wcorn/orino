import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import { describe, expect, it } from "vitest";

import { normalizeUrl, noteLinkOptions } from "./link";

describe("normalizeUrl", () => {
  it("빈 문자열·공백은 null", () => {
    expect(normalizeUrl("")).toBeNull();
    expect(normalizeUrl("   ")).toBeNull();
  });

  it("스킴이 없으면 https:// 를 붙인다(Notion처럼 도메인만 입력)", () => {
    expect(normalizeUrl("example.com")).toBe("https://example.com");
    expect(normalizeUrl("  example.com/path?q=1 ")).toBe(
      "https://example.com/path?q=1",
    );
  });

  it("이미 스킴이 있으면 그대로 둔다", () => {
    expect(normalizeUrl("http://a.com")).toBe("http://a.com");
    expect(normalizeUrl("https://a.com")).toBe("https://a.com");
    expect(normalizeUrl("mailto:a@b.com")).toBe("mailto:a@b.com");
    expect(normalizeUrl("tel:+123")).toBe("tel:+123");
  });

  it("이메일처럼 보이면 mailto: 를 붙인다", () => {
    expect(normalizeUrl("a@b.com")).toBe("mailto:a@b.com");
  });

  it("위험 스킴(javascript/data/vbscript)은 null", () => {
    expect(normalizeUrl("javascript:alert(1)")).toBeNull();
    expect(normalizeUrl("JavaScript:alert(1)")).toBeNull();
    expect(normalizeUrl("data:text/html,<script>")).toBeNull();
    expect(normalizeUrl("vbscript:msgbox")).toBeNull();
  });
});

describe("noteLinkOptions (StarterKit Link 구성)", () => {
  function makeEditor() {
    const element = document.createElement("div");
    document.body.appendChild(element);
    return new Editor({
      element,
      extensions: [StarterKit.configure({ link: noteLinkOptions })],
      content: "<p>hello world</p>",
    });
  }

  it("setLink가 note-link 클래스·target·rel을 붙인다", () => {
    const editor = makeEditor();
    editor
      .chain()
      .setTextSelection({ from: 1, to: 6 }) // "hello"
      .setLink({ href: "https://example.com" })
      .run();
    const html = editor.getHTML();
    expect(html).toContain('href="https://example.com"');
    expect(html).toContain('class="note-link"');
    expect(html).toContain('target="_blank"');
    expect(html).toContain("noopener");
    editor.destroy();
  });

  it("openOnClick=false — 링크 클릭이 새 창 열기를 켜지 않는다", () => {
    const editor = makeEditor();
    const link = editor.extensionManager.extensions.find(
      (e) => e.name === "link",
    );
    expect(link?.options.openOnClick).toBe(false);
    editor.destroy();
  });
});
