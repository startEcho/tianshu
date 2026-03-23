import LoginScreen from "@/components/LoginScreen";

interface LoginPageProps {
  searchParams?: Promise<{ next?: string }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const resolvedParams = (await searchParams) ?? {};
  const nextPath = resolvedParams.next || "/dashboard";
  return <LoginScreen nextPath={nextPath} />;
}
