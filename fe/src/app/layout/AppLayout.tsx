import { LogOut, Menu, Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

import { Logo } from "@/components/brand/Logo";
import { Toaster } from "@/components/Toaster";
import { Button } from "@/components/ui/button";
import { logout } from "@/features/auth/api/auth";
import { useThemeStore } from "@/shared/lib/theme";

import { useAuth } from "../providers";
import { Sidebar } from "./Sidebar";

export function AppLayout() {
  const navigate = useNavigate();
  const { refresh } = useAuth();
  const { theme, setTheme } = useThemeStore();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { pathname } = useLocation();

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [pathname]);

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
    <div className="flex min-h-svh flex-col">
      <header className="flex items-center justify-between border-b px-6 py-3">
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon-sm"
            className="md:hidden"
            aria-label="메뉴 열기"
            onClick={() => setMobileMenuOpen(true)}
          >
            <Menu className="size-4" />
          </Button>
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
      <div className="flex flex-1">
        <Sidebar
          open={mobileMenuOpen}
          onClose={() => setMobileMenuOpen(false)}
        />
        {/* 모바일은 폭이 곧 가독성이라 페이지 여백을 줄인다(24→16). 노트처럼 안쪽에
          카드·본문 패딩이 더 쌓이는 화면은 이 여백이 겹쳐 본문이 크게 좁아진다. */}
        <main className="min-w-0 flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
      <Toaster />
    </div>
  );
}
