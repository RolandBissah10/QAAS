import type { Config } from "tailwindcss";

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#18212f",
        line: "#d8dee8",
        panel: "#f6f8fb",
        brand: "#0f766e",
        accent: "#b45309",
      },
      boxShadow: {
        soft: "0 12px 30px rgba(24, 33, 47, 0.08)",
      },
    },
  },
  plugins: [],
} satisfies Config;
