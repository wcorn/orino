import { LogOut, Menu, Moon, Sun } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Logo } from "@/components/brand/Logo";
import { Button } from "@/components/ui/button";
import { logout } from "@/features/auth/api/auth";
import { useThemeStore } from "@/shared/lib/theme";

import { useAuth } from "../providers";

interface AppHeaderProps {
  /**
   * 모바일 사이드바 열기. 사이드바가 없는 화면(`/select`)에서는 넘기지 않으며,
   * 그러면 메뉴 버튼도 그리지 않는다.
   */
  onOpenMenu?: () => void;
}

/**
 * 앱 상단 헤더 — 로고·테마 토글·로그아웃.
 * 사이드바 없는 `/select`도 같은 헤더를 쓰므로 레이아웃에서 분리해 둔다.
 */
export function AppHeader({ onOpenMenu }: AppHeaderProps) {
  const navigate = useNavigate();
  const { refresh } = useAuth();
  const { theme, setTheme } = useThemeStore();

  const handleLogout = async () => {
    await logout();
    refresh();
    navigate("/", { replace: true });
  };

  const toggleTheme = () => {
    const resolved =
      theme === "system"
        ? window.matchMedia("(prefers-color-scheme: dark)").matches
          ? "dark"
          : "light"
        : theme;
    setTheme(resolved === "dark" ? "light" : "dark");
  };

  return (
    <header className="flex items-center justify-between border-b px-6 py-3">
      <div className="flex items-center gap-2">
        {onOpenMenu && (
          <Button
            variant="ghost"
            size="icon-sm"
            className="md:hidden"
            aria-label="메뉴 열기"
            onClick={onOpenMenu}
          >
            <Menu className="size-4" />
          </Button>
        )}
        <Logo size={22} />
      </div>
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon-sm" onClick={toggleTheme}>
          <Sun className="size-4 scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
          <Moon className="absolute size-4 scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
        </Button>
        <Button variant="ghost" size="sm" onClick={handleLogout}>
          <LogOut className="size-3.5" />
          로그아웃
        </Button>
      </div>
    </header>
  );
}
