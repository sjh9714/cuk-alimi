import type { Metadata } from "next";

export const metadata: Metadata = { title: "학생 혜택" };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
