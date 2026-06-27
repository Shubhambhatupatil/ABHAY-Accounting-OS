/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        abhay: {
          background: "#050816",
          card: "#0F172A",
          card2: "#111827",
          orange: "#F97316",
          cyan: "#22D3EE",
          teal: "#14B8A6",
          gold: "#FACC15",
          text: "#F8FAFC",
          muted: "#94A3B8",
          border: "#1E293B"
        }
      },
      boxShadow: {
        glow: "0 0 45px rgba(34, 211, 238, 0.16)",
        orange: "0 18px 45px rgba(249, 115, 22, 0.2)"
      },
      backgroundImage: {
        "ai-grid":
          "linear-gradient(rgba(34,211,238,0.07) 1px, transparent 1px), linear-gradient(90deg, rgba(249,115,22,0.06) 1px, transparent 1px)"
      }
    }
  },
  plugins: []
};
