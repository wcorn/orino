import { LogOut, Moon, Sun } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useThemeStore } from "@/shared/lib/theme";

import { useAuth } from "../app/providers";
import { logout } from "../features/auth/api/auth";

export function MainPage() {
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
    <div className="min-h-svh">
      <header className="flex items-center justify-between border-b px-6 py-3">
        <span className="text-base font-semibold">orino</span>
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
      <main className="p-6" />
    </div>
  );
}
