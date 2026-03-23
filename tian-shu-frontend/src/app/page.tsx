import Link from "next/link";

const stacks = [
  "Ingress / NGINX",
  "Spring Cloud Gateway",
  "Auth Service",
  "Business Services",
  "Redis",
  "PostgreSQL",
  "Nacos",
  "Prometheus + Grafana",
  "Loki + Promtail",
  "Zipkin",
];

const tracks = [
  {
    title: "Identity control",
    body: "JWT login, refresh-token rotation, RBAC roles, and privileged operator views.",
  },
  {
    title: "Exploit orchestration",
    body: "Launch labs from curated definitions, monitor ownership, and terminate instances from one control plane.",
  },
  {
    title: "Operator telemetry",
    body: "Prometheus metrics, Loki logs, Zipkin traces, and direct entry points for Grafana and Nacos.",
  },
];

export default function HomePage() {
  return (
    <div className="space-y-10 lg:space-y-12">
      <section className="surface-elevated overflow-hidden rounded-[2rem] px-6 py-10 sm:px-8 lg:px-12">
        <div className="grid gap-10 lg:grid-cols-[1.4fr_0.9fr] lg:items-end">
          <div>
            <p className="eyebrow">Full-stack security platform</p>
            <h1 className="hero-title mt-5 max-w-4xl text-5xl leading-[0.95] text-white sm:text-6xl lg:text-7xl">
              A mission-control interface for exploit labs, identity, and runtime telemetry.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-[var(--muted)] sm:text-lg">
              TianShu now exposes the complete system surface: gateway-authenticated APIs, PostgreSQL-backed services,
              lab lifecycle operations, and a built-in operator path to observability.
            </p>
            <div className="mt-8 flex flex-wrap gap-4">
              <Link href="/login" className="action-button">
                Enter control fabric
              </Link>
              <Link href="/vulnerabilities" className="action-button secondary">
                Inspect lab catalog
              </Link>
            </div>
          </div>

          <div className="grid gap-4 rounded-[1.75rem] border border-white/10 bg-[rgba(255,255,255,0.03)] p-5">
            {stacks.map((item, index) => (
              <div
                key={item}
                className="flex items-center justify-between rounded-[1.2rem] border border-white/6 bg-black/10 px-4 py-3"
              >
                <span className="text-sm text-[var(--muted)]">0{index + 1}</span>
                <span className="text-right text-sm font-semibold text-white">{item}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="data-grid three">
        {tracks.map((track) => (
          <article key={track.title} className="surface rounded-[1.75rem] p-6">
            <p className="eyebrow">Capability lane</p>
            <h2 className="mt-4 text-2xl font-semibold text-white">{track.title}</h2>
            <p className="mt-4 text-sm leading-7 text-[var(--muted)]">{track.body}</p>
          </article>
        ))}
      </section>

      <section className="surface rounded-[2rem] px-6 py-8 sm:px-8 lg:px-10">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="eyebrow">Demo credentials</p>
            <h2 className="mt-4 text-3xl font-semibold text-white">Built for end-to-end interview walkthroughs.</h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-[var(--muted)]">
              Use the seeded operator accounts to move through the full system, from authentication to experiment launch
              to observability.
            </p>
          </div>
          <div className="grid min-w-[280px] gap-3 text-sm">
            <div className="rounded-[1.25rem] border border-white/8 bg-black/10 px-4 py-3">
              <span className="text-[var(--muted)]">ADMIN</span>
              <p className="mt-1 font-semibold text-white">admin / Admin123456</p>
            </div>
            <div className="rounded-[1.25rem] border border-white/8 bg-black/10 px-4 py-3">
              <span className="text-[var(--muted)]">TRAINER</span>
              <p className="mt-1 font-semibold text-white">trainer / Trainer123456</p>
            </div>
            <div className="rounded-[1.25rem] border border-white/8 bg-black/10 px-4 py-3">
              <span className="text-[var(--muted)]">STUDENT</span>
              <p className="mt-1 font-semibold text-white">student / Student123456</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
