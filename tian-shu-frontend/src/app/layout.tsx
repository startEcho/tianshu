import type { Metadata } from "next";
import "react-toastify/dist/ReactToastify.css";
import "./globals.css";
import { ToastContainer } from "react-toastify";
import Navbar from "@/components/Navbar";
import AuthBootstrap from "@/components/AuthBootstrap";

export const metadata: Metadata = {
  title: "TianShu Control Fabric",
  description: "A full-stack Java vulnerability lab control plane with gateway, auth, and observability.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <AuthBootstrap />
        <div className="site-shell">
          <Navbar />
          <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-10 lg:py-10">
            {children}
          </main>
          <footer className="mx-auto w-full max-w-7xl border-t border-white/8 px-4 py-6 text-sm text-[var(--muted)] sm:px-6 lg:px-10">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <p>Gateway-authenticated exploit lab platform with PostgreSQL, Redis, Nacos, Prometheus, Loki, and Zipkin.</p>
              <p>Control Fabric © {new Date().getFullYear()}</p>
            </div>
          </footer>
        </div>
        <ToastContainer
          position="top-right"
          autoClose={3500}
          hideProgressBar={false}
          newestOnTop
          closeOnClick
          pauseOnHover
          theme="dark"
        />
      </body>
    </html>
  );
}
