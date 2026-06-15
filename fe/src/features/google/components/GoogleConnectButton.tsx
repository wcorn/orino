import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";

import { useGoogleConnect } from "../hooks/useGoogleConnect";

interface GoogleConnectButtonProps {
  variant?: "default" | "outline";
  size?: "default" | "sm";
  className?: string;
  children?: ReactNode;
}

/** Google 동의 화면으로 이동시키는 연결 버튼. */
export function GoogleConnectButton({
  variant = "default",
  size = "default",
  className,
  children,
}: GoogleConnectButtonProps) {
  const connect = useGoogleConnect();

  return (
    <Button
      variant={variant}
      size={size}
      className={className}
      disabled={connect.isPending}
      onClick={() => connect.mutate()}
    >
      {connect.isPending ? "연결 중…" : (children ?? "Google 연결")}
    </Button>
  );
}
