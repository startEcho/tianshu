"use client";

import Image from "next/image";
import Link from "next/link";
import { useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";

const primaryLinks = [
  { href: "/", label: "Overview" },
  { href: "/dashboard", label: "Mission Control" },
  { href: "/vulnerabilities", label: "Vulnerability Atlas" },
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const session = useAuthStore((state) => state.session);
  const status = useAuthStore((state) => state.status);
  const logout = useAuthStore((state) => state.logout);

  const links = useMemo(() => {
    if (!session?.user.authorities.includes("user:read")) {
      return primaryLinks;
    }
    return [...primaryLinks, { href: "/admin", label: "Ops Console" }];
  }, [session?.user.authorities]);

  const handleLogout = async () => {
    await logout();
    router.push("/login");
  };

  return (
    <header className="sticky top-0 z-50 border-b border-white/8 bg-[rgba(6,12,18,0.86)] backdrop-blur-xl">
      <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-6 px-4 py-4 sm:px-6 lg:px-10">
        <Link
          href="/"
          className="group flex min-w-0 items-center gap-3"
          onClick={() => setOpen(false)}
        >
          <div className="relative h-11 w-11 overflow-hidden rounded-2xl border border-white/10 bg-white/6 shadow-[var(--shadow-soft)]">
            <Image src="/transparent_emblem.png" alt="TianShu" fill className="object-contain p-2" />
          </div>
          <div className="min-w-0">
            <p className="eyebrow text-[0.65rem] text-[var(--accent)]">Exploit Lab Command</p>
            <p className="truncate text-lg font-semibold text-white transition group-hover:text-[var(--accent)]">
              TianShu Control Fabric
            </p>
          </div>
        </Link>

        <button
          type="button"
          className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/6 text-white md:hidden"
          onClick={() => setOpen((value) => !value)}
          aria-label="Toggle navigation"
        >
          <span className="text-xl">{open ? "×" : "≡"}</span>
        </button>

        <nav className="hidden items-center gap-3 md:flex">
          {links.map((link) => {
            const active = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`rounded-full px-4 py-2 text-sm font-medium transition ${
                  active
                    ? "bg-[var(--accent)] text-slate-950"
                    : "text-[var(--muted)] hover:bg-white/8 hover:text-white"
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          {status === "authenticated" && session ? (
            <>
              <div className="rounded-full border border-white/10 bg-white/6 px-4 py-2 text-right shadow-[var(--shadow-soft)]">
                <p className="text-xs uppercase tracking-[0.24em] text-[var(--muted)]">
                  {session.user.roles.join(" · ")}
                </p>
                <p className="mt-1 text-sm font-medium text-white">{session.user.displayName}</p>
              </div>
              <button type="button" className="action-button" onClick={handleLogout}>
                Sign out
              </button>
            </>
          ) : (
            <Link href="/login" className="action-button">
              Sign in
            </Link>
          )}
        </div>
      </div>

      {open ? (
        <div className="border-t border-white/8 bg-[rgba(6,12,18,0.96)] px-4 py-4 md:hidden">
          <nav className="flex flex-col gap-2">
            {links.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={`rounded-2xl px-4 py-3 text-sm font-medium ${
                  pathname === link.href
                    ? "bg-[var(--accent)] text-slate-950"
                    : "bg-white/4 text-white"
                }`}
                onClick={() => setOpen(false)}
              >
                {link.label}
              </Link>
            ))}
            {status === "authenticated" ? (
              <button
                type="button"
                className="rounded-2xl border border-white/10 bg-white/6 px-4 py-3 text-left text-sm font-medium text-white"
                onClick={async () => {
                  setOpen(false);
                  await handleLogout();
                }}
              >
                Sign out
              </button>
            ) : (
              <Link
                href="/login"
                className="rounded-2xl border border-white/10 bg-white/6 px-4 py-3 text-sm font-medium text-white"
                onClick={() => setOpen(false)}
              >
                Sign in
              </Link>
            )}
          </nav>
        </div>
      ) : null}
    </header>
  );
}
