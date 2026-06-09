import * as React from "react";
import { cn } from "@/lib/utils";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost";
};

export function Button({ className, variant = "primary", ...props }: ButtonProps) {
  return (
    <button
      className={cn(
        "inline-flex h-10 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:pointer-events-none disabled:opacity-50",
        variant === "primary" &&
          "bg-gradient-to-r from-orange-500 via-orange-500 to-amber-400 text-primary-foreground shadow-[0_14px_34px_rgba(249,115,22,0.24)] hover:-translate-y-0.5 hover:shadow-[0_18px_44px_rgba(249,115,22,0.32)] active:translate-y-0",
        variant === "secondary" &&
          "border border-white/70 bg-white/75 text-foreground shadow-sm backdrop-blur hover:-translate-y-0.5 hover:bg-white hover:shadow-md active:translate-y-0",
        variant === "ghost" && "text-foreground hover:bg-orange-50/80 active:bg-orange-100/70",
        className
      )}
      {...props}
    />
  );
}
