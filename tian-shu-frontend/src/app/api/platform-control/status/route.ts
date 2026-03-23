import { NextRequest, NextResponse } from "next/server";
import { getPlatformRuntimeStatus, PlatformControlError, requireAdminOperatorAccess } from "@/lib/platformControl";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function errorResponse(error: unknown) {
  if (error instanceof PlatformControlError) {
    return NextResponse.json({ error: error.message }, { status: error.status });
  }

  console.error(error);
  return NextResponse.json({ error: "Platform status inspection failed." }, { status: 500 });
}

export async function GET(request: NextRequest) {
  try {
    await requireAdminOperatorAccess(request.headers.get("authorization"));
    const status = await getPlatformRuntimeStatus();
    return NextResponse.json(status, {
      headers: {
        "Cache-Control": "no-store",
      },
    });
  } catch (error) {
    return errorResponse(error);
  }
}
