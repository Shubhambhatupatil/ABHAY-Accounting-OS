import { Landmark } from "lucide-react";

export function AuthShell({ children, title, subtitle }: Readonly<{
  children: React.ReactNode;
  title: string;
  subtitle: string;
}>) {
  return (
    <main className="abhay-shell-bg min-h-screen">
      <section className="mx-auto grid min-h-screen w-full max-w-6xl grid-cols-1 gap-5 p-4 lg:grid-cols-[1fr_460px] lg:p-8">
        <div className="hero-grid flex min-h-[34vh] flex-col justify-between rounded-3xl p-6 text-white shadow-[0_24px_70px_rgba(15,23,42,0.18)] lg:min-h-[calc(100vh-4rem)] lg:p-10">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/20 text-white shadow-inner backdrop-blur">
              <Landmark aria-hidden="true" size={22} />
            </div>
            <div>
              <p className="text-sm font-semibold">ABHAY Accounting OS</p>
              <p className="text-xs text-white/70">by ANVRITAI</p>
            </div>
          </div>
          <div className="max-w-xl py-10">
            <span className="ai-badge mb-4 border-white/20 bg-white/10 text-white">AI Accounting Alpha</span>
            <h1 className="text-3xl font-semibold tracking-normal sm:text-4xl">{title}</h1>
            <p className="mt-4 text-base leading-7 text-white/80">{subtitle}</p>
          </div>
          <p className="text-sm text-white/70">Made in Bharat for accounting teams that need speed, control, and trust.</p>
        </div>
        <div className="flex items-center justify-center p-6 lg:p-10">{children}</div>
      </section>
    </main>
  );
}
