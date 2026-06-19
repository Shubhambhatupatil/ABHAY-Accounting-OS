import * as React from "react";
import { cn } from "@/lib/utils";

export function Input({ className, ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        "h-11 w-full rounded-xl border border-[#1F2937] bg-[#0F172A] px-3 text-sm leading-none text-foreground shadow-sm outline-none backdrop-blur transition placeholder:text-muted-foreground focus:border-[#FF6B00] focus:ring-2 focus:ring-[#FF6B00]/20",
        className
      )}
      {...props}
    />
  );
}
