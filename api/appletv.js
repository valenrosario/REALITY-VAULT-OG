// Lee una serie de Apple TV (tv.apple.com) y devuelve sus temporadas con la miniatura de cada capítulo.
// Se usa desde el admin: GET /api/appletv?url=<enlace de la serie>
// Solo habla con tv.apple.com (no sigue otros dominios).
const UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';
const FALLBACK = { utscf: 'OjAAAAEAAAAAAAMAEAAAACMAKwAtADgA', caller: 'web', v: '101', pfm: 'web' };
const SF = { us:143441, ar:143505, mx:143468, es:143454, br:143503, cl:143483, co:143501, pe:143507, uy:143514, gb:143444, ca:143455, de:143443, fr:143442, it:143450 };
const ID = /umc\.cmc\.[a-z0-9]+/i;

function showIdFrom(raw){
  let u; try { u = new URL(raw); } catch(_){ return null; }
  if (u.hostname !== 'tv.apple.com') return null;
  const q = u.searchParams.get('showId');
  if (q && ID.test(q)) return { id: q.match(ID)[0], store: (u.pathname.split('/')[1] || 'us').toLowerCase() };
  const parts = u.pathname.split('/').filter(Boolean);           // [us, show, nombre, umc.cmc.xxx]
  if (parts[1] === 'show' && ID.test(parts[parts.length - 1])) return { id: parts[parts.length - 1].match(ID)[0], store: parts[0].toLowerCase() };
  return null;
}
const art = e => {
  const i = e.images || {};
  const raw = i.posterArt?.url || (i.contentImage?.url || '').replace(/KF\.[A-Za-z0-9]+\./, '.');
  return raw ? raw.replace('{w}', '1280').replace('{h}', '720').replace('{f}', 'jpg').replace(/\?.*$/, '') : '';
};
module.exports = async (req, res) => {
  res.setHeader('Cache-Control', 's-maxage=600, stale-while-revalidate=3600');
  try {
    const raw = String((req.query && req.query.url) || new URL(req.url, 'http://x').searchParams.get('url') || '').trim();
    const s = showIdFrom(raw);
    if (!s) return res.status(400).json({ error: 'Pegá el enlace de la serie en Apple TV (tv.apple.com/…/show/…).' });
    // La página de la serie trae los parámetros vigentes de la API (cambian cada tanto).
    const page = await fetch(`https://tv.apple.com/${s.store}/show/x/${s.id}`, { headers: { 'User-Agent': UA, 'Accept-Language': 'es-AR,es;q=0.9,en;q=0.8' } });
    const html = page.ok ? await page.text() : '';
    const cfg = (html.match(/"Default":\{"utscf":"[^}]*\}/) || [''])[0];
    const g = k => (cfg.match(new RegExp('"' + k + '":"([^"]*)"')) || [])[1];
    const p = new URLSearchParams({ sf: g('sf') || String(SF[s.store] || 143441), locale: g('locale') || 'en-US', caller: g('caller') || FALLBACK.caller,
      utscf: g('utscf') || FALLBACK.utscf, v: g('v') || FALLBACK.v, pfm: g('pfm') || FALLBACK.pfm, nextToken: '0:300' });
    if (g('utsk')) p.set('utsk', g('utsk'));
    const r = await fetch(`https://tv.apple.com/api/uts/v3/shows/${s.id}/episodes?${p}`, { headers: { 'User-Agent': UA, Referer: 'https://tv.apple.com/' } });
    if (!r.ok) return res.status(502).json({ error: 'Apple TV no respondió (' + r.status + '). Probá de nuevo en un rato.' });
    const d = (await r.json()).data || {};
    const eps = d.episodes || [];
    if (!eps.length) return res.status(404).json({ error: 'No encontré capítulos en esa serie.' });
    const by = new Map();
    for (const e of eps){
      const n = e.seasonNumber ?? 1;
      if (!by.has(n)) by.set(n, []);
      by.get(n).push({ number: e.episodeNumber, title: e.title || '', description: e.description || '', duration: e.duration ? Math.round(e.duration / 60) + ' min' : '', thumb: art(e) });
    }
    const seasons = [...by.entries()].sort((a, b) => a[0] - b[0]).map(([number, episodes]) => ({ number, episodes: episodes.sort((a, b) => a.number - b.number) }));
    const title = (html.match(/<title>([^<]*)<\/title>/) || [])[1] || '';
    res.status(200).json({ show: s.id, title: title.replace(/[\u200e\u200f]/g, '').replace(/^(Watch|Ver)\s+/i, '').replace(/\s+(on Apple TV|en Apple TV).*$/i, '').replace(/\s*[-–—]\s*(Apple TV|Show|Serie|Series).*$/i, '').trim(), seasons });
  } catch (err) { res.status(500).json({ error: 'No se pudo leer Apple TV: ' + (err.message || err) }); }
};
