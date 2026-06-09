import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ABHAY Accounting OS",
  description: "AI-first Accounting Operating System by ANVRITAI"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}

