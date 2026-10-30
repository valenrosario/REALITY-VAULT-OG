// REALITY VAULT · Edge Function "r2-upload"
// Le da al panel (solo si es administrador) un enlace firmado y temporal para subir un video
// directo a Cloudflare R2, sin exponer las claves de R2 en la página.
// Secretos necesarios: R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET, R2_PUBLIC_URL
import { createClient } from "jsr:@supabase/supabase-js@2";
import { AwsClient } from "npm:aws4fetch@1.0.20";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
      auth: { persistSession: false },
    });
    const { data: ok } = await sb.rpc("is_admin");
    if (ok !== true) return json({ error: "solo el administrador puede subir" }, 403);

    const { ext, type } = await req.json();
    const e = String(ext || "mp4").toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 5) || "mp4";
    const key = `banners/${Date.now().toString(36)}${crypto.randomUUID().slice(0, 8)}.${e}`;
    const acc = Deno.env.get("R2_ACCOUNT_ID")!, bucket = Deno.env.get("R2_BUCKET")!;
    const r2 = new AwsClient({
      accessKeyId: Deno.env.get("R2_ACCESS_KEY_ID")!,
      secretAccessKey: Deno.env.get("R2_SECRET_ACCESS_KEY")!,
      service: "s3", region: "auto",
    });
    const u = new URL(`https://${acc}.r2.cloudflarestorage.com/${bucket}/${key}`);
    u.searchParams.set("X-Amz-Expires", "3600");
    const signed = await r2.sign(new Request(u, { method: "PUT", headers: { "Content-Type": type || "video/mp4" } }), { aws: { signQuery: true } });
    const pub = Deno.env.get("R2_PUBLIC_URL")!.replace(/\/+$/, "");
    return json({ uploadUrl: signed.url, publicUrl: `${pub}/${key}` });
  } catch (err) {
    return json({ error: String(err) }, 500);
  }
});
