// REALITY VAULT · Edge Function "tv-login"
// Cuando alguien activa la tele desde el celular, esta función le da a la tele
// su propia sesión de esa cuenta (sin compartir contraseñas ni cerrar la del celular).
import { createClient } from "jsr:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const { code, secret } = await req.json();
    if (!code || !secret) return json({ error: "faltan datos" }, 400);
    const url = Deno.env.get("SUPABASE_URL")!;
    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

    const { data: row } = await admin.from("tv_codes").select("*").eq("code", String(code).toUpperCase()).maybeSingle();
    if (!row || row.secret !== secret) return json({ error: "codigo invalido" }, 400);
    if (!row.user_id) return json({ error: "esperando" }, 409);
    if (new Date(row.expires_at).getTime() < Date.now()) return json({ error: "vencido" }, 410);
    await admin.from("tv_codes").delete().eq("code", row.code);

    const { data: u, error: ue } = await admin.auth.admin.getUserById(row.user_id);
    if (ue || !u?.user?.email) return json({ error: "usuario no encontrado" }, 400);
    const { data: link, error: le } = await admin.auth.admin.generateLink({ type: "magiclink", email: u.user.email });
    if (le || !link?.properties?.hashed_token) return json({ error: le?.message || "no se pudo crear la sesión" }, 500);

    const pub = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { auth: { persistSession: false } });
    const { data: s, error: se } = await pub.auth.verifyOtp({ token_hash: link.properties.hashed_token, type: "magiclink" });
    if (se || !s?.session) return json({ error: se?.message || "no se pudo crear la sesión" }, 500);

    return json({
      access_token: s.session.access_token,
      refresh_token: s.session.refresh_token,
      expires_in: s.session.expires_in,
      user: { id: s.user?.id, email: s.user?.email },
    });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
