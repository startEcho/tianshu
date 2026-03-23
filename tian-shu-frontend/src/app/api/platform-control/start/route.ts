import { NextRequest, NextResponse } from "next/server";
import { PlatformControlError, queuePlatformAction, requireAdminOperatorAccess } from "@/lib/platformControl";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function errorResponse(error: unknown) {
  if (error instanceof PlatformControlError) {
    return NextResponse.json({ error: error.message }, { status: error.status });
  }

  console.error(error);
  return NextResponse.json({ error: "Platform start request failed." }, { status: 500 });
}

export async function POST(request: NextRequest) {
  try {
    await requireAdminOperatorAccess(request.headers.get("authorization"));
    const body = (await request.json().catch(() => ({}))) as { mode?: "quick" | "rebuild" };
    const response = await queuePlatformAction("start", {
      mode: body.mode === "rebuild" ? "rebuild" : "quick",
    });
    return NextResponse.json(response, { status: 202 });
  } catch (error) {
    return errorResponse(error);
  }
}
