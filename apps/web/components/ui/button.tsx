import * as React from "react";
import { cn } from "@/lib/utils";

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost";
};

export function Button({ className, variant = "primary", ...props }: ButtonProps) {
  return (
    <button
      className={cn(
        "inline-flex h-11 items-center justify-center gap-2 whitespace-nowrap rounded-xl px-4 text-sm font-semibold leading-none transition duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:pointer-events-none disabled:opacity-50",
        variant === "primary" &&
          "bg-gradient-to-r from-[#FF6B00] to-[#FDBA74] text-primary-foreground shadow-[0_14px_34px_rgba(255,107,0,0.24)] hover:-translate-y-0.5 hover:shadow-[0_18px_44px_rgba(255,107,0,0.32)] active:translate-y-0",
        variant === "secondary" &&
          "border border-[#1F2937] bg-[#111827] text-foreground shadow-sm backdrop-blur hover:-translate-y-0.5 hover:border-[#00E5FF]/30 hover:bg-[#0F172A] hover:shadow-md active:translate-y-0",
        variant === "ghost" && "text-foreground hover:bg-[#0F172A] active:bg-[#111827]",
        className
      )}
      {...props}
    />
  );
}
