import type { Metadata } from "next";
import { Be_Vietnam_Pro, Space_Grotesk } from "next/font/google";
import { Providers } from "@/components/providers";
import { Shell } from "@/components/features/shell";
import "./globals.css";
import "./experience.css";
import "./customer.css";
import "./dashboard.css";
import "./venue.css";
const body = Be_Vietnam_Pro({
  subsets: ["vietnamese", "latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-body",
  display: "swap",
});
const display = Space_Grotesk({
  subsets: ["vietnamese", "latin"],
  variable: "--font-display",
  display: "swap",
});
export const metadata: Metadata = {
  title: {
    default: "EventHub · Hẹn nhau ở những khoảnh khắc",
    template: "%s | EventHub",
  },
  description:
    "Khám phá sự kiện, đặt vé và trải nghiệm những khoảnh khắc đáng nhớ. Bản trải nghiệm UI/UX EventHub.",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html
      lang="vi"
      className={`${body.variable} ${display.variable}`}
      suppressHydrationWarning
    >
      <body>
        <Providers>
          <Shell>{children}</Shell>
        </Providers>
      </body>
    </html>
  );
}
