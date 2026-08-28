import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import type { ComponentProps, ReactElement, ReactNode } from "react";

import { cn } from "@/lib/utils";

interface MenuProps {
  /** 트리거로 렌더할 요소(보통 <Button size="icon-sm">). render에 전달되므로 단일 element여야 한다. */
  trigger: ReactElement;
  /**
   * 트리거 기준 정렬. 기본 `end`는 행 우측 끝의 `⋯` 버튼용이다 —
   * 사이드바 스위처처럼 트리거가 폭을 다 쓰는 경우에는 `start`로 맞춘다.
   */
  align?: "start" | "center" | "end";
  /** 팝업 추가 클래스. 폭을 트리거에 맞출 때 쓴다(기본은 `min-w-32`). */
  popupClassName?: string;
  children: ReactNode;
}

/** 표준 드롭다운 메뉴: Root + Trigger + Portal/Positioner/Popup을 캡슐화한다. */
function Menu({ trigger, align = "end", popupClassName, children }: MenuProps) {
  return (
    <MenuPrimitive.Root>
      <MenuPrimitive.Trigger render={trigger} />
      <MenuPrimitive.Portal>
        <MenuPrimitive.Positioner sideOffset={4} align={align} className="z-50">
          <MenuPrimitive.Popup
            className={cn(
              "bg-popover text-popover-foreground min-w-32 rounded-md border p-1 shadow-md",
              popupClassName,
            )}
          >
            {children}
          </MenuPrimitive.Popup>
        </MenuPrimitive.Positioner>
      </MenuPrimitive.Portal>
    </MenuPrimitive.Root>
  );
}

const ITEM_BASE =
  "flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none";
const ITEM_VARIANT = {
  default:
    "data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground",
  destructive: "text-destructive data-[highlighted]:bg-destructive/10",
};

type MenuItemProps = Omit<
  ComponentProps<typeof MenuPrimitive.Item>,
  "className"
> & {
  variant?: "default" | "destructive";
  className?: string;
};

function MenuItem({ variant = "default", className, ...props }: MenuItemProps) {
  return (
    <MenuPrimitive.Item
      className={cn(ITEM_BASE, ITEM_VARIANT[variant], className)}
      {...props}
    />
  );
}

/** 항목 묶음 사이의 구분선. 팝업 패딩(p-1)을 가로질러야 해서 음수 마진을 준다. */
function MenuSeparator({ className }: { className?: string }) {
  return (
    <MenuPrimitive.Separator
      className={cn("bg-border -mx-1 my-1 h-px", className)}
    />
  );
}

export { Menu, MenuItem, MenuSeparator };
