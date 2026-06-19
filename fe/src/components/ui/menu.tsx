import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import type { ComponentProps, ReactElement, ReactNode } from "react";

import { cn } from "@/lib/utils";

interface MenuProps {
  /** 트리거로 렌더할 요소(보통 <Button size="icon-sm">). render에 전달되므로 단일 element여야 한다. */
  trigger: ReactElement;
  children: ReactNode;
}

/** 표준 드롭다운 메뉴: Root + Trigger + Portal/Positioner/Popup을 캡슐화한다. */
function Menu({ trigger, children }: MenuProps) {
  return (
    <MenuPrimitive.Root>
      <MenuPrimitive.Trigger render={trigger} />
      <MenuPrimitive.Portal>
        <MenuPrimitive.Positioner sideOffset={4} align="end" className="z-50">
          <MenuPrimitive.Popup className="bg-popover text-popover-foreground min-w-32 rounded-md border p-1 shadow-md">
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

export { Menu, MenuItem };
