import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { Toaster } from "@/components/Toaster";

import { AppHeader } from "./AppHeader";
import { Sidebar } from "./Sidebar";

export function AppLayout() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { pathname } = useLocation();

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [pathname]);

  return (
    <div className="flex min-h-svh flex-col">
      <AppHeader onOpenMenu={() => setMobileMenuOpen(true)} />
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
