import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Classroom Doubt Portal | PES University DBMS Level 3",
  description:
    "Anonymous Classroom Doubt Portal backed by MySQL 8.0, MongoDB Vector Search, and Spring Boot.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="bg-slate-50 text-slate-900 antialiased">{children}</body>
    </html>
  );
}
