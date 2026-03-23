"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuthStore } from "@/store/authStore";

interface AuthGateProps {
  children: React.ReactNode;
  requiredAuthorities?: string[];
}

export default function AuthGate({ children, requiredAuthorities = [] }: AuthGateProps) {
  const router = useRouter();
  const pathname = usePathname();
  const hydrated = useAuthStore((state) => state.hydrated);
  const status = useAuthStore((state) => state.status);
  const session = useAuthStore((state) => state.session);

  useEffect(() => {
    if (!hydrated || status !== "anonymous") {
      return;
    }
    router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [hydrated, pathname, router, status]);

  if (!hydrated || status === "booting" || status === "authenticating") {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="rounded-[2rem] border border-white/10 bg-white/6 px-6 py-5 text-sm uppercase tracking-[0.28em] text-[var(--muted)] shadow-[var(--shadow-soft)]">
          Synchronizing control plane
        </div>
      </div>
    );
  }

  if (status !== "authenticated" || !session) {
    return null;
  }

  if (
    requiredAuthorities.length > 0 &&
    !requiredAuthorities.some((authority) => session.user.authorities.includes(authority))
  ) {
    return (
      <div className="rounded-[2rem] border border-[var(--danger)]/30 bg-[var(--panel)] p-8 shadow-[var(--shadow-soft)]">
        <p className="eyebrow">Authorization boundary</p>
        <h2 className="mt-4 text-3xl font-semibold text-white">This route is reserved for elevated operators.</h2>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-[var(--muted)]">
          Your current role set does not include the permissions required to operate the identity and access console.
        </p>
        <Link href="/dashboard" className="action-button mt-8 inline-flex">
          Return to mission control
        </Link>
      </div>
    );
  }

  return <>{children}</>;
}
