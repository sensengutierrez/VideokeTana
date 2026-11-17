// YouTube karaoke search proxy para sa Videoke app.
// Nakatago ang YouTube API key dito sa server (secret YT_API_KEY), hindi sa website.
// Pinapayagan lang ang requests mula sa sariling site para hindi maubos ang quota.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const ALLOWED = [
  "https://videoketana.tigomenote.com",
  "https://sensengutierrez.github.io",
  "http://localhost",
  "http://127.0.0.1",
];

function corsFor(origin: string) {
  const ok = ALLOWED.some((a) => origin === a || origin.startsWith(a + ":") || origin.startsWith(a + "/"));
  return {
    ok,
    headers: {
      "Access-Control-Allow-Origin": ok ? origin : ALLOWED[0],
      "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Vary": "Origin",
    } as Record<string, string>,
  };
}

function decode(s: string) {
  return s.replace(/&amp;/g, "&").replace(/&#39;/g, "'").replace(/&quot;/g, '"').replace(/&lt;/g, "<").replace(/&gt;/g, ">");
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin") || "";
  const { ok, headers } = corsFor(origin);
  const json = (body: unknown, status = 200, extra: Record<string, string> = {}) =>
    new Response(JSON.stringify(body), { status, headers: { ...headers, ...extra, "Content-Type": "application/json" } });

  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (!ok) return json({ error: "forbidden_origin" }, 403);

  const key = Deno.env.get("YT_API_KEY");
  if (!key) return json({ error: "no_key" });

  const url = new URL(req.url);
  const q = (url.searchParams.get("q") || "").trim().slice(0, 100);
  const mode = url.searchParams.get("mode") === "latest" ? "latest" : "search";

  const p = new URLSearchParams({
    part: "snippet",
    type: "video",
    videoEmbeddable: "true",
    maxResults: "20",
    regionCode: "PH",
    safeSearch: "moderate",
    key,
  });
  if (mode === "latest") {
    p.set("q", q ? `${q} karaoke` : "karaoke version with lyrics");
    p.set("order", "viewCount");
    p.set("publishedAfter", new Date(Date.now() - 120 * 864e5).toISOString());
  } else {
    if (!q) return json({ items: [] });
    p.set("q", /karaoke|videoke|instrumental|minus ?one/i.test(q) ? q : `${q} karaoke`);
  }

  try {
    const r = await fetch("https://www.googleapis.com/youtube/v3/search?" + p.toString());
    const j = await r.json();
    if (j.error) {
      const reason = j.error.errors?.[0]?.reason || "yt_error";
      return json({ error: reason === "quotaExceeded" ? "quota" : reason });
    }
    const items = (j.items || [])
      .filter((it: any) => it.id?.videoId)
      .map((it: any) => ({
        id: it.id.videoId,
        title: decode(it.snippet.title || ""),
        channel: decode(it.snippet.channelTitle || ""),
        thumb: it.snippet.thumbnails?.medium?.url || it.snippet.thumbnails?.default?.url || "",
        published: it.snippet.publishedAt,
      }));
    return json({ items }, 200, { "Cache-Control": "public, max-age=600" });
  } catch (_e) {
    return json({ error: "network" }, 502);
  }
});
