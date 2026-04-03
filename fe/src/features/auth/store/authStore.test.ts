import { describe, expect, it, beforeEach } from "vitest";
import { useAuthStore, getAccessToken, setAccessToken } from "./authStore";

describe("authStore", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("초기 상태는 null", () => {
    expect(getAccessToken()).toBeNull();
  });

  it("setAccessToken으로 토큰 저장", () => {
    setAccessToken("test-token");
    expect(getAccessToken()).toBe("test-token");
  });

  it("setAccessToken(null)로 토큰 제거", () => {
    setAccessToken("test-token");
    setAccessToken(null);
    expect(getAccessToken()).toBeNull();
  });
});
