// Deletes the signed-in user's account. Profile and contribution rows cascade from auth.users.
// Deploy with JWT verification enabled (the default). Uses the service role only inside Supabase.
import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response(null, { status: 405 });

  const authorization = request.headers.get("Authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!token) return new Response(null, { status: 401 });

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );

  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) return new Response(null, { status: 401 });

  const deleted = await admin.auth.admin.deleteUser(data.user.id);
  if (deleted.error) return new Response(null, { status: 500 });

  return new Response(null, { status: 204 });
});
