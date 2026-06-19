import { faro } from "@grafana/faro-react";
import { Component, type ErrorInfo, type ReactNode } from "react";

import { Button } from "@/components/ui/button";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    faro?.api?.pushError(error, {
      context: { componentStack: info.componentStack ?? "" },
    });
    console.error("ErrorBoundary caught:", error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <div className="flex min-h-svh flex-col items-center justify-center gap-4">
            <p className="text-muted-foreground text-sm">
              문제가 발생했습니다.
            </p>
            <Button variant="link" onClick={() => window.location.reload()}>
              새로고침
            </Button>
          </div>
        )
      );
    }
    return this.props.children;
  }
}
