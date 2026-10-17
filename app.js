(() => {
const S = { site:{}, db:null, user:null, owner:false, titles:[], banners:[], loaded:{t:false,b:false}, noDb:false,
  route:'home', q:'', genre:'', heroIdx:0, heroTimer:null,
  adminTab:'catalogo', adminFilter:'', catalogFilter:'todo', catalogPicker:false, quick:false, qt:null, editing:null, editorTab:'info', dirty:false };
const app = document.getElementById('app');
const overlayRoot = document.getElementById('overlayRoot');

/* ---------- utils ---------- */
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid = p => (p||'') + Date.now().toString(36) + Math.random().toString(36).slice(2,8);
const hue = s => { let h=0; for (const c of String(s)) h = (h*31 + c.charCodeAt(0)) % 997; return [330,345,300,15,280,355,320][h%7]; };
const codeOf = t => 'RV-' + String(t.code || 0).padStart(3,'0');
const clone = o => JSON.parse(JSON.stringify(o));
const safeUrl = u => /^https?:\/\//i.test(u||'') ? u : '';
let toastT;
function toast(msg, err){
  document.querySelector('.toast')?.remove();
  const d = document.createElement('div'); d.className = 'toast' + (err?' err':''); d.setAttribute('role','status'); d.textContent = msg;
  document.body.appendChild(d); clearTimeout(toastT); toastT = setTimeout(()=>d.remove(), 2600);
}

/* ---------- media (Supabase Storage) ---------- */
function mediaUrl(ref){
  if (!ref) return Promise.resolve(null);
  if (/^(https?:|data:|blob:)/i.test(ref)) return Promise.resolve(ref);
  return Promise.resolve(null);
}
const mqMobile = window.matchMedia('(max-width:640px)');
const isMobile = () => mqMobile.matches;
function hydrate(root){
  root.querySelectorAll('[data-src]').forEach(el => {
    const ref = (isMobile() && el.getAttribute('data-src-m')) || el.getAttribute('data-src'); el.removeAttribute('data-src'); el.removeAttribute('data-src-m');
    mediaUrl(ref).then(u => { if (u){ el.style.backgroundImage = `url("${u}")`; el.classList.add('has-img','filled'); } });
  });
  root.querySelectorAll('img[data-img]').forEach(el => {
    const ref = el.getAttribute('data-img'); el.removeAttribute('data-img');
    const showFb = () => { const fb = el.nextElementSibling; if (fb?.classList.contains('logo-fallback')) fb.hidden = false; el.remove(); };
    el.onerror = showFb;
    mediaUrl(ref).then(u => { if (u) { el.src = u; el.hidden = false; } else showFb(); });
  });
  if (root.querySelector('.pillseg')) syncPillSegs(true);
}
function syncPillSegs(first){
  document.querySelectorAll('.pillseg').forEach(seg => {
    const pill = seg.querySelector('.pillseg-pill');
    const btn = seg.querySelector('button[aria-pressed="true"]') || seg.querySelector('button');
    if (!pill || !btn) return;
    if (first) pill.style.transition = 'none';
    const sr = seg.getBoundingClientRect(), r = btn.getBoundingClientRect();
    pill.style.width = r.width + 'px'; pill.style.transform = `translateX(${r.left - sr.left - 4}px)`;
    if (first) requestAnimationFrame(() => requestAnimationFrame(() => { pill.style.transition = ''; }));
  });
}
const webpOK = (() => { try { const c=document.createElement('canvas'); c.width=c.height=1; return c.toDataURL('image/webp').startsWith('data:image/webp'); } catch { return false; } })();
async function processImage(file, maxW, maxH, alpha){
  if (file.type === 'image/gif' || file.type === 'image/svg+xml') return file;
  let src;
  try { src = await createImageBitmap(file); }
  catch {
    src = await new Promise((res, rej) => { const i = new Image(); i.onload=()=>res(i); i.onerror=rej; i.src = URL.createObjectURL(file); });
  }
  const scale = Math.min(1, maxW/src.width, maxH/src.height);
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(src.width*scale)); c.height = Math.max(1, Math.round(src.height*scale));
  const x = c.getContext('2d');
  const type = webpOK ? 'image/webp' : (alpha ? 'image/png' : 'image/jpeg');
  if (!alpha && type==='image/jpeg'){ x.fillStyle = '#000'; x.fillRect(0,0,c.width,c.height); }
  x.drawImage(src,0,0,c.width,c.height);
  return await new Promise((res, rej) => c.toBlob(bl => bl ? res(bl) : rej(new Error('No se pudo procesar la imagen')), type, .88));
}
const IMG_SPECS = { tv:[1920,1080,false], frow:[1000,1480,false], mobilebanner:[1350,1700,false], mobile:[1600,1000,false], sitelogo:[1400,460,true], poster:[1200,1800,false], backdrop:[2560,1440,false], logo:[1400,560,true], thumb:[1280,720,false], face:[400,400,false], banner:[3000,1000,false] };
const BUCKET = 'media';
async function uploadImage(file, kind){
  const [w,h,alpha] = IMG_SPECS[kind] || IMG_SPECS.backdrop;
  const blob = await processImage(file, w, h, alpha);
  const ext = ({'image/webp':'webp','image/png':'png','image/jpeg':'jpg','image/gif':'gif','image/svg+xml':'svg'})[blob.type] || 'img';
  const path = `${kind}/${uid('m')}.${ext}`;
  const { error } = await S.sb.storage.from(BUCKET).upload(path, blob, { contentType: blob.type, cacheControl: '31536000', upsert:false });
  if (error) throw new Error('No se pudo subir la imagen: ' + error.message);
  return S.sb.storage.from(BUCKET).getPublicUrl(path).data.publicUrl;
}
async function dataUrlToStorage(dataUrl, kind){
  const blob = await (await fetch(dataUrl)).blob();
  return uploadImage(new File([blob], 'img', { type: blob.type }), kind || 'backdrop');
}
function storagePathOf(url){
  const m = String(url||'').match(/\/storage\/v1\/object\/public\/media\/(.+)$/);
  return m ? decodeURIComponent(m[1]) : null;
}
function removeMedia(urls){
  const paths = urls.map(storagePathOf).filter(Boolean);
  if (paths.length) S.sb.storage.from(BUCKET).remove(paths).catch(()=>{});
}
/* --- crop before upload --- */
// [aspect ratio, output width]; logos are not cropped (they keep their transparent edges)
const CROP_SPECS = { tv:[16/9,1920], frow:[3/4.45,900], poster:[2/3,1200], backdrop:[16/9,2560], banner:[3,3000], mobilebanner:[4/5,1350], mobile:[16/10,1600], thumb:[16/9,1280], face:[1,400] };
function cropImage(file, aspect, outW){
  return new Promise(async (resolve, reject) => {
    const src = URL.createObjectURL(file);
    const img = new Image();
    try { await new Promise((ok, bad) => { img.onload = ok; img.onerror = bad; img.src = src; }); } catch { URL.revokeObjectURL(src); reject(new Error('No se pudo abrir la imagen')); return; }
    const iw = img.naturalWidth, ih = img.naturalHeight;
    const root = document.createElement('div');
    root.className = 'crop-modal';
    const ratioTxt = aspect >= 1 ? `${Math.round(aspect*100)/100}:1` : `1:${Math.round(100/aspect)/100}`;
    root.innerHTML = `<div class="crop-card" role="dialog" aria-modal="true" aria-label="Recortar imagen">
      <h3>Elegí qué parte se ve</h3>
      <p class="crop-hint">Arrastrá la imagen para moverla y usá la barra (o la rueda del mouse) para acercar.</p>
      <div class="crop-frame" style="aspect-ratio:${aspect};width:min(100%, calc(58vh * ${aspect}))"><img class="crop-img" alt="" draggable="false"><span class="crop-grid" aria-hidden="true"></span></div>
      <div class="crop-zoom-row"><span>−</span><input type="range" class="crop-zoom" min="1" max="4" step="0.01" value="1" aria-label="Acercar"><span>+</span></div>
      <div class="crop-actions"><button class="btn btn-line" data-c="cancel" type="button">Cancelar</button><button class="btn btn-line" data-c="full" type="button">Usar sin recortar</button><button class="btn btn-primary" data-c="ok" type="button">Usar recorte</button></div>
    </div>`;
    document.body.appendChild(root);
    const frame = root.querySelector('.crop-frame'), el = root.querySelector('.crop-img'), zoomIn = root.querySelector('.crop-zoom');
    el.src = src;
    let W, H, base, z = 1, x = 0, y = 0;
    const clamp = () => { const dw = iw*base*z, dh = ih*base*z; x = Math.min(0, Math.max(W - dw, x)); y = Math.min(0, Math.max(H - dh, y)); };
    const paint = () => { el.style.width = (iw*base*z) + 'px'; el.style.height = (ih*base*z) + 'px'; el.style.transform = `translate(${x}px,${y}px)`; };
    const measure = () => { const r = frame.getBoundingClientRect(); const pw = W; W = r.width; H = r.height; base = Math.max(W/iw, H/ih); if (!pw){ x = (W - iw*base)/2; y = (H - ih*base)/2; } else { x = x * W / pw; y = y * W / pw; } clamp(); paint(); };
    requestAnimationFrame(measure);
    const ro = new ResizeObserver(() => W && measure()); ro.observe(frame);
    const setZoom = (nz, cx = W/2, cy = H/2) => {
      nz = Math.min(4, Math.max(1, nz));
      const k = nz / z; x = cx - (cx - x) * k; y = cy - (cy - y) * k; z = nz; zoomIn.value = z; clamp(); paint();
    };
    zoomIn.addEventListener('input', () => setZoom(+zoomIn.value));
    frame.addEventListener('wheel', ev => { ev.preventDefault(); const r = frame.getBoundingClientRect(); setZoom(z * (ev.deltaY < 0 ? 1.08 : 1/1.08), ev.clientX - r.left, ev.clientY - r.top); }, { passive:false });
    const pts = new Map(); let last = null, pinch = null;
    frame.addEventListener('pointerdown', ev => { frame.setPointerCapture(ev.pointerId); pts.set(ev.pointerId, { x:ev.clientX, y:ev.clientY }); last = { x:ev.clientX, y:ev.clientY }; if (pts.size === 2){ const [a,b] = [...pts.values()]; pinch = { d: Math.hypot(a.x-b.x, a.y-b.y), z }; } });
    frame.addEventListener('pointermove', ev => {
      if (!pts.has(ev.pointerId)) return;
      pts.set(ev.pointerId, { x:ev.clientX, y:ev.clientY });
      if (pts.size === 2 && pinch){ const [a,b] = [...pts.values()]; const r = frame.getBoundingClientRect(); setZoom(pinch.z * Math.hypot(a.x-b.x, a.y-b.y) / pinch.d, (a.x+b.x)/2 - r.left, (a.y+b.y)/2 - r.top); return; }
      x += ev.clientX - last.x; y += ev.clientY - last.y; last = { x:ev.clientX, y:ev.clientY }; clamp(); paint();
    });
    const up = ev => { pts.delete(ev.pointerId); pinch = null; const rest = [...pts.values()][0]; if (rest) last = { ...rest }; };
    frame.addEventListener('pointerup', up); frame.addEventListener('pointercancel', up);
    const finish = val => { ro.disconnect(); root.remove(); URL.revokeObjectURL(src); document.removeEventListener('keydown', onKey); resolve(val); };
    const onKey = ev => { if (ev.key === 'Escape') finish(null); };
    document.addEventListener('keydown', onKey);
    root.addEventListener('click', ev => {
      const c = ev.target.closest('[data-c]')?.dataset.c; if (!c) return;
      if (c === 'cancel') return finish(null);
      if (c === 'full') return finish(file);
      const sc = base * z, sx = -x / sc, sy = -y / sc, sw = W / sc, sh = H / sc;
      const ow = Math.round(Math.min(outW, sw)), oh = Math.round(ow / aspect);
      const cv = document.createElement('canvas'); cv.width = ow; cv.height = oh;
      const g = cv.getContext('2d'); g.imageSmoothingQuality = 'high'; g.drawImage(img, sx, sy, sw, sh, 0, 0, ow, oh);
      const type = webpOK ? 'image/webp' : 'image/jpeg';
      cv.toBlob(b => finish(b ? new File([b], 'recorte.' + (type === 'image/webp' ? 'webp' : 'jpg'), { type }) : file), type, .9);
    });
  });
}
function pickFile(accept){
  return new Promise(res => {
    const inp = document.getElementById('filePick'); inp.value='';
    inp.accept = accept || 'image/*';
    inp.onchange = () => res(inp.files[0] || null);
    inp.click();
  });
}

/* ---------- derived ---------- */
const published = () => S.titles.filter(t => t.status !== 'borrador');
const byId = id => S.titles.find(t => t.id === id);
function epCount(t){ return (t.seasons||[]).reduce((n,s)=>n+(s.episodes||[]).length,0); }
function metaLine(t){
  const bits = [];
  if (t.year) bits.push(`<span>${esc(t.year)}</span>`);
  if (t.rating) bits.push(`<span class="rating">${esc(t.rating)}</span>`);
  if (t.type === 'serie'){ const n=(t.seasons||[]).length; if(n) bits.push(`<span>${n} temporada${n>1?'s':''}</span>`); }
  else if (t.duration) bits.push(`<span>${esc(t.duration)}</span>`);
  if ((t.genres||[]).length) bits.push(`<span>${esc(t.genres.slice(0,3).join(' · '))}</span>`);
  return bits.join('');
}
function genBlock(t, label, logo){
  const lg = logo === undefined ? t.logo : logo;
  return `<div class="gen ${lg?'has-logo':''}" style="--h:${hue(t.title||t.id)}">${label?`<span class="g-code">${esc(label)}</span>`:'<span></span>'}<span class="g-title">${esc(t.title||'Sin título')}</span>${lg?`<img class="g-logo" alt="" data-img="${esc(lg)}" hidden>`:''}</div>`;
}
function isSoon(t){ return t.availability === 'proximamente'; }
function soonLabel(t){ return 'Próximamente' + (t.releaseDate ? ' · ' + t.releaseDate : ''); }
function watchLink(t){
  if (isSoon(t)) return '';
  if (t.type === 'serie'){
    for (const s of t.seasons||[]) for (const e of s.episodes||[]) if (safeUrl(e.url)) return e.url;
    return '';
  }
  return safeUrl(t.url);
}

/* ---------- public views ---------- */
function card(t){
  return `<button class="card" data-open="${esc(t.id)}" aria-label="${esc(t.title)}">
    <div class="poster" ${t.poster?`data-src="${esc(t.poster)}"`:''}>${isSoon(t)?'<span class="badge">Próximamente</span>':''}${genBlock(t)}</div>
  </button>`;
}
function row(title, list, href){
  if (!list.length) return '';
  return `<section class="row"><div class="row-head"><h2>${esc(title)}</h2><span class="count">${list.length}</span>${href ? `<a class="see-all" href="${href}">Ver todo ›</a>` : ''}</div>
    <div class="row-wrap"><button class="rail-btn l" data-scroll="-1" aria-label="Anterior">‹</button>
    <div class="rail">${list.map(card).join('')}</div>
    <button class="rail-btn r" data-scroll="1" aria-label="Siguiente">›</button></div></section>`;
}
/* ---------- orden variable y recomendaciones (como las plataformas de streaming) ---------- */
// Cambia en cada visita, pero no salta mientras navegás.
S.seed = (Math.random() * 2**31) >>> 0;
function strHash(s){ let h = 2166136261; for (const c of String(s)) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; }
function rng(key){ let a = (S.seed ^ strHash(key)) >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ t >>> 15, t | 1); t ^= t + Math.imul(t ^ t >>> 7, t | 61); return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
function shuffled(list, key){ const r = rng(key), a = list.slice(); for (let i = a.length - 1; i > 0; i--){ const j = Math.floor(r() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }
// Títulos que abrió en este navegador (para recomendar aunque no tenga cuenta)
function recentOpened(){ try { return JSON.parse(localStorage.getItem('rv-opened') || '[]'); } catch { return []; } }
function rememberOpened(tid){ try { const l = recentOpened().filter(x => x !== tid); l.unshift(tid); localStorage.setItem('rv-opened', JSON.stringify(l.slice(0, 40))); } catch {} }
// Cuánto le gusta cada género según lo que vio, guardó y abrió
function tasteProfile(){
  const g = new Map(), add = (tid, w) => { const t = byId(tid); if (!t) return; (t.genres||[]).forEach(x => g.set(x, (g.get(x)||0) + w)); };
  [...S.progress.keys()].forEach(id => add(id, 3));
  [...S.myList].forEach(id => add(id, 2));
  recentOpened().forEach((id, i) => add(id, i < 10 ? 1 : .5));
  return g;
}
function tasteScore(t, prof){ return (t.genres||[]).reduce((s, x) => s + (prof.get(x) || 0), 0); }
// Ordena por gusto del usuario con un poco de azar; sin historial, al azar
function byTaste(list, key, prof = tasteProfile()){
  if (!prof.size) return shuffled(list, key);
  const r = rng(key); const max = Math.max(1, ...[...prof.values()]);
  return list.map(t => ({ t, s: tasteScore(t, prof) / max + r() * .6 })).sort((a, b) => b.s - a.s).map(x => x.t);
}
function recommendedRow(){
  const prof = tasteProfile(); if (!prof.size) return '';
  const seen = new Set([...S.progress.keys(), ...recentOpened().slice(0, 3)]);
  const list = byTaste(published().filter(t => !seen.has(t.id) && tasteScore(t, prof) > 0), 'rec', prof).slice(0, 20);
  return list.length >= 3 ? row('Recomendado para vos', list) : '';
}
function heroSlides(){
  const pub = published();
  const act = S.banners.filter(b => b.active !== false);
  const pinned = act.filter(b => b.pinned).sort((a,b)=>(a.order||0)-(b.order||0));
  let slides = [...pinned, ...shuffled(act.filter(b => !b.pinned), 'banners')]
    .map(b => ({ b, t: byId(b.titleId) })).filter(x => !x.b.titleId || (x.t && x.t.status!=='borrador'));
  if (!slides.length) slides = shuffled(pub.filter(t=>t.featured), 'featban').slice(0,5).map(t => ({ b:{}, t }));
  return slides;
}
function renderHero(){
  const slides = heroSlides();
  if (!slides.length){
    return `<section class="hero-empty"><div><div class="vault" aria-hidden="true"></div>
      <h1>La bóveda está cerrada</h1>
      <p>${S.owner ? 'Todavía no hay títulos publicados. Cargá tu primera serie o película desde el panel de control.' : 'Pronto vas a encontrar acá series y películas. Volvé en un rato.'}</p>
      ${S.owner ? '<a class="btn btn-primary" href="/admin">Abrir el panel</a>' : ''}</div></section>`;
  }
  S.heroIdx = S.heroIdx % slides.length;
  return `<section class="hero" aria-roledescription="carrusel">
    <div class="hero-track" style="transform:translateX(-${S.heroIdx*100}%)">
    ${slides.map(({b,t},i) => {
      const img = b.image || t?.bannerImage || t?.backdrop || t?.poster;
      const imgM = b.imageMobile || t?.bannerImageMobile || '';
      const headline = b.headline || t?.title || '';
      const text = b.text || t?.description || '';
      const tag = t?.tagline || '';
      const target = t ? playTarget(t) : null;
      const link = t ? (target ? target.url : '') : safeUrl(b.link);
      const vid = safeUrl(b.video || t?.previewVideo || ''), vidM = safeUrl(b.videoMobile || t?.previewVideoMobile || '');
      return `<div class="hero-slide ${i===S.heroIdx?'on':''}" data-slide="${i}" ${vid?`data-video="${esc(vid)}"`:''} ${vidM?`data-video-m="${esc(vidM)}"`:''} ${i===S.heroIdx?'':'aria-hidden="true"'}>
        <div class="hero-bg ${t?'':'wide'}" ${img||imgM?`data-src="${esc(img||imgM)}"`:''} ${imgM?`data-src-m="${esc(imgM)}"`:''}>${t?genBlock(t):''}</div>
        ${t ? `<button class="hero-tap" data-open="${esc(t.id)}" aria-label="Ver ${esc(t.title)}"></button>` : ''}
        ${vid||vidM ? `<button class="hero-mute" data-hero-mute aria-label="Activar sonido" aria-pressed="false">${MUTE_ICON(true)}</button>` : ''}
        <div class="hero-copy">
          ${b.eyebrow ? `<span class="eyebrow">${esc(b.eyebrow)}</span>` : ''}
          ${t?.logo && !b.headline ? `<img class="hero-logo" alt="${esc(t.title)}" data-img="${esc(t.logo)}" hidden><h1 class="logo-fallback" hidden>${esc(headline)}</h1>` : `<h1>${esc(headline)}</h1>`}
          ${tag ? `<div class="hero-tag">${esc(tag)}</div>` : ''}
          ${t ? `<div class="hero-m-info"><span class="g">${esc((t.genres||[]).slice(0,2).join(', '))}</span><span class="cnt">${i+1} <i>·</i> ${slides.length}</span></div>` : ''}
          ${t ? `<div class="meta">${metaLine(t)}</div><div class="meta-m">${t.rating ? `<span class="rating">${esc(t.rating)}</span>` : ''}${esc([t.year, (t.genres||[])[0]].filter(Boolean).join(' • '))}</div>` : ''}
          ${text ? `<p>${esc(text.length>220 ? text.slice(0,217)+'…' : text)}</p>` : ''}
          <div class="hero-actions">
            ${t && isSoon(t) ? `<span class="btn btn-soon">${esc(soonLabel(t))}</span>` : link ? `<a class="btn btn-primary hero-play" ${t ? watchAttrs(t, target?.season, target?.episode) : 'data-watch'} href="${esc(link)}" target="_blank" rel="noopener"><span class="lbl-d">▶ ${esc(target?.cont ? target.label : (b.cta || 'Ver ahora'))}</span><span class="lbl-m">${ICONS.play} ${target?.cont ? 'Continuar' : 'Reproducir'}</span></a>` : ''}
            ${t ? `<button class="btn btn-ghost hero-more" data-open="${esc(t.id)}">Más información</button>${listBtn(t.id,'round')}${listWide(t.id)}` : ''}
          </div>
        </div></div>`;
    }).join('')}
    </div>
    ${slides.length>1 ? `<button class="hero-arrow l" data-hero-step="-1" aria-label="Anterior">‹</button><button class="hero-arrow r" data-hero-step="1" aria-label="Siguiente">›</button>` : ''}
    ${slides.length>1 ? `<div class="hero-dots">${slides.map((_,i)=>`<button data-dot="${i}" aria-label="Destacado ${i+1}" aria-current="${i===S.heroIdx}"></button>`).join('')}</div>` : ''}
  </section>`;
}
function setHero(i){
  const slides = app.querySelectorAll('.hero-slide'); if (!slides.length) return;
  S.heroIdx = (i + slides.length) % slides.length;
  const track = app.querySelector('.hero-track');
  if (track){ track.style.transition = ''; track.style.transform = `translateX(-${S.heroIdx*100}%)`; }
  slides.forEach((s,k)=>{ s.classList.toggle('on', k===S.heroIdx); s.toggleAttribute('aria-hidden', k!==S.heroIdx); });
  app.querySelectorAll('[data-dot]').forEach((d,k)=>d.setAttribute('aria-current', k===S.heroIdx));
}
// swipe with the finger (or drag with the mouse) to change banner
(function heroSwipe(){
  let st = null, dragged = false;
  app.addEventListener('pointerdown', e => {
    const track = e.target.closest('.hero-track'); if (!track || e.button > 0) return;
    if (track.children.length < 2) return;
    st = { x:e.clientX, y:e.clientY, w:track.getBoundingClientRect().width, track, id:e.pointerId, horiz:null };
    dragged = false;
  });
  app.addEventListener('pointermove', e => {
    if (!st || e.pointerId !== st.id) return;
    const dx = e.clientX - st.x, dy = e.clientY - st.y;
    if (st.horiz === null){ if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return; st.horiz = Math.abs(dx) > Math.abs(dy); if (st.horiz){ clearInterval(S.heroTimer); try { st.track.setPointerCapture(e.pointerId); } catch {} } }
    if (!st.horiz) return;
    dragged = true;
    const n = st.track.children.length;
    let off = dx; if ((S.heroIdx === 0 && dx > 0) || (S.heroIdx === n-1 && dx < 0)) off = dx / 3;
    st.track.style.transition = 'none';
    st.track.style.transform = `translateX(calc(-${S.heroIdx*100}% + ${off}px))`;
  });
  const end = e => {
    if (!st || e.pointerId !== st.id) return;
    const dx = e.clientX - st.x, n = st.track.children.length;
    if (st.horiz){
      let next = S.heroIdx;
      if (Math.abs(dx) > st.w * 0.15) next = dx < 0 ? Math.min(n-1, S.heroIdx + 1) : Math.max(0, S.heroIdx - 1);
      setHero(next); startHero();
    }
    st = null;
  };
  app.addEventListener('pointerup', end); app.addEventListener('pointercancel', end);
  // a drag must not count as a tap on the banner
  app.addEventListener('click', e => { if (dragged && e.target.closest('.hero')){ e.preventDefault(); e.stopPropagation(); dragged = false; } }, true);
  app.addEventListener('click', e => { const b = e.target.closest('[data-hero-step]'); if (b){ setHero(S.heroIdx + +b.dataset.heroStep); startHero(); } });
})();
/* banner: the image shows for a few seconds, then the preview video fades in */
const HERO_IMG_MS = 3500, HERO_STILL_MS = 7500;
const MUTE_ICON = on => on
  ? '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="m16 9.5 5 5m0-5-5 5"/></svg>'
  : '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4.5 4.5 0 0 1 0 6M18 6.5a8 8 0 0 1 0 11"/></svg>';
function heroVideoSrc(slide){
  if (!slide) return '';
  return (matchMedia('(max-width:640px)').matches && slide.dataset.videoM) || slide.dataset.video || slide.dataset.videoM || '';
}
function heroEls(){ return app.querySelectorAll('.hero-slide'); }
function heroNext(){ if (heroEls().length > 1){ setHero(S.heroIdx + 1); startHero(); } }
function stopHeroVideo(keep){
  clearTimeout(S.heroVidT);
  const v = S.heroVid;
  if (v && v !== keep){ try { v.pause(); } catch {} v.removeAttribute('src'); v.load?.(); v.remove(); S.heroVid = null; }
  app.querySelectorAll('.hero-slide.vid-on').forEach(s => { if (!keep || !s.contains(keep)) s.classList.remove('vid-on'); });
}
function startHero(){
  clearTimeout(S.heroTimer); clearInterval(S.heroTimer);
  const slide = heroEls()[S.heroIdx];
  if (!slide || matchMedia('(prefers-reduced-motion: reduce)').matches){ stopHeroVideo(); return; }
  const src = heroVideoSrc(slide);
  // a render while the video plays (live updates) keeps the same video going
  const v = S.heroVid;
  if (src && v && v.dataset.src === src && !v.ended && v.isConnected === false){
    stopHeroVideo(v);
    slide.querySelector('.hero-bg')?.appendChild(v);
    if (v.classList.contains('shown')) slide.classList.add('vid-on');
    v.play().catch(()=>{});
    syncMute(slide);
    return;
  }
  stopHeroVideo();
  if (!src){ if (heroEls().length > 1) S.heroTimer = setTimeout(heroNext, HERO_STILL_MS); return; }
  S.heroVidT = setTimeout(() => playHeroVideo(slide, src), HERO_IMG_MS);
}
function playHeroVideo(slide, src){
  if (!slide.isConnected || !slide.classList.contains('on')) return;
  const bg = slide.querySelector('.hero-bg'); if (!bg) return;
  const v = document.createElement('video');
  v.className = 'hero-vid'; v.dataset.src = src;
  v.muted = S.heroMuted !== false; v.defaultMuted = v.muted; v.playsInline = true;
  v.setAttribute('playsinline',''); v.setAttribute('muted',''); v.setAttribute('aria-hidden','true');
  v.preload = 'auto'; v.disablePictureInPicture = true;
  v.src = src;
  S.heroVid = v;
  let failed = false;
  const fail = () => {
    if (failed || S.heroVid !== v) return; failed = true;
    v.remove(); S.heroVid = null;
    const s = v.closest?.('.hero-slide') || slide; s.classList.remove('vid-on');
    if (heroEls().length > 1) S.heroTimer = setTimeout(heroNext, 4000);
  };
  v.addEventListener('playing', () => {
    if (S.heroVid !== v) return;
    v.classList.add('shown');
    const s = v.closest('.hero-slide'); if (s){ s.classList.add('vid-on'); syncMute(s); }
  });
  v.addEventListener('ended', () => {
    if (S.heroVid !== v) return;
    const s = v.closest('.hero-slide'); s?.classList.remove('vid-on'); v.classList.remove('shown');
    // fade back to the image, then move on (or replay later if it is the only banner)
    S.heroTimer = setTimeout(() => {
      if (S.heroVid !== v) return;
      if (heroEls().length > 1) heroNext();
      else { v.currentTime = 0; S.heroTimer = setTimeout(() => { if (S.heroVid === v) v.play().catch(fail); }, HERO_STILL_MS); }
    }, 1200);
  });
  v.addEventListener('error', fail);
  bg.appendChild(v);
  const p = v.play(); if (p && p.catch) p.catch(fail);
}
function syncMute(slide){
  const b = slide?.querySelector('.hero-mute'); if (!b) return;
  const muted = S.heroMuted !== false;
  b.innerHTML = MUTE_ICON(muted);
  b.setAttribute('aria-label', muted ? 'Activar sonido' : 'Silenciar');
  b.setAttribute('aria-pressed', String(!muted));
}
app.addEventListener('click', e => {
  const b = e.target.closest('[data-hero-mute]'); if (!b) return;
  e.preventDefault(); e.stopPropagation();
  S.heroMuted = !(S.heroMuted !== false);
  if (S.heroVid){ S.heroVid.muted = S.heroMuted; if (!S.heroMuted) S.heroVid.volume = 1; }
  syncMute(b.closest('.hero-slide'));
});
document.addEventListener('visibilitychange', () => {
  const v = S.heroVid; if (!v) return;
  if (document.hidden) v.pause(); else if (v.classList.contains('shown') && !v.ended) v.play().catch(()=>{});
});
function logoFallbacks(root){
  root.querySelectorAll('.hero-logo').forEach(img => {
    const fb = img.nextElementSibling;
    const obs = new MutationObserver(()=>{ if(!img.hidden && fb?.classList.contains('logo-fallback')) fb.hidden = true; });
    obs.observe(img,{attributes:true,attributeFilter:['hidden']});
  });
}

function mobileChips(active){
  const items = [['home','/','Todo'],['series','/series','Series'],['peliculas','/peliculas','Películas'],['mi-lista','/mi-lista','Mi lista']];
  return `<nav class="m-chips" aria-label="Secciones">${items.map(([k,h,l]) => `<a href="${h}" class="m-chip ${k===active?'on':''}" ${k===active?'aria-current="page"':''}>${l}</a>`).join('')}</nav>`;
}
function renderHome(){
  const pub = published();
  const newest = [...pub].sort((a,b)=>(b.createdAt||0)-(a.createdAt||0)).slice(0,20);
  const series = pub.filter(t=>t.type==='serie');
  const movies = pub.filter(t=>t.type!=='serie');
  const feat = pub.filter(t=>t.featured);
  const genres = {};
  pub.forEach(t => (t.genres||[]).forEach(g => (genres[g] ||= []).push(t)));
  // solo algunos géneros, que van rotando (con más chance los que le gustan)
  const prof = tasteProfile(), rg = rng('genres'), maxP = Math.max(1, ...[...prof.values()]);
  const genreRows = Object.entries(genres).filter(([, l]) => l.length >= 3)
    .map(([g, l]) => ({ g, l, s: (prof.get(g) || 0) / maxP + rg() * 1.2 })).sort((a, b) => b.s - a.s).slice(0, 3);
  return mobileChips('home') + renderHero() + `<main>
    ${continueRow()}
    ${top10Row()}
    ${recommendedRow()}
    ${listRow()}
    ${(S.frows||[]).map(featRow).join('')}
    ${row('Destacados', byTaste(feat, 'feat', prof))}
    ${row('Novedades', newest)}
    ${row('Series', byTaste(series, 'series', prof), '/series')}
    ${row('Películas', byTaste(movies, 'movies', prof), '/peliculas')}
    ${genreRows.map(({ g, l }) => row(g, byTaste(l, 'g-' + g, prof))).join('')}
  </main>`;
}
function renderBrowse(kind){
  const base = shuffled(published().filter(t => kind==='series' ? t.type==='serie' : t.type!=='serie'), 'browse-' + kind);
  const genres = [...new Set(base.flatMap(t=>t.genres||[]))].sort();
  const list = S.genre ? base.filter(t => (t.genres||[]).includes(S.genre)) : base;
  return mobileChips(kind) + `<main><div class="page-head"><h1>${kind==='series'?'Series':'Películas'}</h1><p>${list.length} título${list.length===1?'':'s'} en la bóveda</p></div>
    ${genres.length ? `<div class="chips"><button class="chip" data-genre="" aria-pressed="${!S.genre}">Todo</button>${genres.map(g=>`<button class="chip" data-genre="${esc(g)}" aria-pressed="${S.genre===g}">${esc(g)}</button>`).join('')}</div>` : ''}
    ${list.length ? `<div class="grid">${list.map(card).join('')}</div>` : `<p class="empty-note">Todavía no hay ${kind==='series'?'series':'películas'} publicadas.</p>`}
  </main>`;
}
function renderSearch(){
  const q = S.q.toLowerCase().trim();
  const list = published().filter(t => [t.title,t.tagline,t.description,t.director,...(t.genres||[]),...(t.tags||[]),...(t.cast||[]).map(c=>c.name+' '+(c.role||''))]
    .join(' ').toLowerCase().includes(q));
  return `<main><div class="page-head"><h1>“${esc(S.q)}”</h1><p>${list.length} resultado${list.length===1?'':'s'}</p></div>
    ${list.length ? `<div class="grid">${list.map(card).join('')}</div>` : '<p class="empty-note">No encontramos nada con esa búsqueda. Probá con el nombre de un participante o una etiqueta.</p>'}</main>`;
}

/* ---------- detail ---------- */
let detailSeason = 0;
function showDetail(id, seasonIdx){
  const t = byId(id); if (!t) return;
  S._detailId = id;
  detailSeason = seasonIdx ?? 0;
  const seasons = (t.seasons||[]).slice().sort((a,b)=>(a.number||0)-(b.number||0));
  if (detailSeason >= seasons.length) detailSeason = 0;
  const s = seasons[detailSeason];
  const link = watchLink(t);
  const similar = published().filter(x => x.id!==t.id && (x.genres||[]).some(g => (t.genres||[]).includes(g))).slice(0,6);
  const initials = n => String(n||'?').split(/\s+/).map(w=>w[0]).slice(0,2).join('').toUpperCase();
  overlayRoot.innerHTML = `<div class="overlay" data-close-bg><article class="sheet" role="dialog" aria-modal="true" aria-label="${esc(t.title)}">
    <div class="sheet-hero wide" ${t.backdrop||t.poster?`data-src="${esc(t.backdrop||t.poster)}"`:''}>${genBlock(t)}
      <button class="sheet-close" data-close aria-label="Cerrar">✕</button>
      <div class="sheet-title">
        <span class="eyebrow">${t.type==='serie'?'Serie':'Película'}${isSoon(t)?' · Próximamente':''}</span>
        ${t.logo ? `<img class="hero-logo" alt="${esc(t.title)}" data-img="${esc(t.logo)}" hidden><h2 class="logo-fallback" hidden>${esc(t.title)}</h2>` : `<h2>${esc(t.title)}</h2>`}
        <div class="meta">${metaLine(t)}</div>
        <div class="hero-actions">
          ${link ? `<a class="btn btn-primary" href="${esc(link)}" target="_blank" rel="noopener">▶ ${t.type==='serie'?'Ver T'+(seasons[0]?.number||1)+' E1':'Ver ahora'}</a>` : isSoon(t) ? `<span class="btn btn-soon">${esc(soonLabel(t))}</span>` : ''}
          ${safeUrl(t.trailer) ? `<button class="btn btn-ghost" data-trailer="${esc(t.id)}">Tráiler</button>` : ''}
        </div>
      </div>
    </div>
    <div class="sheet-body">
      <div class="sheet-cols">
        <div style="display:flex;flex-direction:column;gap:12px">
          ${t.tagline ? `<div class="tagline">${esc(t.tagline)}</div>` : ''}
          ${t.description ? `<p class="desc">${esc(t.description)}</p>` : ''}
          ${(t.tags||[]).length ? `<div class="tags">${t.tags.map(x=>`<span class="tag">#${esc(x)}</span>`).join('')}</div>` : ''}
        </div>
        <dl class="facts">
          ${(t.cast||[]).length ? `<div><dt>Elenco</dt><dd>${esc(t.cast.slice(0,5).map(c=>c.name).join(', '))}</dd></div>` : ''}
          ${t.director ? `<div><dt>${t.type==='serie'?'Creación':'Dirección'}</dt><dd>${esc(t.director)}</dd></div>` : ''}
          ${(t.genres||[]).length ? `<div><dt>Géneros</dt><dd>${esc(t.genres.join(', '))}</dd></div>` : ''}
          ${t.type==='serie' ? `<div><dt>Episodios</dt><dd>${epCount(t)}</dd></div>` : ''}
        </dl>
      </div>
      ${t.type==='serie' && seasons.length ? `<section style="display:flex;flex-direction:column;gap:10px">
        <div class="seasons-bar"><h3 class="section-h">Episodios</h3>
          ${seasons.length>1 ? `<select class="select" id="seasonSel" aria-label="Temporada">${seasons.map((x,i)=>`<option value="${i}" ${i===detailSeason?'selected':''}>Temporada ${esc(x.number)}${x.title?' · '+esc(x.title):''}</option>`).join('')}</select>` : `<span class="meta">Temporada ${esc(s.number)}${s.title?' · '+esc(s.title):''}</span>`}
        </div>
        <div class="eps">${(s.episodes||[]).slice().sort((a,b)=>(a.number||0)-(b.number||0)).map(e => {
          const u = isSoon(t) ? '' : safeUrl(e.url); const tag = u ? 'a' : 'div';
          return `<${tag} class="ep" ${u?`data-watch href="${esc(u)}" target="_blank" rel="noopener"`:''}>
            <span class="ep-n">${esc(e.number)}</span>
            <div class="ep-thumb" ${e.thumb?`data-src="${esc(e.thumb)}"`:''}>${genBlock({title:e.title||'Episodio '+e.number,id:t.id}, 'T'+s.number+' · E'+e.number, t.logo)}</div>
            <div class="ep-info"><strong>${esc(e.title || 'Episodio '+e.number)}<span>${esc(e.duration||'')}</span></strong>${e.description?`<p>${esc(e.description)}</p>`:''}</div>
          </${tag}>`; }).join('') || '<p class="empty-note" style="padding:12px 0">Esta temporada todavía no tiene episodios.</p>'}</div>
      </section>` : ''}
      ${(t.cast||[]).length ? `<section style="display:flex;flex-direction:column;gap:12px"><h3 class="section-h">Reparto</h3>
        <div class="cast">${t.cast.map(c=>`<div class="person"><div class="avatar" ${c.photo?`data-src="${esc(c.photo)}"`:''}>${c.photo?'':esc(initials(c.name))}</div><div>${esc(c.name)}${c.role?`<small>${esc(c.role)}</small>`:''}</div></div>`).join('')}</div></section>` : ''}
      ${similar.length ? `<section style="display:flex;flex-direction:column;gap:12px"><h3 class="section-h">Más como esto</h3><div class="more">${similar.map(card).join('')}</div></section>` : ''}
    </div></article></div>`;
  document.body.style.overflow = 'hidden';
  hydrate(overlayRoot); logoFallbacks(overlayRoot);
  // avatars with photos: clear initials once loaded
  overlayRoot.querySelector('.sheet-close')?.focus();
}
function closeDetail(){ overlayRoot.innerHTML=''; document.body.style.overflow=''; }
overlayRoot.addEventListener('click', e => {
  if (e.target.closest('[data-close]') || e.target.hasAttribute('data-close-bg')) return closeDetail();
  const o = e.target.closest('[data-open]'); if (o){ showDetail(o.dataset.open); overlayRoot.querySelector('.overlay')?.scrollTo(0,0); }
});
overlayRoot.addEventListener('change', e => {
  if (e.target.id==='seasonSel'){ const y = overlayRoot.querySelector('.overlay')?.scrollTop||0; showDetail(S._detailId, +e.target.value); const ov=overlayRoot.querySelector('.overlay'); if(ov) ov.scrollTop=y; overlayRoot.querySelector('#seasonSel')?.focus(); }
});
document.addEventListener('keydown', e => { if (e.key==='Escape' && overlayRoot.firstChild) closeDetail(); });

/* ---------- admin ---------- */
const GENRE_SUGGEST = ['Reality','Competencia','Docureality','Citas','Supervivencia','Cocina','Talento','Convivencia','Drama','Comedia','Documental','Thriller','Romance','Aventura'];
const RATINGS = ['ATP','+7','+13','+16','+18'];
function blankTitle(type){
  return { id:uid('t'), type:type||'serie', title:'', tagline:'', description:'', year:String(new Date().getFullYear()), rating:'+13', duration:'',
    genres:[], tags:[], cast:[], director:'', poster:'', backdrop:'', logo:'', bannerImage:'', availability:'disponible', releaseDate:'', url:'', trailer:'', featured:false, status:'borrador',
    seasons: type==='pelicula' ? [] : [{ id:uid('s'), number:1, title:'', episodes:[] }], _new:true };
}
function renderLock(){
  if (S.session) return `<div class="lock"><div class="dial" style="width:64px;height:64px" aria-hidden="true"></div><h1>Acceso restringido</h1>
    <p>La cuenta ${esc(S.session.user?.email||'')} no tiene permiso para usar el panel.</p><button class="btn btn-line" data-logout>Cerrar sesión</button></div>`;
  const posters = published().map(t => t.poster).filter(Boolean);
  const wall = posters.length ? Array.from({ length: 24 }, (_, k) => posters[k % posters.length]) : [];
  const ICON_MAIL = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="m4 7 8 6 8-6"/></svg>';
  const ICON_LOCK = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><rect x="4.5" y="10.5" width="15" height="10" rx="2.5"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/></svg>';
  const ICON_EYE = '<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/></svg>';
  return `<section class="login-screen">
    ${wall.length ? `<div class="login-wall" aria-hidden="true">${wall.map(u => `<i style="background-image:url('${esc(u)}')"></i>`).join('')}</div>` : ''}
    <form class="login-card" id="loginForm" novalidate>
      <div class="login-brand">${S.site.logo ? `<img src="${esc(S.site.logo)}" alt="REALITY VAULT">` : '<b>REALITY <span>VAULT</span></b>'}</div>
      <div class="login-head"><span class="login-eyebrow">Acceso privado</span><h1>Panel de control</h1><p>Entrá con tu cuenta de administrador para manejar el catálogo.</p></div>
      <label class="login-field"><span class="lf-label">Email</span>
        <span class="lf-box">${ICON_MAIL}<input id="login-email" type="email" autocomplete="username" inputmode="email" placeholder="tu@email.com" required></span></label>
      <label class="login-field"><span class="lf-label">Contraseña</span>
        <span class="lf-box">${ICON_LOCK}<input id="login-pass" type="password" autocomplete="current-password" placeholder="••••••••" required><button type="button" class="lf-eye" data-toggle-pass aria-label="Mostrar contraseña" title="Mostrar contraseña">${ICON_EYE}</button></span></label>
      <p id="loginMsg" class="login-msg" role="alert"></p>
      <button class="login-btn" type="submit">Entrar al panel</button>
      <a class="login-back" href="/">← Volver al sitio</a>
    </form>
  </section>`;
}
function renderAdmin(){
  if (!S.owner) return renderLock();
  const tab = S.adminTab;
  const sections = [
    ['catalogo','Catálogo', S.titles.length],
    ['banners','Banners', S.banners.length],
    ['etiquetas','Géneros y etiquetas', null],
    ['filas','Filas destacadas', (S.frows||[]).length],
    ['sitio','Sitio', null],
  ];
  return `<div class="admin admin-light">
    <div class="adm-top">
      <div class="adm-brand">${S.site.logo ? `<img src="${esc(S.site.logo)}" alt="REALITY VAULT">` : 'REALITY <span>VAULT</span>'}</div>
      <div class="spacer"></div>
      <nav class="adm-nav">${sections.map(([id,label,count])=>`<button data-tab="${id}" aria-pressed="${tab===id}">${esc(label)}${count!=null?`<span>${count}</span>`:''}</button>`).join('')}</nav>
      <div class="adm-top-actions"><a class="adm-ghost" href="/">← Volver al sitio</a><button class="adm-ghost" data-logout>Cerrar sesión</button></div>
    </div>
    <section class="panel" id="panel">${tab==='catalogo' ? (S.editing && !S.quick ? renderEditor() : renderCatalog()) : tab==='banners' ? renderBanners() : tab==='sitio' ? renderSite() : tab==='filas' ? renderFrowsAdmin() : renderTaxonomy()}</section></div>`;
}
function catalogList(){
  const f = S.adminFilter.toLowerCase();
  return S.titles.filter(t => (!f || (t.title||'').toLowerCase().includes(f)) && (S.catalogFilter==='todo' || t.type===S.catalogFilter)).sort((a,b)=>(b.code||0)-(a.code||0));
}
function catalogGridHtml(){
  const list = catalogList();
  return list.length ? `<div class="admin-grid">${list.map((t,i)=>`<div class="admin-tile" style="--i:${Math.min(i,14)}">
        <button class="admin-poster" data-edit="${esc(t.id)}" aria-label="Editar ${esc(t.title||'Sin título')}"><div class="poster" ${t.poster?`data-src="${esc(t.poster)}"`:''}>${genBlock(t)}</div>
          <span class="admin-badges">${t.status==='borrador'?'<span class="pill draft">Borrador</span>':''}${t.featured?'<span class="pill feat">Destacado</span>':''}${isSoon(t)?'<span class="pill draft">Próximamente</span>':''}</span></button>
        <strong class="admin-name">${esc(t.title||'Sin título')}</strong>
        <span class="admin-sub">${esc([t.type==='serie'?'Serie':'Película', t.year, t.type==='serie' ? `${(t.seasons||[]).length} temp. · ${epCount(t)} ep.` : t.duration].filter(Boolean).join(' · '))}</span>
        <div class="actions admin-actions" data-row="${esc(t.id)}"><button class="btn btn-line btn-sm" data-edit="${esc(t.id)}">Editar</button><button class="btn btn-danger btn-sm" data-del="${esc(t.id)}">Eliminar</button></div>
      </div>`).join('')}</div>` : `<p class="empty-note" style="padding:20px 0">${S.titles.length ? 'Ningún título coincide con el filtro.' : 'El catálogo está vacío. Empezá con “Nuevo”.'}</p>`;
}
function catPicker(){
  if (!S.catalogPicker && !S.quick) return '';
  const withP = shuffled(S.titles.filter(t=>t.poster), 'picker');
  let wall = '';
  if (withP.length){
    const pool = []; while (pool.length < 21) pool.push(...withP);
    const col = c => { const ps = [0,1,2].map(r => `<div class="cp-poster" data-src="${esc(pool[c*3+r].poster)}"></div>`).join(''); return `<div class="cp-col${c%2?' up':''}">${ps}${ps}</div>`; };
    wall = `<div class="cp-wall" aria-hidden="true">${[0,1,2,3,4,5,6].map(col).join('')}</div>`;
  }
  const art = type => { const t = shuffled(S.titles.filter(x=>(x.type==='serie')===(type==='serie') && (x.backdrop||x.bannerImage||x.poster)), 'pick-'+type)[0]; return t ? esc(t.backdrop||t.bannerImage||t.poster) : ''; };
  const fall = { serie:'radial-gradient(circle at 78% 16%,hsla(345,90%,94%,.95),transparent 46%),linear-gradient(160deg,hsl(315,82%,86%),hsl(353,70%,78%))', pelicula:'radial-gradient(circle at 78% 16%,hsla(228,90%,94%,.95),transparent 46%),linear-gradient(160deg,hsl(198,82%,86%),hsl(236,70%,78%))' };
  const choice = (type, label, sub) => { const img = art(type); return `<button class="cat-choice" data-new="${type}"><span class="cc-bg" ${img?`data-src="${img}"`:`style="background:${fall[type]}"`}></span><span class="cc-scrim"></span><span><span class="cc-lbl">${label}</span><span class="cc-sub">${sub}</span></span></button>`; };
  const E = S.editing;
  const Q = S.qt;
  const qtPane = () => {
    const isS = E.type==='serie';
    if (Q.pick){ const p = Q.pick; return `<div class="cq-box cq-wide"><div class="cq-top"><span class="cq-chip">Importar de TMDB</span><button type="button" class="cq-x" data-picker-close aria-label="Cerrar">✕</button></div>
      <div class="cq-body cq-prev"><div class="cq-pimg">${p.poster?`<img src="${esc(p.poster)}" alt="">`:'<span>Sin póster</span>'}</div>
      <div class="cq-pinfo"><h3>${esc(p.title)}</h3><div class="cq-meta">${esc([isS?'Serie':'Película', p.year, p.rating].filter(Boolean).join(' · '))}</div><p>${esc(p.overview||'Sin descripción en TMDB.')}</p>
        ${isS && (p.seasons||[]).length ? `<div class="cq-seas"><div class="cq-sh"><strong>Temporadas a importar</strong><span><button type="button" data-qt-seasall>Todas</button> · <button type="button" data-qt-seasnone>Ninguna</button></span></div><div class="cq-sc">${p.seasons.map(x=>`<button type="button" class="cq-s" data-qt-season="${x.season_number}" aria-pressed="${Q.sel.includes(x.season_number)}">${x.season_number===0?'Especiales':'T'+x.season_number}<small>${x.episode_count||0} ep.</small></button>`).join('')}</div></div>` : ''}</div></div>
      <div class="cq-foot"><button type="button" class="cq-link" data-qt-back>← Elegir otra</button><span class="spacer"></span><button type="button" class="btn btn-line" data-picker-close>Cancelar</button><button type="button" class="btn-new" data-qt-confirm="${p.id}">Importar y seguir</button></div></div>`; }
    return `<div class="cq-box cq-wide"><div class="cq-top"><span class="cq-chip">Importar de TMDB</span><button type="button" class="cq-x" data-picker-close aria-label="Cerrar">✕</button></div>
      <form class="cq-search" data-qt-form><input class="input" id="qt-q" value="${esc(Q.q||'')}" placeholder="Nombre de la ${isS?'serie':'película'}" autocomplete="off"><button class="btn-new" type="submit">Buscar</button></form>
      <div class="cq-status">${esc(Q.status||'')}</div>
      <div class="cq-results">${(Q.results||[]).map(r=>`<button type="button" class="cq-res" data-qt-pick="${r.id}"><span class="cq-rp" ${r.poster_path?`style="background-image:url('${tmdbImg(r.poster_path,'w185')}')"`:''}></span><strong>${esc(r.name||r.title)}</strong><small>${esc((r.first_air_date||r.release_date||'').slice(0,4))}</small></button>`).join('')}</div>
      <div class="cq-foot"><button type="button" class="cq-link" data-qt-back>← Volver</button><span class="spacer"></span><button type="button" class="btn btn-line" data-picker-close>Cancelar</button></div></div>`;
  };
  const inner = S.quick && E && Q ? qtPane() : S.quick && E ? `<form class="cq-box" data-quick-form>
        <div class="cq-top"><span class="cq-chip">${E.type==='serie'?'Nueva serie':'Nueva película'}</span><button type="button" class="cq-x" data-picker-close aria-label="Cerrar">✕</button></div>
        <div class="cq-body">
          <div class="cq-poster"><span>Póster</span>${slot('poster','poster',E.poster,'r23','Subir póster')}</div>
          <div class="cq-fields">
            <label class="field"><span>Título</span><input class="input" id="f-title" data-k="title" value="${esc(E.title)}" placeholder="${E.type==='serie'?'Ej: La Isla de los Secretos':'Ej: Detrás del Final'}" autocomplete="off"></label>
            <div class="cq-row"><label class="field"><span>Año</span><input class="input" data-k="year" inputmode="numeric" value="${esc(E.year)}"></label>
              <label class="field"><span>Clasificación</span><select class="select" data-k="rating">${RATINGS.map(r=>`<option ${E.rating===r?'selected':''}>${r}</option>`).join('')}</select></label></div>
            <label class="field"><span>Descripción corta</span><textarea class="textarea" data-k="description" placeholder="De qué trata, en una o dos líneas"></textarea></label>
          </div>
        </div>
        <div class="cq-foot"><button type="button" class="cq-link" data-quick-tmdb>Importar de TMDB</button><span class="spacer"></span><button type="button" class="btn btn-line" data-picker-close>Cancelar</button><button type="submit" class="btn-new">Crear y seguir</button></div>
      </form>` : `<div class="cat-picker-box">
      <h2>¿Qué querés crear?</h2><p>Elegí el tipo de título para empezar a cargarlo</p>
      <div class="cat-choices">${choice('serie','Serie','Temporadas y episodios')}${choice('pelicula','Película','Título único')}</div></div>`;
  return `<div class="cat-picker"><button class="cat-picker-scrim" data-picker-close aria-label="Cerrar" tabindex="-1"></button>${wall}<div class="cp-veil"></div>${S.quick?'':'<button class="cat-picker-close" data-picker-close aria-label="Cerrar">✕</button>'}${inner}</div>`;
}
function renderCatalog(){
  const ser = S.titles.filter(t=>t.type==='serie'), mov = S.titles.filter(t=>t.type!=='serie');
  return `<div class="cat-tools">
      <div class="pillseg"><span class="pillseg-pill"></span>
        <button data-catfilter="todo" aria-pressed="${S.catalogFilter==='todo'}">Todo</button>
        <button data-catfilter="serie" aria-pressed="${S.catalogFilter==='serie'}">Series</button>
        <button data-catfilter="pelicula" aria-pressed="${S.catalogFilter==='pelicula'}">Películas</button>
      </div>
      <label class="cat-search"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg><input id="adminFilter" placeholder="Filtrar por título" value="${esc(S.adminFilter)}"></label>
      <div class="spacer"></div>
      <button class="btn-new" data-nuevo-open><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3"><path d="M12 5v14M5 12h14"/></svg>Nuevo</button>
    </div>
    <div class="cat-stats"><div><b>${ser.length}</b>series</div><div><b>${mov.length}</b>películas</div><div><b>${ser.reduce((n,t)=>n+epCount(t),0)}</b>episodios</div><div><b>${S.titles.filter(t=>t.status==='borrador').length}</b>borradores</div>${S.titles.some(t=>!(t.description||'').trim())?`<button class="adm-ghost" data-fill-desc>Completar ${S.titles.filter(t=>!(t.description||'').trim()).length} descripciones</button>`:''}</div>
    <div id="catGrid">${catalogGridHtml()}</div>
    ${catPicker()}`;
}
function slot(kind, path, value, cls, label){
  return `<div class="slot ${cls} ${value?'filled':''}" role="button" tabindex="0" data-slot="${kind}" data-path="${esc(path)}" ${value?`data-src="${esc(value)}"`:''} aria-label="${esc(label)}">
    <span class="slot-empty">${esc(label)}</span><button type="button" class="slot-x" data-clear="${esc(path)}" aria-label="Quitar imagen">✕</button><button type="button" class="slot-url" data-slot-url="${esc(path)}" aria-label="Usar un enlace de imagen" title="Pegar enlace de imagen">🔗</button></div>`;
}
function chipField(key, label, list, suggest){
  const sugg = (suggest||[]).filter(x=>!list.includes(x)).slice(0,14);
  return `<div class="field span2"><span>${label}</span>
    <div class="chipbox" data-chips="${key}">${list.map((x,i)=>`<span class="tag">${esc(x)}<button data-chip-x="${key}" data-i="${i}" aria-label="Quitar ${esc(x)}">✕</button></span>`).join('')}
      <input id="chip-${key}" data-chip-in="${key}" placeholder="Escribí y presioná Enter"></div>
    ${sugg.length?`<div class="suggest">${sugg.map(x=>`<button data-chip-add="${key}" data-v="${esc(x)}">+ ${esc(x)}</button>`).join('')}</div>`:''}</div>`;
}
function renderEditor(){
  const E = S.editing; const isSerie = E.type==='serie';
  const allTags = [...new Set(S.titles.flatMap(t=>t.tags||[]))];
  const allGenres = [...new Set([...S.titles.flatMap(t=>t.genres||[]), ...GENRE_SUGGEST])];
  const etabs = [['info','Información'],['imagenes','Imágenes'],['contenido','Contenido'],['extra','Extra']];
  const et = S.editorTab || 'info';
  return `<div class="panel-head"><button class="btn btn-line btn-sm" data-back>← Catálogo</button><h1>${E._new?'Nuevo título':esc(E.title||'Editar')}</h1><button class="btn btn-primary" data-tmdb-open>Importar de TMDB</button></div>
  ${S.tmdb?.open ? renderTmdbBox() : ''}
  <div class="pillseg editor-tabs"><span class="pillseg-pill"></span>${etabs.map(([id,label])=>`<button data-etab="${id}" aria-pressed="${et===id}">${label}</button>`).join('')}</div>
  <div class="editor">
    <div data-epanel="info" ${et==='info'?'':'hidden'}>
    <div class="fs"><h3>Información</h3>
      <div class="seg" role="group" aria-label="Tipo"><button data-type="serie" aria-pressed="${isSerie}">Serie</button><button data-type="pelicula" aria-pressed="${!isSerie}">Película</button></div>
      <div class="fgrid">
        <label class="field span2"><span>Título</span><input class="input" id="f-title" data-k="title" value="${esc(E.title)}" placeholder="Ej: La Isla de los Secretos"></label>
        <label class="field span2"><span>Frase corta</span><input class="input" id="f-tagline" data-k="tagline" value="${esc(E.tagline)}" placeholder="Una línea que aparece bajo el título"></label>
        <label class="field span2"><span>Descripción</span><textarea class="textarea" id="f-desc" data-k="description">${esc(E.description)}</textarea></label>
        <label class="field"><span>Año</span><input class="input" id="f-year" data-k="year" inputmode="numeric" value="${esc(E.year)}"></label>
        ${isSerie?`<label class="field"><span>Año de fin (opcional)</span><input class="input" id="f-yearend" data-k="yearEnd" inputmode="numeric" value="${esc(E.yearEnd||'')}" placeholder="Si terminó, ej: 2007"></label>`:''}
        <label class="field"><span>Clasificación</span><select class="select" id="f-rating" data-k="rating">${RATINGS.map(r=>`<option ${E.rating===r?'selected':''}>${r}</option>`).join('')}</select></label>
        ${!isSerie?`<label class="field"><span>Duración</span><input class="input" id="f-dur" data-k="duration" value="${esc(E.duration)}" placeholder="1 h 48 min"></label>`:''}
        <label class="field"><span>${isSerie?'Creación':'Dirección'}</span><input class="input" id="f-dir" data-k="director" value="${esc(E.director)}"></label>
      </div>
    </div>
    <div class="fs"><h3>Géneros y etiquetas</h3><div class="fgrid">
      ${chipField('genres','Géneros (arman las filas del inicio)',E.genres,allGenres)}
      ${chipField('tags','Etiquetas (para búsqueda y ficha)',E.tags,allTags)}
    </div></div>
    </div>
    <div data-epanel="imagenes" ${et==='imagenes'?'':'hidden'}>
    <div class="fs"><h3>Imágenes <small>tocá para subir un archivo o usá 🔗 para pegar un enlace</small></h3>
      <div class="imgs">
        <div class="field"><span>Póster</span>${slot('poster','poster',E.poster,'r23','Subir póster')}<span class="hint"><b>1000 × 1500 px</b> (vertical 2:3)</span></div>
        <div class="field"><span>Fondo de la ficha</span>${slot('backdrop','backdrop',E.backdrop,'r169','Subir fondo de la ficha')}<span class="hint"><b>1920 × 1080 px</b> (16:9). Se ve detrás del logo al abrir el título. Dejá libre el lado izquierdo, ahí va el texto.</span></div>
        <div class="field"><span>Logo del título</span>${slot('logo','logo',E.logo,'r31 contain','Subir logo')}<span class="hint"><b>1000 × 400 px</b> aprox., PNG con fondo transparente y recortado al borde. Reemplaza al título escrito.</span></div>
        <div class="field"><span>Miniatura horizontal</span>${slot('thumb','thumbWide',E.thumbWide,'r169','Subir miniatura')}<span class="hint"><b>1280 × 720 px</b> (16:9). Se muestra tal cual en Mi lista y Seguir viendo. Si no la cargás, se usa el fondo del título.</span></div>
        <div class="field span3"><span>Imagen del banner principal</span>${slot('banner','bannerImage',E.bannerImage,'r31','Subir imagen para el banner')}<span class="hint"><b>2400 × 800 px</b> (3:1). Dejá lo importante en el centro y la derecha: a la izquierda va el logo y el texto, y en pantallas angostas se recortan un poco los costados. La que se ve en la portada cuando este título está en el banner. Distinta del fondo de la ficha.</span></div>
      </div>
      <h3 style="margin-top:8px">Versión celular <small>opcional · si no las cargás, se usan las de arriba</small></h3>
      <div class="imgs imgs-m">
        <div class="field"><span>Banner principal · celular</span>${slot('mobilebanner','bannerImageMobile',E.bannerImageMobile,'r45','Subir banner para celular')}<span class="hint"><b>1080 × 1350 px</b> (vertical 4:5). Ocupa toda la portada del celular; el logo y los botones van abajo al centro.</span></div>
        <div class="field"><span>Fondo de la ficha · celular</span>${slot('mobile','backdropMobile',E.backdropMobile,'r1610','Subir fondo para celular')}<span class="hint"><b>1280 × 800 px</b> (16:10). Se muestra arriba al abrir el título en el celular.</span></div>
      </div>
      <h3 style="margin-top:8px">Versión TV <small>opcional · solo para la app de la tele · si no las cargás, se usan las de arriba</small></h3>
      <div class="imgs imgs-m">
        <div class="field"><span>Banner principal · TV</span>${slot('tv','bannerImageTv',E.bannerImageTv,'r169','Subir banner para TV')}<span class="hint"><b>1920 × 1080 px</b> (16:9). Ocupa toda la pantalla del inicio en la tele. Dejá lo importante a la derecha: abajo a la izquierda van el logo, el texto y los botones.</span></div>
        <div class="field"><span>Fondo de la ficha · TV</span>${slot('tv','backdropTv',E.backdropTv,'r169','Subir fondo para TV')}<span class="hint"><b>1920 × 1080 px</b> (16:9). Se ve a pantalla completa al abrir el título en la tele. También de ahí sale el color del fondo de los capítulos.</span></div>
      </div>
    </div>
    </div>
    <div data-epanel="contenido" ${et==='contenido'?'':'hidden'}>
    <div class="fs"><h3>Reparto <small>${E.cast.length} persona${E.cast.length===1?'':'s'}</small></h3>
      <div class="cast-ed">${E.cast.map((c,i)=>`<div class="cast-row">
        ${slot('face','cast.'+i+'.photo',c.photo,'','+')}
        <input class="input" id="cast-n-${i}" data-cast="${i}" data-f="name" value="${esc(c.name)}" placeholder="Nombre" aria-label="Nombre">
        <input class="input" id="cast-r-${i}" data-cast="${i}" data-f="role" value="${esc(c.role)}" placeholder="Rol (participante, conductora…)" aria-label="Rol">
        <button class="iconbtn" data-cast-x="${i}" aria-label="Quitar">✕</button></div>`).join('')}
      </div><div><button class="btn btn-line btn-sm" data-cast-add>+ Agregar persona</button></div>
    </div>
    ${isSerie ? renderSeasonsEd(E) : `<div class="fs"><h3>Reproducción</h3><div class="fgrid">
      <label class="field span2"><span>Enlace para ver la película</span><input class="input" id="f-url" data-k="url" value="${esc(E.url)}" placeholder="https://…"><span class="hint">Se reproduce dentro del sitio. Funciona con YouTube, Vimeo, Google Drive, Dailymotion, OK.ru, Internet Archive, archivos .mp4 o .m3u8 y servicios como StreamHG: ahí copiá el <b>Embed code</b> completo y pegalo acá (el sitio se queda solo con el enlace).</span></label>
    </div></div>`}
    <div class="fs"><h3>Video del banner principal</h3>
      <p class="hint" style="margin:0 0 12px">Cuando esta serie aparece en el banner del inicio, se ve la imagen unos segundos y después arranca este video sin sonido (con un botón para activarlo). Al terminar, pasa al siguiente banner. Tiene que ser un archivo de video: los enlaces de YouTube no sirven acá.</p>
      <div class="vid-grid">
        ${vidField(E, 'previewVideo', 'Computadora', '1920 × 1080 px (horizontal 16:9)', 'Se recorta arriba y abajo para llenar el banner: dejá lo importante en la franja del medio.')}
        ${vidField(E, 'previewVideoMobile', 'Celular (opcional)', '1080 × 1350 px (vertical 4:5)', 'Si no cargás uno, en el celular se usa el de computadora recortado al centro.')}
      </div>
      <span class="hint">Formato MP4 (H.264) o WebM · entre 15 y 40 segundos · hasta 50 MB (ideal menos de 20 MB para que cargue rápido).</span>
    </div>
    <div class="fs"><h3>Disponibilidad</h3>
      <div class="seg" role="group" aria-label="Disponibilidad"><button data-avail="disponible" aria-pressed="${!isSoon(E)}">Disponible para ver</button><button data-avail="proximamente" aria-pressed="${isSoon(E)}">Próximamente</button></div>
      ${isSoon(E) ? `<label class="field" style="max-width:320px"><span>Fecha de estreno (opcional)</span><input class="input" id="f-release" data-k="releaseDate" value="${esc(E.releaseDate||'')}" placeholder="15 de noviembre"><span class="hint">Se muestra junto a “Próximamente”. Los enlaces para ver quedan ocultos hasta que lo cambies a disponible.</span></label>`
        : `<span class="hint">El botón “Ver ahora” abre ${E.type==='serie'?'el enlace del primer capítulo que lo tenga':'el enlace de la película'}.</span>`}
    </div>
    </div>
    <div data-epanel="extra" ${et==='extra'?'':'hidden'}>
    <div class="fs"><h3>Extras y publicación</h3><div class="fgrid">
      <label class="field span2"><span>Enlace al tráiler</span><input class="input" id="f-trailer" data-k="trailer" value="${esc(E.trailer)}" placeholder="https://www.youtube.com/watch?v=…"><span class="hint">Aparece el ícono de claqueta al lado de los botones y el tráiler se abre en una ventana encima de la página. Sirve YouTube, Vimeo, Google Drive o un .mp4.</span></label>
      <label class="check"><input type="checkbox" id="f-feat" data-k="featured" ${E.featured?'checked':''}> Mostrar en Destacados</label>
      <label class="check"><input type="checkbox" id="f-pub" data-k="published" ${E.status!=='borrador'?'checked':''}> Publicado (visible en el sitio)</label>
    </div></div>
    </div>
    <div class="savebar"><span class="status" id="saveStatus">${S.dirty?'Cambios sin guardar':(E._new?'Nuevo, sin guardar':'Todo guardado')}</span>
      ${E._new?'':`<span data-row="${esc(E.id)}"><button class="btn btn-danger" data-del="${esc(E.id)}">Eliminar</button></span>`}
      <button class="btn btn-line" data-back>Cancelar</button><button class="btn btn-primary" data-save>Guardar</button></div>
  </div>`;
}
function vidField(E, key, label, size, note){
  const v = safeUrl(E[key]);
  return `<div class="vid-field">
    <div class="vid-h"><strong>${esc(label)}</strong><span class="vid-size">${esc(size)}</span></div>
    <div class="vid-box ${key==='previewVideoMobile'?'m':''}">${v ? `<video src="${esc(v)}" muted loop playsinline autoplay preload="metadata"></video>` : `<span>Sin video</span>`}</div>
    <div class="vid-actions">
      <button class="btn btn-line btn-sm" data-vidup="${key}">${v ? 'Cambiar video' : 'Subir video'}</button>
      ${v ? `<button class="btn btn-line btn-sm" data-vidclear="${key}">Quitar</button>` : ''}
    </div>
    <label class="field"><span>o pegá un enlace directo (.mp4 / .webm)</span><input class="input" data-k="${key}" value="${esc(E[key]||'')}" placeholder="https://…/video.mp4"></label>
    <span class="hint">${esc(note)}</span>
  </div>`;
}
const VIDEO_MAX_MB = 50;
async function uploadVideo(file){
  if (!/^video\/(mp4|webm|quicktime)$/.test(file.type) && !/\.(mp4|webm|mov|m4v)$/i.test(file.name)) throw new Error('Elegí un archivo MP4 o WebM.');
  if (file.size > VIDEO_MAX_MB * 1024 * 1024) throw new Error(`El video pesa ${(file.size/1048576).toFixed(0)} MB. El máximo es ${VIDEO_MAX_MB} MB: achicalo o recortalo antes de subirlo.`);
  const ext = (file.name.match(/\.(mp4|webm|mov|m4v)$/i)?.[1] || (file.type.split('/')[1]) || 'mp4').toLowerCase().replace('quicktime','mov');
  const path = `video/${uid('v')}.${ext}`;
  const { error } = await S.sb.storage.from(BUCKET).upload(path, file, { contentType: file.type || 'video/mp4', cacheControl: '31536000', upsert:false });
  if (error) throw new Error('No se pudo subir el video: ' + error.message);
  return S.sb.storage.from(BUCKET).getPublicUrl(path).data.publicUrl;
}
app.addEventListener('click', async e => {
  const up = e.target.closest('[data-vidup]'), clr = e.target.closest('[data-vidclear]');
  if (!S.editing || (!up && !clr)) return;
  e.preventDefault();
  if (clr){ S.editing[clr.dataset.vidclear] = ''; markDirty(); rerenderPanel(); return; }
  const key = up.dataset.vidup;
  const file = await pickFile('video/mp4,video/webm,video/quicktime,.mp4,.webm,.mov,.m4v'); if (!file) return;
  const label = up.textContent; up.disabled = true; up.textContent = 'Subiendo…';
  try {
    const url = await uploadVideo(file);
    S.editing[key] = url; markDirty(); rerenderPanel();
    toast('Video subido. Tocá Guardar para publicarlo.');
  } catch(err){ toast(err.message || 'No se pudo subir el video', true); up.disabled = false; up.textContent = label; }
});
function renderSeasonsEd(E){
  return `<div class="fs"><h3>Temporadas y capítulos <small>${E.seasons.length} temp. · ${epCount(E)} ep.</small></h3>
    ${E.seasons.map((s,si)=>`<div class="season">
      <div class="season-h"><span class="num">T${esc(s.number)}</span>
        <input class="input" id="s-t-${si}" data-season="${si}" data-f="title" value="${esc(s.title)}" placeholder="Nombre de la temporada (opcional)" aria-label="Nombre de la temporada">
        <input class="input" id="s-n-${si}" data-season="${si}" data-f="number" value="${esc(s.number)}" style="max-width:80px;flex:0" inputmode="numeric" aria-label="Número">
        <span data-row="s${si}"><button class="btn btn-danger btn-sm" data-season-x="${si}">Quitar temporada</button></span></div>
      <div class="season-b">
        ${(s.episodes||[]).map((e,ei)=>`<div class="ep-ed">
          <div class="field">${slot('thumb',`seasons.${si}.episodes.${ei}.thumb`,e.thumb,'r169','Miniatura')}<span class="hint">1280 × 720 px</span></div>
          <div style="display:flex;flex-direction:column;gap:10px;min-width:0">
            <div class="fgrid">
              <label class="field"><span>Nº</span><input class="input" id="e-n-${si}-${ei}" data-ep="${si}.${ei}" data-f="number" value="${esc(e.number)}" inputmode="numeric"></label>
              <label class="field"><span>Título del capítulo</span><input class="input" id="e-t-${si}-${ei}" data-ep="${si}.${ei}" data-f="title" value="${esc(e.title)}"></label>
              <label class="field"><span>Duración</span><input class="input" id="e-d-${si}-${ei}" data-ep="${si}.${ei}" data-f="duration" value="${esc(e.duration)}" placeholder="45 min"></label>
            </div>
            <label class="field"><span>Sinopsis</span><textarea class="textarea" style="min-height:64px" id="e-s-${si}-${ei}" data-ep="${si}.${ei}" data-f="description">${esc(e.description)}</textarea></label>
            <div class="ep-ed-foot"><input class="input" id="e-u-${si}-${ei}" data-ep="${si}.${ei}" data-f="url" value="${esc(e.url)}" placeholder="Enlace o código de inserción (embed) del video" aria-label="Enlace del capítulo">
              <button class="iconbtn" data-ep-x="${si}.${ei}" aria-label="Quitar capítulo">✕</button></div>
          </div></div>`).join('')}
        <div><button class="btn btn-line btn-sm" data-ep-add="${si}">+ Capítulo</button></div>
      </div></div>`).join('')}
    <div><button class="btn btn-line" data-season-add>+ Temporada</button></div></div>`;
}
function renderBanners(){
  const bs = S.banners.slice().sort((a,b)=>(a.order||0)-(b.order||0));
  const opts = sel => `<option value="">— Sin título vinculado —</option>` + S.titles.map(t=>`<option value="${esc(t.id)}" ${sel===t.id?'selected':''}>${esc(t.title||'Sin título')}</option>`).join('');
  return `<div class="panel-head"><h1>Banners</h1><button class="btn btn-primary" data-banner-add>+ Nuevo banner</button></div>
    <p style="margin:0;color:var(--muted);max-width:65ch">Los banners activos aparecen en orden aleatorio en cada visita. Los que marques como <b>Fijar primero</b> salen siempre al principio (en este orden) y después rotan los demás. Si un campo queda vacío se usa la información del título vinculado. Sin banners activos, la portada muestra los títulos destacados.</p>
    <div class="banner-list">${bs.map((b,i)=>{ const t = byId(b.titleId); const img = b.image || t?.bannerImage || t?.backdrop;
      return `<div class="bcard" data-banner="${esc(b.id)}">
        <div class="field">${slot('banner','banner:'+b.id, img, 'r31', 'Subir imagen del banner')}
          <span class="hint"><b>2400 × 800 px</b> (3:1). Lo importante en el centro y la derecha.</span>
          <span style="font-size:12.5px;font-weight:700;color:var(--muted);margin-top:6px">Versión celular (opcional)</span>
          ${slot('mobilebanner','bannerm:'+b.id, b.imageMobile || t?.bannerImageMobile, 'r45', 'Subir imagen para celular')}
          <span class="hint"><b>1080 × 1350 px</b> (vertical 4:5). El logo y los botones van abajo al centro: dejá libre la parte de abajo. Si no la cargás, se usa la del título o un recorte de la de escritorio.</span>
          <span class="hint">${b.image ? 'Imagen propia de este banner.' : t?.bannerImage ? 'Usando la imagen de banner del título. Tocá para cambiarla.' : t?.backdrop ? 'Usando el fondo de la ficha. Cargá una imagen de banner en el título o tocá acá.' : 'Sin imagen todavía.'}</span>
          <div class="order"><span class="code">#${i+1}</span>
            <button class="iconbtn" data-bmove="${esc(b.id)}" data-d="-1" aria-label="Subir" ${i===0?'disabled':''}>↑</button>
            <button class="iconbtn" data-bmove="${esc(b.id)}" data-d="1" aria-label="Bajar" ${i===bs.length-1?'disabled':''}>↓</button>
            <label class="check" style="margin-left:auto"><input type="checkbox" id="b-pin-${esc(b.id)}" data-bf="pinned" ${b.pinned?'checked':''}> Fijar primero</label>
            <label class="check"><input type="checkbox" id="b-act-${esc(b.id)}" data-bf="active" ${b.active!==false?'checked':''}> Activo</label></div></div>
        <div class="fgrid">
          <label class="field span2"><span>Título vinculado</span><select class="select" id="b-t-${esc(b.id)}" data-bf="titleId">${opts(b.titleId)}</select></label>
          <label class="field"><span>Antetítulo</span><input class="input" id="b-e-${esc(b.id)}" data-bf="eyebrow" value="${esc(b.eyebrow)}" placeholder="Estreno · Nueva temporada"></label>
          <label class="field"><span>Texto del botón</span><input class="input" id="b-c-${esc(b.id)}" data-bf="cta" value="${esc(b.cta)}" placeholder="Ver ahora"></label>
          <label class="field span2"><span>Título del banner</span><input class="input" id="b-h-${esc(b.id)}" data-bf="headline" value="${esc(b.headline)}" placeholder="${esc(t?.title || 'Título grande')}"></label>
          <label class="field span2"><span>Texto</span><textarea class="textarea" style="min-height:70px" id="b-x-${esc(b.id)}" data-bf="text" placeholder="${esc(t?.tagline || '')}">${esc(b.text)}</textarea></label>
          ${!b.titleId?`<label class="field span2"><span>Enlace del botón</span><input class="input" id="b-l-${esc(b.id)}" data-bf="link" value="${esc(b.link)}" placeholder="https://…"></label>`:''}
          <div class="span2" data-row="b-${esc(b.id)}"><button class="btn btn-danger btn-sm" data-bdel="${esc(b.id)}">Eliminar banner</button></div>
        </div></div>`; }).join('') || '<p class="empty-note" style="padding:12px 0">Todavía no hay banners.</p>'}</div>`;
}
/* ---------- Filas destacadas (Disney+ style feature cards) ---------- */
S.frows = [];
const tintCache = new Map();
function fallbackTint(t){ return `hsl(${hue(t.title||t.id)},55%,22%)`; }
function computeTint(url){
  if (tintCache.has(url)) return tintCache.get(url);
  let saved = null; try { saved = JSON.parse(localStorage.getItem('rv-tints') || '{}')[url]; } catch {}
  if (saved){ const p = Promise.resolve(saved); tintCache.set(url, p); return p; }
  const p = new Promise(res => {
    const im = new Image(); im.crossOrigin = 'anonymous';
    im.onload = () => {
      try {
        const c = document.createElement('canvas'); c.width = c.height = 48; const g = c.getContext('2d'); g.drawImage(im, 0, 0, 48, 48);
        const d = g.getImageData(0, 0, 48, 48).data; const bins = {};
        for (let i = 0; i < d.length; i += 4){
          const r = d[i]/255, gg = d[i+1]/255, b = d[i+2]/255, mx = Math.max(r,gg,b), mn = Math.min(r,gg,b), l = (mx+mn)/2, dl = mx-mn;
          if (dl < .12 || l < .12 || l > .92) continue;
          const sat = dl / (1 - Math.abs(2*l - 1));
          let h = mx === r ? ((gg-b)/dl) % 6 : mx === gg ? (b-r)/dl + 2 : (r-gg)/dl + 4; h = (h*60 + 360) % 360;
          const k = Math.round(h/15), w = sat*sat; const bin = bins[k] || (bins[k] = { w:0, h:0, s:0 }); bin.w += w; bin.h += h*w; bin.s += sat*w;
        }
        let best = null; for (const k in bins) if (!best || bins[k].w > best.w) best = bins[k];
        const out = best ? `hsl(${Math.round(best.h/best.w)},${Math.min(70, Math.round(best.s/best.w*100))}%,20%)` : 'hsl(0,0%,8%)';
        try { const all = JSON.parse(localStorage.getItem('rv-tints') || '{}'); all[url] = out; const keys = Object.keys(all); if (keys.length > 300) delete all[keys[0]]; localStorage.setItem('rv-tints', JSON.stringify(all)); } catch {}
        res(out);
      } catch { res(null); }
    };
    im.onerror = () => res(null);
    im.src = url;
  });
  tintCache.set(url, p); return p;
}
function applyTints(root){
  root.querySelectorAll('[data-tint-src]').forEach(el => {
    const u = el.getAttribute('data-tint-src'); el.removeAttribute('data-tint-src');
    computeTint(u).then(c => { if (c) el.style.setProperty('--tint', c); });
  });
}
function featRow(r){
  const raw = (r.items || []).map(it => ({ it, t: byId(it.titleId) })).filter(x => x.t && x.t.status !== 'borrador');
  if (!raw.length) return '';
  const order = byTaste(raw.map(x => x.t), 'frow-' + r.id);
  const items = raw.slice().sort((a, b) => order.indexOf(a.t) - order.indexOf(b.t));
  return `<section class="row frow"><div class="row-head"><h2>${esc(r.title || 'Destacados')}</h2></div>
    <div class="row-wrap"><button class="rail-btn l" data-scroll="-1" aria-label="Anterior">‹</button>
    <div class="rail rail-feat">${items.map(({ it, t }) => {
      const img = it.image || t.backdrop || t.bannerImage || t.poster || '';
      const meta = [t.year, (t.genres||[]).slice(0,2).join(', ')].filter(Boolean).join(' • ');
      return `<button class="fc" data-open="${esc(t.id)}" aria-label="${esc(t.title)}" style="--tint:${fallbackTint(t)}" ${img ? `data-tint-src="${esc(img)}"` : ''}>
        <span class="fc-bg" ${img ? `data-src="${esc(img)}"` : ''}>${genBlock(t, '', '')}</span><span class="fc-sh"></span>
        <span class="fc-ct">${it.label ? `<span class="fc-chip">${esc(it.label)}</span>` : ''}
          ${t.logo ? `<img class="fc-logo" alt="${esc(t.title)}" data-img="${esc(t.logo)}" hidden><b class="fc-title logo-fallback" hidden>${esc(t.title)}</b>` : `<b class="fc-title">${esc(t.title)}</b>`}
          <span class="fc-meta">${t.rating ? `<i>${esc(t.rating)}</i>` : ''}${esc(meta)}</span></span></button>`; }).join('')}</div>
    <button class="rail-btn r" data-scroll="1" aria-label="Siguiente">›</button></div></section>`;
}
let frowSaveT = null;
function saveFrows(now){
  clearTimeout(frowSaveT);
  const run = async () => { try { await S.db.doc('site/rows').set({ rows: S.frows, updatedAt: Date.now() }); } catch(err){ toast('No se pudieron guardar las filas', true); } };
  if (now) return run();
  frowSaveT = setTimeout(run, 600);
}
function renderFrowsAdmin(){
  const opts = sel => `<option value="">Elegí un título…</option>` + S.titles.slice().sort((a,b)=>(a.title||'').localeCompare(b.title||'')).map(t => `<option value="${esc(t.id)}" ${sel===t.id?'selected':''}>${esc(t.title||'Sin título')}</option>`).join('');
  const rows = S.frows || [];
  return `<div class="panel-head"><h1>Filas destacadas</h1><button class="btn btn-primary" data-frow-add>+ Nueva fila</button></div>
    <p style="margin:0;color:var(--muted);max-width:68ch">Filas con tarjetas grandes estilo Disney+: foto de fondo, logo y un degradado del color de la imagen. Aparecen en el inicio en este orden. Los cambios se guardan solos.</p>
    ${rows.length ? rows.map((r, ri) => `<div class="fs frow-ed" data-frow="${esc(r.id)}">
      <div class="frow-head">
        <input class="input" id="fr-t-${esc(r.id)}" data-frow-title="${esc(r.id)}" value="${esc(r.title||'')}" placeholder="Nombre de la fila (ej: También te podría gustar)">
        <button class="iconbtn" data-frow-move="${esc(r.id)}" data-d="-1" aria-label="Subir fila" ${ri===0?'disabled':''}>↑</button>
        <button class="iconbtn" data-frow-move="${esc(r.id)}" data-d="1" aria-label="Bajar fila" ${ri===rows.length-1?'disabled':''}>↓</button>
        <span data-row="frow-${esc(r.id)}"><button class="btn btn-danger btn-sm" data-frow-del="${esc(r.id)}">Eliminar fila</button></span>
      </div>
      <div class="frow-items">${(r.items||[]).map((it, ii) => { const t = byId(it.titleId); return `<div class="frow-item">
        ${slot('frow', `frow:${r.id}:${ii}`, it.image || (t ? (t.backdrop || t.bannerImage || t.poster) : ''), 'r34', 'Imagen de la tarjeta')}
        <select class="select" id="fr-s-${esc(r.id)}-${ii}" data-frow-item="${esc(r.id)}" data-i="${ii}" data-f="titleId" aria-label="Título">${opts(it.titleId)}</select>
        <input class="input" id="fr-l-${esc(r.id)}-${ii}" data-frow-item="${esc(r.id)}" data-i="${ii}" data-f="label" value="${esc(it.label||'')}" placeholder="Etiqueta (opcional): Nueva serie">
        <span class="hint">${it.image ? 'Imagen propia para esta fila.' : 'Usa el fondo de la ficha. Tocá para poner otra.'} <b>900 × 1335 px</b></span>
        <div class="frow-item-act">
          <button class="iconbtn" data-frow-imove="${esc(r.id)}" data-i="${ii}" data-d="-1" aria-label="Mover antes" ${ii===0?'disabled':''}>←</button>
          <button class="iconbtn" data-frow-imove="${esc(r.id)}" data-i="${ii}" data-d="1" aria-label="Mover después" ${ii===(r.items.length-1)?'disabled':''}>→</button>
          <button class="iconbtn" data-frow-idel="${esc(r.id)}" data-i="${ii}" aria-label="Quitar de la fila">✕</button>
        </div></div>`; }).join('')}
        <button class="frow-add-item" data-frow-iadd="${esc(r.id)}">+ Agregar título</button>
      </div></div>`).join('') : '<p class="empty-note" style="padding:12px 0">Todavía no hay filas destacadas. Tocá “+ Nueva fila”.</p>'}`;
}
document.addEventListener('input', e => {
  const el = e.target; if (!S.owner) return;
  if (el.dataset.frowTitle){ const r = S.frows.find(x => x.id === el.dataset.frowTitle); if (r){ r.title = el.value; saveFrows(); } }
  if (el.dataset.frowItem && el.dataset.f === 'label'){ const r = S.frows.find(x => x.id === el.dataset.frowItem); if (r){ r.items[+el.dataset.i].label = el.value; saveFrows(); } }
});
document.addEventListener('change', e => {
  const el = e.target; if (!S.owner) return;
  if (el.dataset.frowItem && el.dataset.f === 'titleId'){ const r = S.frows.find(x => x.id === el.dataset.frowItem); if (r){ r.items[+el.dataset.i].titleId = el.value; saveFrows(true); rerenderPanel(); } }
});
document.addEventListener('click', e => {
  if (!S.owner) return;
  const b = e.target.closest('[data-frow-add],[data-frow-move],[data-frow-del],[data-frow-iadd],[data-frow-imove],[data-frow-idel]'); if (!b) return;
  const d = b.dataset; const find = id => S.frows.find(x => x.id === id);
  if (d.frowAdd !== undefined){ S.frows.push({ id: uid('r'), title: 'También te podría gustar', items: [] }); saveFrows(true); rerenderPanel(); return; }
  if (d.frowMove){ const i = S.frows.findIndex(x => x.id === d.frowMove), j = i + +d.d; if (j < 0 || j >= S.frows.length) return; [S.frows[i], S.frows[j]] = [S.frows[j], S.frows[i]]; saveFrows(true); rerenderPanel(); return; }
  if (d.frowDel){ inlineConfirm(b.closest('[data-row]'), '¿Eliminar la fila?', () => { S.frows = S.frows.filter(x => x.id !== d.frowDel); saveFrows(true); rerenderPanel(); toast('Fila eliminada'); }); return; }
  if (d.frowIadd){ const r = find(d.frowIadd); r.items.push({ titleId: '', label: '', image: '' }); saveFrows(); rerenderPanel(); return; }
  if (d.frowImove){ const r = find(d.frowImove), i = +d.i, j = i + +d.d; if (j < 0 || j >= r.items.length) return; [r.items[i], r.items[j]] = [r.items[j], r.items[i]]; saveFrows(true); rerenderPanel(); return; }
  if (d.frowIdel){ const r = find(d.frowIdel); r.items.splice(+d.i, 1); saveFrows(true); rerenderPanel(); return; }
});

function renderSite(){
  return `<div class="panel-head"><h1>Sitio</h1></div>
    <div class="fs"><h3>Logo del sitio <small>aparece arriba a la izquierda · PNG transparente de 600 × 160 px aprox.</small></h3>
      <div style="max-width:420px" class="field">${slot('sitelogo','site:logo',S.site.logo,'r31 contain','Subir logo (PNG transparente)')}
      <span class="hint">${S.site.logo ? 'Tocá la imagen para reemplazarla.' : 'Mientras no haya logo se muestra el nombre escrito.'}</span></div>
    </div>
    <div class="fs"><h3>Pie de página <small>se guarda al salir de cada campo</small></h3><div class="fgrid">
      <div class="field span2" style="max-width:300px"><span>Logo del pie (opcional · PNG transparente, 300 × 100 px aprox.)</span>${slot('sitelogo','site:footerLogo',S.site.footerLogo,'r31 contain','Subir logo (PNG transparente)')}<span class="hint">Se muestra chico junto al texto principal.</span></div>
      <label class="field span2"><span>Texto principal</span><input class="input" id="s-foot" data-site="footerText" value="${esc(S.site.footerText ?? 'REALITY VAULT')}"></label>
      <label class="field span2"><span>Texto secundario</span><input class="input" id="s-foot2" data-site="footerSub" value="${esc(S.site.footerSub ?? '')}" placeholder="© 2026 · Todos los derechos reservados"></label>
      <label class="field span2"><span>Texto del DMCA (ventana que se abre desde el pie)</span><textarea class="textarea" id="s-dmca" data-site="dmcaText" rows="10">${esc(S.site.dmcaText || DMCA_DEFAULT)}</textarea><span class="hint">La primera línea es el título. Cada línea nueva es un párrafo.</span></label>
      <label class="field span2"><span>Email de contacto para reclamos (opcional)</span><input class="input" id="s-dmca-mail" data-site="dmcaEmail" type="email" value="${esc(S.site.dmcaEmail || DMCA_EMAIL_DEFAULT)}" placeholder="contacto@…"><span class="hint">Si lo completás, aparece al final del DMCA.</span></label>
      <label class="field"><span>Instagram</span><input class="input" id="s-ig" data-site="instagram" value="${esc(S.site.instagram ?? '')}" placeholder="https://instagram.com/tuusuario"></label>
      <label class="field"><span>TikTok</span><input class="input" id="s-tt" data-site="tiktok" value="${esc(S.site.tiktok ?? '')}" placeholder="https://tiktok.com/@tuusuario"></label>
      <span class="hint span2">Los íconos aparecen solo si cargás el enlace. Dejá un campo vacío para ocultarlo.</span>
    </div></div>
    <div class="fs"><h3>Respaldo <small>todo el catálogo en un archivo</small></h3>
      <div class="toolbar"><button class="btn btn-line" data-backup-dl>Descargar respaldo</button><button class="btn btn-line" data-backup-up>Importar respaldo</button></div>
      <span class="hint">Importar agrega o reemplaza títulos, banners y la configuración del sitio con los del archivo. Las imágenes incluidas dentro del archivo se suben solas.</span>
      <span class="hint" id="importStatus"></span>
    </div>`;
}
function renderTaxonomy(){
  const g = {}, tg = {};
  S.titles.forEach(t => { (t.genres||[]).forEach(x=> g[x]=(g[x]||0)+1); (t.tags||[]).forEach(x=> tg[x]=(tg[x]||0)+1); });
  const block = (name, obj, key) => `<div class="fs"><h3>${name} <small>${Object.keys(obj).length}</small></h3>
    ${Object.keys(obj).length ? `<div class="table-wrap"><table style="min-width:0"><tbody>${Object.entries(obj).sort((a,b)=>b[1]-a[1]).map(([k,n])=>`<tr><td><span class="tag">${esc(k)}</span></td><td class="code">${n} título${n>1?'s':''}</td>
      <td><div class="actions" data-row="tx-${key}-${esc(k)}"><button class="btn btn-line btn-sm" data-tx-ren="${key}" data-v="${esc(k)}">Renombrar</button><button class="btn btn-danger btn-sm" data-tx-del="${key}" data-v="${esc(k)}">Quitar de todos</button></div></td></tr>`).join('')}</tbody></table></div>` : '<p class="empty-note" style="padding:0">Sin datos todavía.</p>'}</div>`;
  return `<div class="panel-head"><h1>Géneros y etiquetas</h1></div>
    <p style="margin:0;color:var(--muted)">Cada género con al menos un título publicado se convierte en una fila de la portada.</p>
    ${block('Géneros', g, 'genres')}${block('Etiquetas', tg, 'tags')}`;
}

/* --- TMDB import --- */
const TMDB_IMG = 'https://image.tmdb.org/t/p/';
function tmdbKey(){ let k = ''; try { k = localStorage.getItem('rv-tmdb-key') || ''; } catch {} return k || (window.RV_CONFIG && window.RV_CONFIG.tmdbKey) || ''; }
function setTmdbKey(k){ try { k ? localStorage.setItem('rv-tmdb-key', k) : localStorage.removeItem('rv-tmdb-key'); } catch {} }
async function tmdb(path, params = {}){
  const key = tmdbKey(); if (!key) throw new Error('Falta la clave de TMDB.');
  const url = new URL('https://api.themoviedb.org/3' + path);
  url.searchParams.set('language', 'es-MX');
  for (const [k,v] of Object.entries(params)) url.searchParams.set(k, v);
  const headers = { accept:'application/json' };
  if (key.startsWith('eyJ')) headers.Authorization = 'Bearer ' + key; else url.searchParams.set('api_key', key);
  const r = await fetch(url, { headers });
  if (r.status === 401) throw new Error('La clave de TMDB no es válida.');
  if (!r.ok) throw new Error('TMDB respondió con un error (' + r.status + ').');
  return r.json();
}
async function tmdbOverview(base, det){
  if (det && det.overview) return det.overview;
  for (const language of ['es-ES','es-AR','en-US']){ const d = await tmdb(base, { language }).catch(()=>null); if (d && d.overview) return d.overview; }
  return '';
}
const tmdbImg = (path, size) => path ? TMDB_IMG + size + path : '';
const TMDB_GENRE_FIX = { 'Reality':'Reality', 'Sci-Fi & Fantasy':'Ciencia ficción', 'Action & Adventure':'Acción y aventura', 'War & Politics':'Bélica', 'Kids':'Infantil' };
function mapRating(cert){
  if (!cert) return '';
  const c = String(cert).toUpperCase().replace(/\s/g,'');
  if (/^(ATP|G|TV-G|TV-Y|TV-Y7|0|U|AA|A|TP|L)$/.test(c)) return /Y7/.test(c) ? '+7' : 'ATP';
  if (/^(\+?7|TV-Y7|PG|TV-PG|B)$/.test(c)) return '+7';
  if (/^(\+?13|\+?12|PG-13|TV-14|B-15|12|13|14)$/.test(c)) return '+13';
  if (/^(\+?16|R|15|16|C)$/.test(c)) return '+16';
  if (/^(\+?18|NC-17|TV-MA|18|D)$/.test(c)) return '+18';
  return '';
}
const fmtRuntime = m => !m ? '' : m >= 60 ? `${Math.floor(m/60)} h${m%60 ? ' ' + (m%60) + ' min' : ''}` : `${m} min`;
function pickLogo(logos){
  const order = ['es','en',null,undefined];
  for (const lang of order){ const l = (logos||[]).filter(x => x.iso_639_1 === lang).sort((a,b)=>(b.vote_average||0)-(a.vote_average||0))[0]; if (l) return l.file_path; }
  return (logos||[])[0]?.file_path || '';
}
function renderTmdbBox(){
  const T = S.tmdb || {};
  const key = tmdbKey();
  if (!key || T.editKey) return `<div class="fs tmdb"><h3>Importar de TMDB <small>completa todo el formulario a partir de The Movie Database</small></h3>
    <p class="hint" style="margin:0">Necesitás una clave gratuita de TMDB. Creá una cuenta en themoviedb.org, entrá a Configuración → API, pedí una clave para uso personal y copiá la <b>Clave de la API</b> o el <b>Token de acceso de lectura</b>. Se guarda solo en este navegador.</p>
    <div class="toolbar"><input class="input" id="tmdb-key" style="max-width:460px" placeholder="Pegá tu clave de TMDB" value="${esc(key)}"><button class="btn btn-primary" data-tmdb-savekey>Guardar clave</button><button class="btn btn-line" data-tmdb-close>Cerrar</button></div></div>`;
  return `<div class="fs tmdb"><h3>Importar de TMDB <small>${S.editing.type==='serie'?'buscando series':'buscando películas'}</small></h3>
    <form class="toolbar" id="tmdbForm"><input class="input" id="tmdb-q" style="max-width:420px" placeholder="Nombre de la ${S.editing.type==='serie'?'serie':'película'}" value="${esc(T.q||'')}"><button class="btn btn-primary" type="submit">Buscar</button><button class="btn btn-line" type="button" data-tmdb-close>Cerrar</button><button class="btn btn-line btn-sm" type="button" data-tmdb-editkey>Cambiar clave</button></form>
    ${T.status ? `<p class="hint" style="margin:0">${esc(T.status)}</p>` : ''}
    ${(T.results||[]).length ? `<div class="tmdb-res">${T.results.map(r => `<button class="tmdb-item" data-tmdb-pick="${r.id}">
        <span class="mini" ${r.poster_path?`style="background-image:url('${tmdbImg(r.poster_path,'w185')}')"`:''}></span>
        <span><strong>${esc(r.name || r.title)}</strong><small>${esc(((r.first_air_date||r.release_date||'').slice(0,4)) || 's/f')}${r.original_name && r.original_name!==r.name ? ' · ' + esc(r.original_name) : ''}${r.original_title && r.original_title!==r.title ? ' · ' + esc(r.original_title) : ''}</small></span></button>`).join('')}</div>` : ''}
    <label class="check"><input type="checkbox" id="tmdb-specials" ${T.specials?'checked':''}> Incluir “Especiales” (temporada 0)</label>
    <span class="hint">Al elegir un resultado se reemplazan los datos del formulario: textos, géneros, reparto, imágenes y ${S.editing.type==='serie'?'todas las temporadas con sus capítulos':'duración'}. Los enlaces para ver no se tocan. Revisá todo y tocá Guardar.</span></div>`;
}
async function tmdbSearch(q){
  const E = S.editing; S.tmdb = { ...(S.tmdb||{}), q, results:[], status:'Buscando…' }; rerenderPanel();
  try {
    const r = await tmdb(E.type==='serie' ? '/search/tv' : '/search/movie', { query:q, include_adult:'false' });
    S.tmdb.results = (r.results||[]).slice(0,12);
    S.tmdb.status = S.tmdb.results.length ? 'Elegí el resultado correcto:' : 'No se encontraron resultados.';
  } catch(err){ S.tmdb.status = err.message; }
  rerenderPanel();
}
// Descripciones en español para títulos que TMDB solo tiene en inglés (clave: id de TMDB)
const DESC_ES = {
  7587: 'Spin-off de “I Love New York” de VH1, en ocho episodios de media hora. Tiffany “New York” Pollard se va a Hollywood para conseguir un papel de actriz y, para lograrlo, deja atrás su vida, a su madre y a su pareja.',
  893: 'Serie de VH1 en la que Tiffany “New York” Pollard, finalista de dos temporadas de “Flavor of Love”, busca a su verdadero amor entre un grupo de pretendientes, con el apoyo de su familia y de otros personajes del programa.',
  18088: 'Continuación de “New York Goes to Hollywood”. Tiffany “New York” Pollard intenta conseguir un trabajo común: los fans eligen su empleo en cada episodio y ella gana un bono de 10.000 dólares por semana si impresiona a su jefe.',
  275651: 'Más de 20 años después de conquistar el mundo, las amigas de toda la vida Paris Hilton y Nicole Richie se reúnen en un nuevo especial: producir una presentación operística única en la vida, basada en su frase “Sanasa”.',
  299091: 'Una niña de 12 años y sus cinco adorables perritos viven grandes aventuras desde su lujoso penthouse, llevando alegría y amabilidad a toda su comunidad.',
  8004: 'Reality competitivo en el que Paris Hilton busca a su nueva mejor amiga. Tras el éxito de la versión original estadounidense llegaron otra temporada y dos versiones internacionales.',
  19108: 'Reality de E! sobre la vida de la modelo Kendra Wilkinson después de dejar la Mansión Playboy: su compromiso y casamiento con el jugador Hank Baskett y su adaptación a ser mamá primeriza.',
  2396: '¿Qué pasa cuando las estrellas más grandes se marean con la fama? El bromista Ashton Kutcher está ahí para bajarlas a tierra con sus bromas.',
  4940: 'Un grupo de jóvenes cuyo comportamiento desbordado arruinó sus relaciones y sus carreras comparte una mansión. Dicen que quieren cambiar, pero ¿vivir juntas las ayudará a salir adelante o reinará el caos?',
  1776: 'Veinte mujeres solteras se mudan a una mansión en Los Ángeles para competir por el corazón de Flavor Flav, quien elige a su verdadero amor.',
  499: 'Reality estadounidense que muestra la vida cotidiana del cantante de heavy metal Ozzy Osbourne y su familia. Se estrenó en MTV en 2002 y en su primera temporada fue la serie más vista de la cadena.'
};
async function fillMissingDescriptions(manual){
  const miss = S.titles.filter(t => !(t.description||'').trim());
  if (!miss.length){ if (manual) toast('Todos los títulos ya tienen descripción.'); return; }
  let n = 0;
  for (const t of miss){
    let d = DESC_ES[t.tmdbId] || '';
    if (!d && t.tmdbId && tmdbKey()){ try { d = await tmdbOverview((t.type==='serie'?'/tv/':'/movie/')+t.tmdbId, null); } catch {} }
    if (!d) continue;
    try { await S.db.doc('titles/'+t.id).set({ ...t, description:d, updatedAt:Date.now() }); n++; } catch {}
  }
  if (n) toast(`Completé la descripción de ${n} título${n>1?'s':''}.`);
  const left = S.titles.filter(t => !(t.description||'').trim()).length;
  if (left && manual) toast(`${left} título${left>1?'s siguen':' sigue'} sin descripción: cargala a mano.`, true);
}
async function tmdbQuickSearch(q){
  const E = S.editing; if (!E || !S.qt) return; S.qt.q = q; S.qt.results = []; S.qt.pick = null; S.qt.status = 'Buscando…'; rerenderPanel();
  try {
    const r = await tmdb(E.type==='serie' ? '/search/tv' : '/search/movie', { query:q, include_adult:'false' });
    S.qt.results = (r.results||[]).slice(0,12);
    S.qt.status = S.qt.results.length ? 'Tocá el resultado correcto' : 'No se encontraron resultados.';
  } catch(err){ S.qt.status = err.message; }
  rerenderPanel(); document.getElementById('qt-q')?.focus();
}
async function tmdbQuickPick(id){
  const E = S.editing; if (!E || !S.qt) return; const isS = E.type==='serie'; const base = (isS?'/tv/':'/movie/')+id;
  S.qt.status = 'Cargando…'; rerenderPanel();
  try {
    const det = await tmdb(base, { append_to_response: isS ? 'content_ratings' : 'release_dates' });
    let cert = '';
    if (isS){ const cr = det.content_ratings?.results||[]; cert = (cr.find(x=>x.iso_3166_1==='AR')||cr.find(x=>x.iso_3166_1==='MX')||cr.find(x=>x.iso_3166_1==='US'))?.rating; }
    else { const rd = det.release_dates?.results||[]; const pk = rd.find(x=>x.iso_3166_1==='AR')||rd.find(x=>x.iso_3166_1==='MX')||rd.find(x=>x.iso_3166_1==='US'); cert = pk?.release_dates?.find(x=>x.certification)?.certification; }
    S.qt.pick = { id, title: det.name||det.title||'', year: (det.first_air_date||det.release_date||'').slice(0,4), rating: mapRating(cert), poster: tmdbImg(det.poster_path,'w500'), overview: await tmdbOverview(base, det), seasons: (det.seasons||[]).filter(x=>x.episode_count>0 || x.season_number>0) };
    S.qt.sel = S.qt.pick.seasons.filter(x=>x.season_number>0).map(x=>x.season_number);
    S.qt.status = '';
  } catch(err){ S.qt.status = err.message; }
  rerenderPanel();
}
async function tmdbImport(id){
  const E = S.editing; const isSerie = E.type==='serie';
  S.tmdb.status = 'Importando datos…'; S.tmdb.results = []; rerenderPanel();
  try {
    const base = isSerie ? `/tv/${id}` : `/movie/${id}`;
    const det = await tmdb(base, { append_to_response: isSerie ? 'aggregate_credits,content_ratings' : 'credits,release_dates' });
    const imgs = await tmdb(base + '/images', { language:'', include_image_language:'es,en,null' }).catch(()=>({}));
    let det2 = det;
    if (!det.overview || !det.tagline){ det2 = await tmdb(base, { language:'es-ES' }).catch(()=>det); }
    E.tmdbId = id;
    E.title = det.name || det.title || E.title;
    E.tagline = det.tagline || det2.tagline || '';
    E.description = det.overview || det2.overview || DESC_ES[id] || await tmdbOverview(base, null);
    const first = det.first_air_date || det.release_date || '';
    E.year = first.slice(0,4) || E.year;
    if (isSerie) E.yearEnd = (det.status === 'Ended' || det.status === 'Canceled') && det.last_air_date && det.last_air_date.slice(0,4) !== E.year ? det.last_air_date.slice(0,4) : '';
    E.genres = (det.genres||[]).map(g => TMDB_GENRE_FIX[g.name] || g.name);
    if (!isSerie) E.duration = fmtRuntime(det.runtime);
    // rating
    let cert = '';
    if (isSerie){ const cr = det.content_ratings?.results || []; cert = (cr.find(x=>x.iso_3166_1==='AR') || cr.find(x=>x.iso_3166_1==='MX') || cr.find(x=>x.iso_3166_1==='US'))?.rating; }
    else { const rd = det.release_dates?.results || []; const pick = rd.find(x=>x.iso_3166_1==='AR') || rd.find(x=>x.iso_3166_1==='MX') || rd.find(x=>x.iso_3166_1==='US'); cert = pick?.release_dates?.find(x=>x.certification)?.certification; }
    const rt = mapRating(cert); if (rt) E.rating = rt;
    // people
    if (isSerie){
      E.director = (det.created_by||[]).map(p=>p.name).join(', ');
      E.cast = (det.aggregate_credits?.cast||[]).slice(0,16).map(c => ({ name:c.name, role:(c.roles||[])[0]?.character || '', photo: tmdbImg(c.profile_path,'w185') }));
    } else {
      E.director = (det.credits?.crew||[]).filter(c=>c.job==='Director').map(c=>c.name).join(', ');
      E.cast = (det.credits?.cast||[]).slice(0,16).map(c => ({ name:c.name, role:c.character || '', photo: tmdbImg(c.profile_path,'w185') }));
    }
    // images
    const bestPoster = (imgs.posters||[]).find(x=>x.iso_639_1==='es')?.file_path || det.poster_path;
    const titled = (imgs.backdrops||[]).filter(x => x.iso_639_1 === 'es' || x.iso_639_1 === 'en').sort((a,b) => (a.iso_639_1==='es'?-1:0) - (b.iso_639_1==='es'?-1:0) || (b.vote_average||0) - (a.vote_average||0))[0];
    if (titled) E.thumbWide = tmdbImg(titled.file_path, 'w1280');
    E.poster = tmdbImg(bestPoster, 'w780');
    const bd = det.backdrop_path || (imgs.backdrops||[])[0]?.file_path;
    E.backdrop = tmdbImg(bd, 'original');
    const logo = pickLogo(imgs.logos); if (logo) E.logo = tmdbImg(logo, 'w500');
    // seasons
    if (isSerie){
      const wanted = (det.seasons||[]).filter(x => S.tmdb.sel ? S.tmdb.sel.includes(x.season_number) : (S.tmdb.specials || x.season_number > 0));
      const old = E.seasons || [];
      const seasons = [];
      for (let i=0;i<wanted.length;i++){
        S.tmdb.status = `Importando temporada ${wanted[i].season_number} (${i+1} de ${wanted.length})…`; rerenderPanel();
        const sd = await tmdb(`/tv/${id}/season/${wanted[i].season_number}`);
        const prev = old.find(x => +x.number === sd.season_number);
        seasons.push({
          id: prev?.id || uid('s'), number: sd.season_number,
          title: sd.name && !/^(Temporada|Season)\s*\d+$/i.test(sd.name) ? sd.name : '',
          episodes: (sd.episodes||[]).map(ep => {
            const pe = prev?.episodes?.find(x => +x.number === ep.episode_number);
            return { id: pe?.id || uid('e'), number: ep.episode_number, title: ep.name || '', description: ep.overview || '', duration: ep.runtime ? `${ep.runtime} min` : '', thumb: tmdbImg(ep.still_path,'w780'), url: pe?.url || '' };
          })
        });
      }
      E.seasons = seasons;
    }
    markDirty();
    S.tmdb = { open:false };
    toast('Datos importados de TMDB. Revisá y tocá Guardar.');
    rerenderPanel(); window.scrollTo(0,0);
  } catch(err){ S.tmdb.status = err.message || 'No se pudo importar.'; rerenderPanel(); }
}

/* --- backup --- */
function downloadBackup(){
  const strip = o => { const c = { ...o }; delete c._new; return c; };
  const data = { app:'reality-vault', version:2, exportedAt:new Date().toISOString(), titles:S.titles.map(strip), banners:S.banners.map(strip), site:S.site };
  const blob = new Blob([JSON.stringify(data, null, 2)], { type:'application/json' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
  a.download = `reality-vault-respaldo-${new Date().toISOString().slice(0,10)}.json`;
  document.body.appendChild(a); a.click(); a.remove(); setTimeout(()=>URL.revokeObjectURL(a.href), 5000);
}
async function importBackup(){
  const inp = document.createElement('input'); inp.type='file'; inp.accept='application/json,.json';
  inp.onchange = async () => {
    const f = inp.files[0]; if (!f) return;
    const status = (m) => { const el = document.getElementById('importStatus'); if (el) el.textContent = m; };
    try {
      const data = JSON.parse(await f.text());
      if (!Array.isArray(data.titles)) throw new Error('El archivo no es un respaldo de REALITY VAULT.');
      const cache = new Map();
      async function walk(v, kind){
        if (typeof v === 'string' && v.startsWith('data:image/')){
          if (!cache.has(v)) cache.set(v, dataUrlToStorage(v, kind));
          return cache.get(v);
        }
        if (Array.isArray(v)) return Promise.all(v.map(x => walk(x, kind)));
        if (v && typeof v === 'object'){
          const o = {}; for (const [k,x] of Object.entries(v)) o[k] = await walk(x, ({poster:'poster',backdrop:'backdrop',bannerImage:'banner',image:'banner',logo:'logo',thumb:'thumb',photo:'face',footerLogo:'sitelogo'})[k] || kind);
          return o;
        }
        return v;
      }
      status('Subiendo imágenes…');
      const titles = await walk(data.titles), banners = await walk(data.banners || []), site = await walk(data.site || {});
      status('Guardando títulos…');
      for (const t of titles) await S.db.doc('titles/'+t.id).set(t);
      for (const b of banners) await S.db.doc('banners/'+b.id).set(b);
      if (Object.keys(site).length) await S.db.doc('site/config').set(site);
      status(`Listo: ${titles.length} títulos y ${banners.length} banners importados.`);
      toast('Respaldo importado');
    } catch(err){ status(''); toast(err.message || 'No se pudo importar', true); }
  };
  inp.click();
}

/* --- admin interactions --- */
async function applyImage(path, ref){
  if (path.startsWith('frow:')){ const [, rid, idx] = path.split(':'); const r = S.frows.find(x => x.id === rid); if (r && r.items[+idx]){ r.items[+idx].image = ref; await saveFrows(true); rerenderPanel(); } return; }
  if (path.startsWith('site:')){ await S.db.doc('site/config').set({ ...S.site, [path.slice(5)]:ref, updatedAt:Date.now() }); toast('Imagen actualizada'); }
  else if (path.startsWith('bannerm:')){ await S.db.doc('banners/'+path.slice(8)).update({imageMobile:ref}); toast('Imagen para celular actualizada'); }
  else if (path.startsWith('banner:')){ await S.db.doc('banners/'+path.slice(7)).update({image:ref}); toast('Imagen del banner actualizada'); }
  else { setPath(S.editing, path, ref); markDirty(); rerenderPanel(); }
}
function setPath(obj, path, val){ const p = path.split('.'); let o=obj; for (let i=0;i<p.length-1;i++) o = o[p[i]]; o[p[p.length-1]] = val; }
function markDirty(){ S.dirty = true; const s = document.getElementById('saveStatus'); if (s) s.textContent = 'Cambios sin guardar'; }
function animatePanel(panel){
  panel = panel || document.getElementById('panel'); if (!panel) return;
  panel.classList.remove('enter'); void panel.offsetWidth; panel.classList.add('enter');
}
function rerenderPanel(anim){
  const panel = document.getElementById('panel'); if (!panel) return render();
  const y = window.scrollY;
  panel.innerHTML = S.adminTab==='catalogo' ? (S.editing && !S.quick ? renderEditor() : renderCatalog()) : S.adminTab==='banners' ? renderBanners() : S.adminTab==='sitio' ? renderSite() : S.adminTab==='filas' ? renderFrowsAdmin() : renderTaxonomy();
  hydrate(panel); window.scrollTo(0,y);
  if (anim === true) animatePanel(panel);
}
function inlineConfirm(holder, label, onYes){
  if (!holder) return;
  const prev = holder.innerHTML;
  holder.innerHTML = `<span class="confirm">${esc(label)} <button class="btn btn-danger btn-sm" data-yes>Sí, eliminar</button><button class="btn btn-line btn-sm" data-no>No</button></span>`;
  holder.querySelector('[data-yes]').onclick = e => { e.stopPropagation(); onYes(); };
  holder.querySelector('[data-no]').onclick = e => { e.stopPropagation(); holder.innerHTML = prev; };
}
function collectRefs(t){
  const r = [t.poster,t.backdrop,t.logo,t.thumbWide,t.bannerImage,t.bannerImageMobile,t.backdropMobile,t.bannerImageTv,t.backdropTv,t.previewVideo,t.previewVideoMobile,...(t.cast||[]).map(c=>c.photo)];
  (t.seasons||[]).forEach(s=>(s.episodes||[]).forEach(e=>r.push(e.thumb)));
  return r.filter(Boolean);
}
async function deleteTitle(id){
  const t = byId(id); if (!t) return;
  try {
    await S.db.doc('titles/'+id).delete();
    removeMedia(collectRefs(t));
    S.banners.filter(b=>b.titleId===id).forEach(b => S.db.doc('banners/'+b.id).update({titleId:''}).catch(()=>{}));
    if (S.editing?.id===id){ S.editing=null; S.dirty=false; }
    toast('Título eliminado'); rerenderPanel();
  } catch(e){ toast('No se pudo eliminar: ' + (e.message||e.code), true); }
}
async function saveTitle(){
  const E = S.editing;
  if (!E.title.trim()){ toast('Poné un título antes de guardar.', true); document.getElementById('f-title')?.focus(); return; }
  const doc = clone(E); const isNew = doc._new; delete doc._new;
  if (isNew){ doc.code = Math.max(0, ...S.titles.map(t=>t.code||0)) + 1; doc.createdAt = Date.now(); }
  doc.updatedAt = Date.now();
  doc.cast = doc.cast.filter(c=>c.name.trim());
  doc.seasons.forEach(s => { s.number = +s.number || 1; s.episodes.forEach(e => e.number = +e.number || 1); });
  if (doc.type!=='serie') doc.seasons = [];
  const btn = document.querySelector('[data-save]'); if (btn){ btn.disabled = true; btn.textContent='Guardando…'; }
  try {
    await S.db.doc('titles/'+doc.id).set(doc);
    S.editing = clone(doc); S.dirty = false;
    toast(isNew ? 'Título creado' : 'Cambios guardados');
    rerenderPanel();
  } catch(e){
    toast(e.code==='invalid_argument' && JSON.stringify(doc).length>250000 ? 'El título es demasiado grande. Probá con menos capítulos por temporada.' : 'No se pudo guardar: ' + (e.message||e.code), true);
    if (btn){ btn.disabled=false; btn.textContent='Guardar'; }
  }
}
function addChip(key, v){
  v = v.trim().replace(/^#/, ''); if (!v) return;
  const list = S.editing[key]; if (!list.some(x=>x.toLowerCase()===v.toLowerCase())) list.push(v);
  markDirty(); rerenderPanel(); document.getElementById('chip-'+key)?.focus();
}

// accept a full embed code (<iframe src="…">) pasted into a video link field: keep only the link
function cleanVideoLink(v){
  const raw = String(v || '').trim();
  const m = raw.match(/<iframe[^>]*\ssrc\s*=\s*["']([^"']+)["']/i);
  let u = m ? m[1] : raw;
  if (u.startsWith('//')) u = 'https:' + u;
  return u.replace(/&amp;/g, '&');
}
app.addEventListener('input', e => {
  const el = e.target;
  if ((el.dataset.k === 'url' || el.dataset.k === 'trailer' || el.dataset.f === 'url') && /<iframe/i.test(el.value)) el.value = cleanVideoLink(el.value);
  if (el.id==='adminFilter'){ S.adminFilter = el.value; const pos = el.selectionStart; rerenderPanel(); const n=document.getElementById('adminFilter'); n.focus(); n.setSelectionRange(pos,pos); return; }
  const E = S.editing; if (!E) return;
  if (el.dataset.k && el.type!=='checkbox'){ E[el.dataset.k] = el.value; markDirty(); }
  else if (el.dataset.cast){ E.cast[+el.dataset.cast][el.dataset.f] = el.value; markDirty(); }
  else if (el.dataset.season){ E.seasons[+el.dataset.season][el.dataset.f] = el.value; markDirty(); if (el.dataset.f==='number'){ const n = el.closest('.season-h').querySelector('.num'); n.textContent = 'T'+el.value; } }
  else if (el.dataset.ep){ const [si,ei] = el.dataset.ep.split('.').map(Number); E.seasons[si].episodes[ei][el.dataset.f] = el.value; markDirty(); }
});
app.addEventListener('change', async e => {
  const el = e.target;
  if (el.id==='tpSeason'){ S.tpSeason = +el.value; const y = window.scrollY; render(); window.scrollTo(0,y); document.getElementById('tpSeason')?.focus(); return; }
  if (el.dataset.site){ try { await S.db.doc('site/config').set({ ...S.site, [el.dataset.site]: el.value.trim(), updatedAt:Date.now() }); toast('Guardado'); } catch(err){ toast('No se pudo guardar', true); } return; }
  if (S.editing && el.type==='checkbox' && el.dataset.k){
    if (el.dataset.k==='featured') S.editing.featured = el.checked;
    if (el.dataset.k==='published') S.editing.status = el.checked ? 'publicado' : 'borrador';
    markDirty();
  }
  if (el.dataset.bf){
    const id = el.closest('[data-banner]').dataset.banner;
    const val = el.type==='checkbox' ? el.checked : el.value;
    try { await S.db.doc('banners/'+id).update({ [el.dataset.bf]: val }); if (el.dataset.bf==='titleId'||el.dataset.bf==='active'||el.dataset.bf==='pinned') toast('Banner actualizado'); }
    catch(err){ toast('No se pudo guardar el banner', true); }
  }
});
app.addEventListener('keydown', e => {
  const el = e.target;
  if (el.dataset.chipIn && (e.key==='Enter' || e.key===',')){ e.preventDefault(); addChip(el.dataset.chipIn, el.value); }
  if (el.dataset.chipIn && e.key==='Backspace' && !el.value){ const l = S.editing[el.dataset.chipIn]; if (l.length){ l.pop(); markDirty(); rerenderPanel(); document.getElementById('chip-'+el.dataset.chipIn)?.focus(); } }
  if (el.classList.contains('slot') && (e.key==='Enter'||e.key===' ')){ e.preventDefault(); el.click(); }
});
app.addEventListener('focusout', e => { const el=e.target; if (el.dataset?.chipIn && el.value.trim()) addChip(el.dataset.chipIn, el.value); });

app.addEventListener('click', async e => {
  const t = e.target.closest('button, [data-slot], a, [data-open]'); if (!t) return;
  const d = t.dataset;
  if (d.open){ S.q=''; document.getElementById('q').value=''; const tt = byId(d.open); if (tt) navigate(titlePath(tt)); return; }
  if (d.tptab){ S.tpTab = d.tptab; const y = window.scrollY; render(); window.scrollTo(0,y); return; }
  if (d.scroll){ const rail = t.parentElement.querySelector('.rail'); rail.scrollBy({left: +d.scroll * rail.clientWidth * .85, behavior:'smooth'}); return; }
  if (d.dot){ setHero(+d.dot); startHero(); return; }
  if (d.genre !== undefined && t.classList.contains('chip')){ S.genre = d.genre; render(); return; }
  if (d.logout !== undefined){ await doLogout(); return; }
  if (!S.owner) return;
  if (d.backupDl !== undefined){ downloadBackup(); return; }
  if (d.backupUp !== undefined){ importBackup(); return; }
  if (d.tab){ if (S.dirty && S.editing){ toast('Guardá o cancelá los cambios primero', true); return; } S.adminTab = d.tab; S.editing=null; render(); animatePanel(); return; }
  if (d.catfilter){ S.catalogFilter = d.catfilter; document.querySelectorAll('[data-catfilter]').forEach(b=>b.setAttribute('aria-pressed', String(b.dataset.catfilter===d.catfilter))); syncPillSegs(); const g=document.getElementById('catGrid'); if (g){ g.innerHTML = catalogGridHtml(); hydrate(g); g.classList.remove('grid-in'); void g.offsetWidth; g.classList.add('grid-in'); } return; }
  if (d.etab){ S.editorTab = d.etab; rerenderPanel(true); return; }
  if (d.nuevoOpen !== undefined){ S.catalogPicker = true; rerenderPanel(); return; }
  if (d.pickerClose !== undefined){ S.qt = null; S.catalogPicker = false; if (S.quick){ S.quick = false; S.editing = null; S.dirty = false; } rerenderPanel(); return; }
  if (d.quickTmdb !== undefined){ S.qt = { q: S.editing.title||'', results:[], sel:[], status: tmdbKey()?'':'Falta la clave de TMDB (Cambiar clave en el editor).' }; rerenderPanel(); setTimeout(()=>{ const i=document.getElementById('qt-q'); i?.focus(); if (i && i.value.trim()) tmdbQuickSearch(i.value.trim()); },80); return; }
  if (d.fillDesc !== undefined){ fillMissingDescriptions(true); return; }
  if (d.qtBack !== undefined){ if (S.qt?.pick){ S.qt.pick = null; } else S.qt = null; rerenderPanel(); return; }
  if (d.qtSeason !== undefined){ const n = +d.qtSeason, q = S.qt; q.sel = q.sel.includes(n) ? q.sel.filter(x=>x!==n) : [...q.sel, n]; rerenderPanel(); return; }
  if (d.qtSeasall !== undefined){ S.qt.sel = S.qt.pick.seasons.map(x=>x.season_number); rerenderPanel(); return; }
  if (d.qtSeasnone !== undefined){ S.qt.sel = []; rerenderPanel(); return; }
  if (d.qtPick !== undefined){ tmdbQuickPick(d.qtPick); return; }
  if (d.qtConfirm !== undefined){ const id = d.qtConfirm; const sel = S.qt.sel.slice(); S.qt = null; S.quick = false; S.catalogPicker = false; S.tmdb = { open:true, specials:false, sel }; S.editorTab = 'info'; rerenderPanel(true); tmdbImport(+id); return; }
  if (d.new){ S.tmdb = { open:false }; S.catalogPicker = false; S.editing = blankTitle(d.new); S.quick = true; S.dirty=false; S.editorTab='info'; rerenderPanel(); setTimeout(()=>document.getElementById('f-title')?.focus(), 120); return; }
  if (d.edit){ S.tmdb = { open:false }; S.editing = clone(byId(d.edit)); S.editing.cast ||= []; S.editing.seasons ||= []; S.editing.genres ||= []; S.editing.tags ||= []; S.dirty=false; S.editorTab='info'; rerenderPanel(true); window.scrollTo(0,0); return; }
  if (d.del !== undefined && d.del){ inlineConfirm(t.closest('[data-row]'), '¿Eliminar?', () => deleteTitle(d.del)); return; }
  if (d.back !== undefined){ S.editing=null; S.dirty=false; rerenderPanel(true); return; }
  if (d.save !== undefined){ saveTitle(); return; }
  const E = S.editing;
  if (d.tmdbOpen !== undefined){ S.tmdb = { ...(S.tmdb||{}), open:true, editKey:false }; rerenderPanel(); document.getElementById(tmdbKey()?'tmdb-q':'tmdb-key')?.focus(); return; }
  if (d.tmdbClose !== undefined){ S.tmdb = { open:false }; rerenderPanel(); return; }
  if (d.tmdbEditkey !== undefined){ S.tmdb.editKey = true; rerenderPanel(); return; }
  if (d.tmdbSavekey !== undefined){ const k = document.getElementById('tmdb-key').value.trim(); setTmdbKey(k); S.tmdb.editKey = false; toast(k ? 'Clave guardada en este navegador' : 'Clave borrada'); rerenderPanel(); document.getElementById('tmdb-q')?.focus(); return; }
  if (d.tmdbPick){ S.tmdb.specials = !!document.getElementById('tmdb-specials')?.checked; tmdbImport(d.tmdbPick); return; }
  if (d.avail && E){ E.availability = d.avail; markDirty(); rerenderPanel(); return; }
  if (d.type && E){ E.type = d.type; if (E.type==='serie' && !E.seasons.length) E.seasons.push({id:uid('s'),number:1,title:'',episodes:[]}); markDirty(); rerenderPanel(); return; }
  if (d.chipX){ E[d.chipX].splice(+d.i,1); markDirty(); rerenderPanel(); return; }
  if (d.chipAdd){ addChip(d.chipAdd, d.v); return; }
  if (d.castAdd !== undefined){ E.cast.push({name:'',role:'',photo:''}); markDirty(); rerenderPanel(); document.getElementById('cast-n-'+(E.cast.length-1))?.focus(); return; }
  if (d.castX !== undefined){ E.cast.splice(+d.castX,1); markDirty(); rerenderPanel(); return; }
  if (d.seasonAdd !== undefined){ const n = Math.max(0,...E.seasons.map(s=>+s.number||0))+1; E.seasons.push({id:uid('s'),number:n,title:'',episodes:[]}); markDirty(); rerenderPanel(); return; }
  if (d.seasonX !== undefined){ const si=+d.seasonX; inlineConfirm(t.closest('[data-row]'), '¿Quitar con sus capítulos?', ()=>{ E.seasons.splice(si,1); markDirty(); rerenderPanel(); }); return; }
  if (d.epAdd !== undefined){ const s = E.seasons[+d.epAdd]; const n = Math.max(0,...s.episodes.map(x=>+x.number||0))+1; s.episodes.push({id:uid('e'),number:n,title:'',description:'',duration:'',thumb:'',url:''}); markDirty(); rerenderPanel(); document.getElementById(`e-t-${d.epAdd}-${s.episodes.length-1}`)?.focus(); return; }
  if (d.epX){ const [si,ei]=d.epX.split('.').map(Number); E.seasons[si].episodes.splice(ei,1); markDirty(); rerenderPanel(); return; }
  if (d.clear){ e.stopPropagation();
    if (d.clear.startsWith('frow:')){ const [, rid, idx] = d.clear.split(':'); const r = S.frows.find(x => x.id === rid); if (r && r.items[+idx]){ r.items[+idx].image = ''; await saveFrows(true); rerenderPanel(); } return; }
    if (d.clear.startsWith('site:')){ await S.db.doc('site/config').set({ ...S.site, [d.clear.slice(5)]:'' }).catch(()=>toast('No se pudo quitar',true)); return; }
    if (d.clear.startsWith('bannerm:')){ await S.db.doc('banners/'+d.clear.slice(8)).update({imageMobile:''}).catch(()=>toast('No se pudo quitar',true)); return; }
    if (d.clear.startsWith('banner:')){ const id=d.clear.slice(7); await S.db.doc('banners/'+id).update({image:''}).catch(()=>toast('No se pudo quitar',true)); return; }
    setPath(E, d.clear, ''); markDirty(); rerenderPanel(); return; }
  if (d.slotUrl){ e.stopPropagation();
    const url = (window.prompt('Pegá el enlace de la imagen (https://…)') || '').trim();
    if (!url) return;
    if (!/^https?:\/\//i.test(url)){ toast('El enlace tiene que empezar con https://', true); return; }
    try { await applyImage(d.slotUrl, url); } catch(err){ toast(err.message || 'No se pudo guardar', true); }
    return;
  }
  if (d.slot){
    const file = await pickFile(); if (!file) return;
    if (!file.type.startsWith('image/')){ toast('Elegí un archivo de imagen', true); return; }
    let toUpload = file;
    const spec = CROP_SPECS[d.slot];
    if (spec && !/gif|svg/.test(file.type)){
      try { toUpload = await cropImage(file, spec[0], spec[1]); } catch { toUpload = file; }
      if (!toUpload) return;
    }
    t.classList.add('busy');
    try {
      const ref = await uploadImage(toUpload, d.slot);
      await applyImage(d.path, ref);
    } catch(err){ toast(err.message || 'No se pudo subir la imagen', true); t.classList.remove('busy'); }
    return;
  }
  if (d.bannerAdd !== undefined){
    const id = uid('b'); const order = Math.max(0,...S.banners.map(b=>b.order||0))+1;
    try { await S.db.doc('banners/'+id).set({ id, titleId: published()[0]?.id || '', image:'', eyebrow:'', headline:'', text:'', cta:'', link:'', active:true, order, createdAt:Date.now() }); toast('Banner creado'); }
    catch(err){ toast('No se pudo crear el banner', true); } return;
  }
  if (d.bmove){
    const bs = S.banners.slice().sort((a,b)=>(a.order||0)-(b.order||0)); const i = bs.findIndex(b=>b.id===d.bmove); const j = i + +d.d;
    if (j<0||j>=bs.length) return;
    [bs[i],bs[j]] = [bs[j],bs[i]];
    try { for (let k=0;k<bs.length;k++) if ((bs[k].order||0)!==k+1) await S.db.doc('banners/'+bs[k].id).update({order:k+1}); } catch(err){ toast('No se pudo reordenar', true); }
    return;
  }
  if (d.bdel){ const b = S.banners.find(x=>x.id===d.bdel); inlineConfirm(t.closest('[data-row]'), '¿Eliminar este banner?', async ()=>{ try{ await S.db.doc('banners/'+d.bdel).delete(); if (b?.image) removeMedia([b.image]); toast('Banner eliminado'); }catch(err){ toast('No se pudo eliminar', true); } }); return; }
  if (d.txDel){ const key=d.txDel, v=d.v; inlineConfirm(t.closest('[data-row]'), `¿Quitar “${v}”?`, async ()=>{
      for (const x of S.titles.filter(x=>(x[key]||[]).includes(v))) await S.db.doc('titles/'+x.id).update({[key]: x[key].filter(y=>y!==v)}).catch(()=>{});
      toast('Quitado de todos los títulos'); }); return; }
  if (d.txRen){ const key=d.txRen, v=d.v; const holder = t.closest('[data-row]'); const prev = holder.innerHTML;
    holder.innerHTML = `<span class="confirm" style="color:var(--fg)"><input class="input" id="ren-in" value="${esc(v)}" style="width:160px"><button class="btn btn-primary btn-sm" data-ren-ok>Guardar</button><button class="btn btn-line btn-sm" data-ren-no>No</button></span>`;
    const inp = holder.querySelector('#ren-in'); inp.focus(); inp.select();
    holder.querySelector('[data-ren-no]').onclick = ev => { ev.stopPropagation(); holder.innerHTML = prev; };
    holder.querySelector('[data-ren-ok]').onclick = async ev => { ev.stopPropagation(); const nv = inp.value.trim(); if (!nv || nv===v) { holder.innerHTML=prev; return; }
      for (const x of S.titles.filter(x=>(x[key]||[]).includes(v))) await S.db.doc('titles/'+x.id).update({[key]: [...new Set(x[key].map(y=>y===v?nv:y))]}).catch(()=>{});
      toast('Renombrado'); };
    return; }
});


/* ---------- title page ---------- */
const ICONS = {
  ig:'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.5" cy="6.5" r="1" fill="currentColor" stroke="none"/></svg>',
  tt:'<svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16.6 5.82A4.28 4.28 0 0 1 15.54 3h-3.09v12.4a2.59 2.59 0 0 1-2.59 2.5c-1.42 0-2.6-1.16-2.6-2.6 0-1.72 1.66-3.01 3.37-2.48V9.66c-3.45-.46-6.47 2.22-6.47 5.64 0 3.33 2.76 5.7 5.69 5.7 3.14 0 5.69-2.55 5.69-5.7V9.01a7.35 7.35 0 0 0 4.3 1.38V7.3s-1.88.09-3.24-1.48z"/></svg>',
  play:'<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 4l15 8-15 8z"/></svg>',
  trailer:'<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round" aria-hidden="true"><path d="M3.5 10h17v9.5a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5z"/><path d="M3.2 9.9 2.6 7.3a1.5 1.5 0 0 1 1.1-1.8l13.6-3.2a1.5 1.5 0 0 1 1.8 1.1l.6 2.6z"/><path d="m7.6 4.9 2.6 4.1M12.6 3.7l2.6 4.1"/></svg>'
};
function sortedSeasons(t){ return (t.seasons||[]).slice().sort((a,b)=>(a.number||0)-(b.number||0)).map(s=>({...s, episodes:(s.episodes||[]).slice().sort((a,b)=>(a.number||0)-(b.number||0))})); }
function renderTitlePage(id){
  const t = byId(id);
  if (!t || (t.status==='borrador' && !S.owner)) return `<div class="lock"><h1>No encontramos este título</h1><p>Puede que lo hayan quitado del catálogo.</p><a class="btn btn-line" href="/">Volver al inicio</a></div>`;
  const isSerie = t.type==='serie';
  const seasons = isSerie ? sortedSeasons(t) : [];
  const tabs = [...(isSerie && seasons.length ? [['episodios','Episodios']] : []), ['sugerencias','Sugerencias'], ['detalles','Detalles']];
  if (!tabs.some(x=>x[0]===S.tpTab)) S.tpTab = tabs[0][0];
  if (S.tpSeason >= seasons.length) S.tpSeason = 0;
  let firstEp = null, firstSeason = null;
  for (const s of seasons){ const e = s.episodes[0]; if (e){ firstEp = e; firstSeason = s; break; } }
  const target = playTarget(t);
  if (target && target.ep){ firstEp = target.ep; firstSeason = seasons.find(x => +x.number === +target.season) || firstSeason; }
  const link = target ? target.url : '';
  const years = t.yearEnd ? `${t.year}-${t.yearEnd}` : t.year;
  const meta = [years, isSerie && seasons.length ? `${seasons.length} temporada${seasons.length>1?'s':''}` : t.duration, (t.genres||[]).join(', ')].filter(Boolean).join(' • ');
  const playLabel = target ? target.label : 'Ver';
  return `<section class="tp-hero">
    <div class="tp-bg wide" ${t.backdrop||t.bannerImage||t.poster||t.backdropMobile?`data-src="${esc(t.backdrop||t.bannerImage||t.poster||t.backdropMobile)}"`:''} ${t.backdropMobile?`data-src-m="${esc(t.backdropMobile)}"`:''}>${genBlock(t)}</div>
    <div class="tp-copy">
      ${t.logo ? `<img class="tp-logo hero-logo" alt="${esc(t.title)}" data-img="${esc(t.logo)}" hidden><h1 class="logo-fallback" hidden>${esc(t.title)}</h1>` : `<h1>${esc(t.title)}</h1>`}
      <div class="chips-sm">${t.rating?`<span>${esc(t.rating)}</span>`:''}${isSoon(t)?'<span>Próximamente</span>':''}${(t.tags||[]).filter(x=>x.length<=6).slice(0,3).map(x=>`<span>${esc(x)}</span>`).join('')}</div>
      ${meta ? `<div class="tp-meta">${esc(meta)}</div>` : ''}
      ${isSerie && firstEp ? `<div class="tp-ep"><strong>T${esc(firstSeason.number)}:E${esc(firstEp.number)} ${esc(firstEp.title||'')}</strong>${firstEp.description?`<p>${esc(firstEp.description)}</p>`:''}</div>`
        : `<div class="tp-ep">${t.tagline?`<strong>${esc(t.tagline)}</strong>`:''}${t.description?`<p>${esc(t.description.length>180?t.description.slice(0,177)+'…':t.description)}</p>`:''}</div>`}
      <div class="tp-actions">
        ${isSoon(t) ? `<span class="btn btn-soon">${esc(soonLabel(t))}</span>` : link ? `<a class="btn btn-play" ${watchAttrs(t, target?.season, target?.episode)} href="${esc(link)}" target="_blank" rel="noopener">${ICONS.play} ${esc(playLabel)}</a>` : ''}
        ${listBtn(t.id,'round')}
        ${safeUrl(t.trailer) ? `<button class="round" data-trailer="${esc(t.id)}" aria-label="Ver tráiler" title="Tráiler">${ICONS.trailer}</button>` : ''}
      </div>
    </div>
  </section>
  <div class="tp-tabs" role="tablist">${tabs.map(([k,l])=>`<button role="tab" data-tptab="${k}" aria-selected="${S.tpTab===k}">${l}</button>`).join('')}</div>
  <section class="tp-panel">${S.tpTab==='episodios' ? renderEpisodesTab(t, seasons) : S.tpTab==='sugerencias' ? renderSuggTab(t) : renderDetailsTab(t, seasons)}</section>`;
}
function renderEpisodesTab(t, seasons){
  const s = seasons[S.tpSeason] || seasons[0]; if (!s) return '';
  return `${seasons.length>1 ? `<select class="pill-select" id="tpSeason" aria-label="Temporada">${seasons.map((x,i)=>`<option value="${i}" ${i===S.tpSeason?'selected':''}>Temporada ${esc(x.number)}${x.title?' · '+esc(x.title):''}</option>`).join('')}</select>` : `<span class="pill-select" style="background-image:none;padding-right:16px">Temporada ${esc(s.number)}</span>`}
    ${s.episodes.length ? `<div class="row-wrap ep-row"><button class="rail-btn l" data-scroll="-1" aria-label="Anteriores">‹</button><div class="rail rail-eps">${s.episodes.map(e => {
      const u = isSoon(t) ? '' : safeUrl(e.url); const tag = u ? 'a' : 'div';
      const seen = (S.progress.get(t.id)?.watched || []).includes(epKey(s.number, e.number));
      return `<${tag} class="ep-card ${seen?'seen':''}" ${u?`${watchAttrs(t, s.number, e.number)} href="${esc(u)}" target="_blank" rel="noopener"`:''}>
        <div class="ep-thumb" ${e.thumb?`data-src="${esc(e.thumb)}"`:''}>${genBlock({title:e.title||'Episodio '+e.number,id:t.id}, 'T'+s.number+' · E'+e.number, t.logo)}${seen?'<span class="seen-tag">✓ Visto</span>':''}</div>
        <div class="ep-meta"><span class="ep-label">Episodio ${esc(e.number)}</span><strong>${esc(e.title || 'Episodio '+e.number)}</strong></div>
        ${e.description?`<p>${esc(e.description)}</p>`:''}
        ${e.duration?`<div class="ep-d">${esc(e.duration)}</div>`:''}</${tag}>`; }).join('')}</div><button class="rail-btn r" data-scroll="1" aria-label="Siguientes">›</button></div>` : '<p class="empty-note" style="padding:0">Esta temporada todavía no tiene episodios.</p>'}`;
}
function renderSuggTab(t){
  const pub = published().filter(x=>x.id!==t.id);
  let list = pub.filter(x => (x.genres||[]).some(g => (t.genres||[]).includes(g)));
  if (list.length < 6) list = [...list, ...pub.filter(x=>!list.includes(x))].slice(0, 12);
  return list.length ? `<div class="sugg">${list.map(card).join('')}</div>` : '<p class="empty-note" style="padding:0">Todavía no hay otros títulos para sugerir.</p>';
}
function renderDetailsTab(t, seasons){
  const isSerie = t.type==='serie';
  const rows = [
    isSerie ? ['Temporadas', seasons.length ? `${seasons.length} · ${epCount(t)} episodios` : ''] : ['Duración', t.duration],
    ['Estreno', t.yearEnd ? `${t.year}-${t.yearEnd}` : t.year],
    ['Género', (t.genres||[]).join(', ')],
    ['Clasificación', t.rating],
    [isSerie ? 'Creación' : 'Dirección', t.director],
    ['Etiquetas', (t.tags||[]).join(', ')]
  ].filter(r => r[1]);
  const crew = String(t.director||'').split(',').map(x=>x.trim()).filter(Boolean).map(name => ({ name, role: isSerie ? 'Creación' : 'Dirección', photo:'' }));
  const people = [...(t.cast||[]).filter(c => c.name), ...crew];
  const initials = n => String(n||'?').split(/\s+/).map(w=>w[0]).slice(0,2).join('').toUpperCase();
  const castRail = people.length ? `<section class="cast-sec"><h2 class="section-h">Reparto y equipo</h2>
    <div class="row-wrap"><button class="rail-btn l" data-scroll="-1" aria-label="Anteriores">‹</button>
    <div class="rail rail-cast">${people.map(c => `<a class="cast-card" href="/persona/${esc(slugify(c.name))}" aria-label="Ver títulos con ${esc(c.name)}">
      <div class="cast-photo" ${c.photo?`data-src="${esc(c.photo)}"`:''}><span>${esc(initials(c.name))}</span></div>
      <strong>${esc(c.name)}</strong>${c.role?`<small>${esc(c.role)}</small>`:''}</a>`).join('')}</div>
    <button class="rail-btn r" data-scroll="1" aria-label="Siguientes">›</button></div></section>` : '';
  return castRail + `<div class="det"><div><h2>${esc(t.title)}</h2>${t.tagline?`<p style="font-weight:700;margin-bottom:10px">${esc(t.tagline)}</p>`:''}${t.description?`<p>${esc(t.description)}</p>`:''}</div>
    <dl>${rows.map(([k,v])=>`<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl></div>`;
}
function renderPerson(slug){
  let name = '', photo = '';
  const credits = [];
  published().forEach(t => {
    const roles = [];
    (t.cast||[]).forEach(c => { if (c.name && slugify(c.name) === slug){ name = name || c.name; photo = photo || c.photo; if (c.role) roles.push(c.role); else roles.push('Reparto'); } });
    String(t.director||'').split(',').map(x=>x.trim()).filter(Boolean).forEach(d => { if (slugify(d) === slug){ name = name || d; roles.push(t.type === 'serie' ? 'Creación' : 'Dirección'); } });
    if (roles.length) credits.push({ t, roles:[...new Set(roles)] });
  });
  if (!credits.length) return `<div class="lock"><h1>No encontramos a esta persona</h1><p>Puede que ya no tenga títulos en el catálogo.</p><a class="btn btn-line" href="/">Volver al inicio</a></div>`;
  credits.sort((a,b) => (+b.t.year||0) - (+a.t.year||0));
  const initials = String(name).split(/\s+/).map(w=>w[0]).slice(0,2).join('').toUpperCase();
  const series = credits.filter(c => c.t.type === 'serie').length, movies = credits.length - series;
  const count = [series ? `${series} serie${series>1?'s':''}` : '', movies ? `${movies} película${movies>1?'s':''}` : ''].filter(Boolean).join(' · ');
  return `<main><section class="person-head">
      <div class="cast-photo person-photo" ${photo?`data-src="${esc(photo)}"`:''}><span>${esc(initials)}</span></div>
      <div><span class="eyebrow">Reparto y equipo</span><h1>${esc(name)}</h1><p>${esc(count)} en REALITY VAULT</p></div>
    </section>
    <div class="grid person-grid">${credits.map(({ t, roles }) => `<div class="person-credit">${card(t)}<span class="credit-role">${esc(roles.join(' · '))}</span></div>`).join('')}</div>
  </main>`;
}
const DMCA_DEFAULT = "DMCA / Aviso legal y derechos de autor\nReality Vault es un catálogo de series y películas de reality. La información de cada título (descripciones, imágenes, reparto) proviene de TMDb y de fuentes públicas, y se usa solo con fines informativos.\nReality Vault no almacena archivos de video en sus propios servidores. Los videos que se reproducen en el sitio están alojados en servicios de terceros.\nRespetamos los derechos de autor. Si sos titular de derechos, o representás a quien lo es, y considerás que algún contenido accesible desde este sitio infringe tus derechos, escribinos al email de contacto que figura abajo con: tu nombre y datos de contacto, el título y el enlace exacto de la página del sitio donde aparece el contenido, una descripción de la obra protegida y una declaración de que la información es correcta y de que sos titular o estás autorizado a actuar en su nombre.\nAl recibir un reclamo completo, retiraremos el enlace o el contenido señalado del sitio en un plazo de hasta 72 horas y te responderemos por el mismo medio. También podés dirigir tu reclamo al servicio que aloja el video.\nLa inclusión de enlaces de terceros no implica que Reality Vault sea propietario de ese contenido. Las marcas, logos e imágenes pertenecen a sus respectivos dueños.\nEste aviso no excluye ni limita ninguna obligación legal que corresponda conforme a la legislación aplicable.\n© 2026 Reality Vault. Todos los derechos reservados.";
const DMCA_EMAIL_DEFAULT = 'valenrosario6@gmail.com';
function openDmca(){
  closeDmca();
  const raw = (S.site.dmcaText || DMCA_DEFAULT).trim();
  const lines = raw.split(/\n+/).map(l => l.trim()).filter(Boolean);
  const title = lines.length > 1 ? lines.shift() : 'DMCA';
  const mail = String(S.site.dmcaEmail || DMCA_EMAIL_DEFAULT).trim();
  const box = document.createElement('div'); box.className = 'trailer-pop dmca-pop'; box.setAttribute('role','dialog'); box.setAttribute('aria-modal','true'); box.setAttribute('aria-label', title);
  box.innerHTML = `<div class="tp-card dmca-card"><div class="tp-head"><strong>${esc(title)}</strong><button class="pl-btn" data-dmca-close aria-label="Cerrar">✕</button></div>
    <div class="dmca-body">${lines.map(l => `<p>${esc(l)}</p>`).join('')}${/^[^\s@]+@[^\s@]+$/.test(mail) ? `<p>Contacto: <a href="mailto:${esc(mail)}">${esc(mail)}</a></p>` : ''}</div></div>`;
  document.body.appendChild(box); document.body.style.overflow = 'hidden';
  box.querySelector('[data-dmca-close]').focus();
}
function closeDmca(){ const b = document.querySelector('.dmca-pop'); if (b){ b.remove(); document.body.style.overflow = ''; } }
document.addEventListener('click', e => {
  if (e.target.closest('[data-dmca]')){ e.preventDefault(); openDmca(); return; }
  if (e.target.closest('[data-dmca-close]') || e.target.classList?.contains('dmca-pop')) closeDmca();
});
document.addEventListener('keydown', e => { if (e.key === 'Escape' && document.querySelector('.dmca-pop')) closeDmca(); });
function renderFooter(){
  const st = S.site || {};
  const left = st.footerText ?? 'REALITY VAULT';
  const sub = st.footerSub ?? '';
  const ig = safeUrl(st.instagram), tt = safeUrl(st.tiktok);
  return `<footer><div class="foot-l"><div class="foot-brand">${st.footerLogo?`<img class="foot-logo" alt="" data-img="${esc(st.footerLogo)}" hidden>`:''}${left?`<span>${esc(left)}</span>`:''}</div>${sub?`<span style="color:var(--faint)">${esc(sub)}</span>`:''}<button class="foot-link" data-dmca>DMCA</button></div>
    <div class="foot-r">${ig||tt ? `<div class="social">${ig?`<a href="${esc(ig)}" target="_blank" rel="noopener" aria-label="Instagram">${ICONS.ig}</a>`:''}${tt?`<a href="${esc(tt)}" target="_blank" rel="noopener" aria-label="TikTok">${ICONS.tt}</a>`:''}</div>` : ''}</div></footer>`;
}

app.addEventListener('submit', async e => {
  if (e.target.matches('[data-qt-form]')){ e.preventDefault(); const q = document.getElementById('qt-q').value.trim(); if (q) tmdbQuickSearch(q); return; }
  if (e.target.matches('[data-quick-form]')){ e.preventDefault(); if (!S.editing || !S.editing.title.trim()){ toast('Poné un título para crearlo.', true); document.getElementById('f-title')?.focus(); return; } S.quick = false; S.catalogPicker = false; S.editorTab = 'info'; await saveTitle(); animatePanel(); return; }
  if (e.target.id === 'tmdbForm'){ e.preventDefault(); S.tmdb.specials = !!document.getElementById('tmdb-specials')?.checked; const q = document.getElementById('tmdb-q').value.trim(); if (q) tmdbSearch(q); return; }
  if (e.target.id !== 'loginForm') return;
  e.preventDefault();
  const msg = document.getElementById('loginMsg'); msg.textContent = ''; e.target.classList.remove('shake');
  const em = document.getElementById('login-email').value.trim(), pw = document.getElementById('login-pass').value;
  if (!em || !pw){ msg.textContent = 'Completá el email y la contraseña.'; void e.target.offsetWidth; e.target.classList.add('shake'); return; }
  const btn = e.target.querySelector('button[type="submit"]'); btn.disabled = true; btn.textContent = 'Entrando…';
  const { error } = await S.sb.auth.signInWithPassword({ email: document.getElementById('login-email').value.trim(), password: document.getElementById('login-pass').value });
  if (error){ msg.textContent = 'Email o contraseña incorrectos.'; btn.disabled = false; btn.textContent = 'Entrar al panel'; void e.target.offsetWidth; e.target.classList.add('shake'); }
});

app.addEventListener('click', e => {
  const b = e.target.closest('[data-toggle-pass]'); if (!b) return;
  const inp = document.getElementById('login-pass'); const show = inp.type === 'password';
  inp.type = show ? 'text' : 'password'; b.classList.toggle('on', show);
  b.setAttribute('aria-label', show ? 'Ocultar contraseña' : 'Mostrar contraseña'); inp.focus();
});

/* ---------- router ---------- */
/* ---------- viewer accounts & Mi lista ---------- */
S.myList = new Set();
const ICON_HEART_FILL = '<svg class="ic-heart" width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 20.5s-7.5-4.6-9.2-9.4C1.6 7.7 3.9 4.5 7.3 4.5c2 0 3.6 1.1 4.7 2.7 1.1-1.6 2.7-2.7 4.7-2.7 3.4 0 5.7 3.2 4.5 6.6-1.7 4.8-9.2 9.4-9.2 9.4z"/></svg>';
const ICON_HEART = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M12 20.5s-7.5-4.6-9.2-9.4C1.6 7.7 3.9 4.5 7.3 4.5c2 0 3.6 1.1 4.7 2.7 1.1-1.6 2.7-2.7 4.7-2.7 3.4 0 5.7 3.2 4.5 6.6-1.7 4.8-9.2 9.4-9.2 9.4z"/></svg>';
const ICON_PLUS = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>';
const ICON_CHECK = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
function listWide(id){
  const on = S.myList.has(id);
  return `<button class="btn list-wide ${on?'on':''}" data-mylist="${esc(id)}" data-wide="1" aria-pressed="${on}" aria-label="${on?'Quitar de Mi lista':'Agregar a Mi lista'}" title="${on?'Quitar de Mi lista':'Agregar a Mi lista'}">${on?ICON_CHECK:ICON_PLUS}</button>`;
}
function listBtn(id, cls){
  const on = S.myList.has(id);
  return `<button class="${cls||'round'} list-btn ${on?'on':''}" data-mylist="${esc(id)}" aria-pressed="${on}" aria-label="${on?'Quitar de Mi lista':'Agregar a Mi lista'}" title="${on?'Quitar de Mi lista':'Agregar a Mi lista'}">${on?ICON_CHECK:ICON_PLUS}</button>`;
}
const userName = () => (S.session?.user?.user_metadata?.name || S.session?.user?.email || '').trim();
/* --- Seguir viendo --- */
S.progress = new Map(); // title_id -> { last_season, last_episode, watched:[], updated_at }
const epKey = (s, e) => `${s}-${e}`;
async function loadProgress(){
  S.progress = new Map();
  if (!S.session || !S.sb) return;
  const { data, error } = await S.sb.from('watch_progress').select('title_id,last_season,last_episode,watched,updated_at').order('updated_at', { ascending:false });
  if (!error) (data||[]).forEach(r => S.progress.set(r.title_id, { ...r, watched: r.watched || [] }));
}
function flatEpisodes(t){ const out = []; sortedSeasons(t).forEach(s => s.episodes.forEach(e => out.push({ s, e }))); return out; }
// What "Ver" should play: next episode after the last one opened, else the first one with a link.
function playTarget(t){
  if (isSoon(t)) return null;
  if (t.type !== 'serie') return safeUrl(t.url) ? { url: t.url, label: S.progress.has(t.id) ? 'Volver a ver' : 'Ver', movie:true } : null;
  const eps = flatEpisodes(t).filter(x => safeUrl(x.e.url));
  if (!eps.length) return null;
  const p = S.progress.get(t.id);
  let i = 0, cont = false;
  if (p){
    const j = eps.findIndex(x => +x.s.number === +p.last_season && +x.e.number === +p.last_episode);
    if (j >= 0){ i = j + 1 < eps.length ? j + 1 : j; cont = true; }
  }
  const { s, e } = eps[i];
  return { url: e.url, season: s.number, episode: e.number, ep: e, label: `${cont ? 'Continuar' : 'Ver'} T${s.number}:E${e.number}`, cont };
}
function watchAttrs(t, season, episode){
  return `data-watch data-tid="${esc(t.id)}" ${season != null ? `data-s="${esc(season)}" data-e="${esc(episode)}"` : ''}`;
}
async function recordWatch(tid, season, episode){
  if (!S.session || !S.sb || !tid) return;
  const prev = S.progress.get(tid);
  const watched = new Set(prev?.watched || []);
  if (season != null && season !== '') watched.add(epKey(season, episode));
  const row = { user_id: S.session.user.id, title_id: tid, last_season: season === '' || season == null ? null : +season, last_episode: episode === '' || episode == null ? null : +episode, watched: [...watched], updated_at: new Date().toISOString() };
  S.progress.delete(tid); S.progress = new Map([[tid, row], ...S.progress]);
  const { error } = await S.sb.from('watch_progress').upsert(row);
  setTimeout(loadTop10, 800);
  if (error) console.warn('No se pudo guardar el progreso', error.message);
  setTimeout(() => { const y = window.scrollY; render(); window.scrollTo(0, y); }, 400);
}
async function removeProgress(tid){
  S.progress.delete(tid); render();
  await S.sb.from('watch_progress').delete().eq('user_id', S.session.user.id).eq('title_id', tid);
  toast('Quitado de Seguir viendo');
}
// Horizontal artwork for Mi lista / Seguir viendo: the "Miniatura horizontal" or the backdrop with the logo on top.
function wideArt(t, extra){
  const img = t.thumbWide || t.backdrop || t.bannerImage || t.poster;
  return `<div class="wide-art" ${img ? `data-src="${esc(img)}"` : ''}>${genBlock(t)}${extra||''}</div>`;
}
function listRow(){
  if (!S.session) return '';
  const list = shuffled([...S.myList].map(byId).filter(t => t && t.status !== 'borrador'), 'list');
  if (!list.length) return '';
  return `<section class="row"><div class="row-head"><h2 class="h-heart">${ICON_HEART_FILL} Mi lista</h2><span class="count">${list.length}</span><a class="see-all" href="/mi-lista">Ver todo ›</a></div>
    <div class="row-wrap"><button class="rail-btn l" data-scroll="-1" aria-label="Anterior">‹</button>
    <div class="rail rail-wide">${list.map(wideCard).join('')}</div>
    <button class="rail-btn r" data-scroll="1" aria-label="Siguiente">›</button></div></section>`;
}
function wideCard(t){
  return `<div class="wide-card"><button class="wide-main" data-open="${esc(t.id)}" aria-label="${esc(t.title)}">${wideArt(t)}</button>
    <button class="wa-heart" data-mylist="${esc(t.id)}" data-heart="1" aria-label="Quitar de Mi lista" title="Quitar de Mi lista">${ICON_HEART_FILL}</button></div>`;
}
/* --- Top 10 hoy --- */
S.top10 = [];
async function loadTop10(){
  if (!S.sb) return;
  const { data, error } = await S.sb.rpc('top10');
  if (error || !Array.isArray(data)) return;
  const ids = data.map(r => r.title_id);
  if (ids.join() === S.top10.join()) return;
  S.top10 = ids;
  if (S.route === 'home' && !S.q.trim() && !S.playerOpen){ const y = window.scrollY; render(); window.scrollTo(0, y); }
}
// count a visit to a title page (once per title every 10 minutes in this browser) for "Top 10 hoy"
function viewerId(){
  if (S.session?.user?.id) return S.session.user.id;
  try { let v = localStorage.getItem('rv-viewer'); if (!v){ v = 'a-' + uid(''); localStorage.setItem('rv-viewer', v); } return v; } catch { return 'a-' + uid(''); }
}
function trackView(tid){
  if (tid && byId(tid)) rememberOpened(tid);
  if (!tid || !S.sb || !byId(tid)) return;
  const key = 'rv-seen-' + tid; let last = 0;
  try { last = +localStorage.getItem(key) || 0; } catch {}
  if (Date.now() - last < 600e3) return;
  try { localStorage.setItem(key, String(Date.now())); } catch {}
  S.sb.from('title_views').insert({ title_id: tid, viewer: viewerId() }).then(({ error }) => { if (!error) setTimeout(loadTop10, 600); });
}
function top10Row(){
  const list = S.top10.map(byId).filter(t => t && t.status !== 'borrador').slice(0, 10);
  if (!list.length) return '';
  return `<section class="row top10"><div class="row-head"><h2>Top 10 hoy</h2></div>
    <div class="row-wrap"><button class="rail-btn l" data-scroll="-1" aria-label="Anterior">‹</button>
    <div class="rail rail-top">${list.map((t, i) => `<button class="t10" data-open="${esc(t.id)}" aria-label="${i+1}. ${esc(t.title)}">
      <span class="t10-num ${i===9?'ten':''}" aria-hidden="true">${i+1}</span>
      <span class="poster" ${t.poster?`data-src="${esc(t.poster)}"`:''}>${genBlock(t)}</span></button>`).join('')}</div>
    <button class="rail-btn r" data-scroll="1" aria-label="Siguiente">›</button></div></section>`;
}
function continueRow(){
  if (!S.session) return '';
  const items = shuffled([...S.progress.values()].map(p => ({ p, t: byId(p.title_id) })).filter(x => x.t && x.t.status !== 'borrador'), 'cont').slice(0, 20);
  if (!items.length) return '';
  return `<section class="row"><div class="row-head"><h2>Seguir viendo</h2><span class="count">${items.length}</span></div>
    <div class="row-wrap"><button class="rail-btn l" data-scroll="-1" aria-label="Anterior">‹</button>
    <div class="rail rail-wide">${items.map(({ p, t }) => {
      const target = playTarget(t);
      const total = t.type === 'serie' ? flatEpisodes(t).length : 1;
      const seen = t.type === 'serie' ? Math.min((p.watched||[]).length, total) : 1;
      const pct = total ? Math.max(6, Math.round(seen / total * 100)) : 0;

      const sub = t.type === 'serie' ? (target ? (target.cont ? 'Sigue ' : '') + `T${target.season}:E${target.episode}${target.ep?.title ? ' · ' + target.ep.title : ''}` : `${seen} de ${total} episodios`) : 'Película';
      const inner = wideArt(t, `<span class="cw-play">${ICONS.play}</span><span class="cw-bar"><i style="width:${pct}%"></i></span>`);
      return `<div class="cw-card">
        ${target ? `<a class="cw-main" ${watchAttrs(t, target.season, target.episode)} href="${esc(target.url)}" target="_blank" rel="noopener" aria-label="${esc(target.label + ' · ' + t.title)}">${inner}</a>` : `<button class="cw-main" data-open="${esc(t.id)}">${inner}</button>`}
        <div class="cw-info"><button class="cw-title" data-open="${esc(t.id)}">${esc(t.title)}</button><span class="cw-sub">${esc(sub)}</span></div>
        <button class="cw-x" data-cw-remove="${esc(t.id)}" aria-label="Quitar de Seguir viendo" title="Quitar">✕</button>
      </div>`; }).join('')}</div>
    <button class="rail-btn r" data-scroll="1" aria-label="Siguiente">›</button></div></section>`;
}

async function loadMyList(){
  S.myList = new Set();
  if (!S.session || !S.sb) return;
  const { data, error } = await S.sb.from('user_lists').select('title_id').order('created_at', { ascending:false });
  if (!error) S.myList = new Set((data||[]).map(r => r.title_id));
}
async function toggleList(id){
  if (!S.session){ openAuth({ reason:'list', titleId:id }); return; }
  const on = S.myList.has(id);
  on ? S.myList.delete(id) : (S.myList = new Set([id, ...S.myList]));
  refreshListButtons();
  const q = on ? S.sb.from('user_lists').delete().eq('user_id', S.session.user.id).eq('title_id', id)
               : S.sb.from('user_lists').insert({ user_id: S.session.user.id, title_id: id });
  const { error } = await q;
  if (error){ on ? S.myList.add(id) : S.myList.delete(id); refreshListButtons(); toast('No se pudo actualizar tu lista', true); return; }
  toast(on ? 'Quitado de Mi lista' : 'Agregado a Mi lista');
  if (S.route === 'mi-lista') render();
}
function refreshListButtons(){
  if (document.querySelector('[data-heart]')){ const y = window.scrollY; render(); window.scrollTo(0, y); return; }
  document.querySelectorAll('[data-mylist]').forEach(b => { const tmp = document.createElement('div'); tmp.innerHTML = b.dataset.wide ? listWide(b.dataset.mylist) : listBtn(b.dataset.mylist, 'round'); b.replaceWith(tmp.firstElementChild); });
}
function renderMyList(){
  if (!S.session) return `<div class="lock"><h1>Mi lista</h1><p>Creá una cuenta gratis para guardar tus series y películas.</p><button class="btn btn-primary" data-auth="signup">Crear cuenta</button><button class="btn btn-line" data-auth="login">Ya tengo cuenta</button></div>`;
  const list = shuffled([...S.myList].map(byId).filter(t => t && t.status !== 'borrador'), 'list');
  return mobileChips('mi-lista') + `<main><div class="page-head"><h1 class="h-heart">${ICON_HEART_FILL} Mi lista</h1><p>${list.length} título${list.length===1?'':'s'}</p></div>
    ${list.length ? `<div class="grid grid-wide">${list.map(wideCard).join('')}</div>` : '<p class="empty-note">Todavía no agregaste nada. Tocá el botón + en cualquier serie o película para guardarla acá.</p>'}</main>`;
}
function userHue(){
  const id = S.session?.user?.id || S.session?.user?.email || 'x';
  let h = 0; for (const ch of id) h = (h * 33 + ch.charCodeAt(0)) >>> 0;
  return h % 360;
}
const STAR_SVG = '<svg class="ga-star" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.6l2.75 6.05 6.6.7-4.95 4.45 1.4 6.5L12 16.95 6.2 20.3l1.4-6.5L2.65 9.35l6.6-.7z"/></svg>';
function glitterAvatar(size){ return `<span class="glitter-av ${size||''}" style="--h:${userHue()}" aria-hidden="true"><span class="ga-sparkle"></span>${STAR_SVG}</span>`; }
function renderAccount(){
  const box = document.getElementById('account'); if (!box) return;
  document.querySelectorAll('[data-r="mi-lista"]').forEach(a => a.hidden = !S.session);
  if (!S.session){ box.innerHTML = S.sb ? `<a class="icon-btn heart-m" href="/mi-lista" aria-label="Mi lista">${ICON_HEART}</a><button class="btn btn-sm btn-enter" data-auth="login">Entrar</button>` : ''; return; }
  const n = userName();
  box.innerHTML = `<a class="icon-btn heart-m" href="/mi-lista" aria-label="Mi lista">${ICON_HEART}</a><button class="avatar-btn" data-account-menu aria-haspopup="true" aria-expanded="false" title="${esc(n)}" aria-label="Tu cuenta">${glitterAvatar()}</button>
    <div class="acc-menu" hidden><div class="acc-name">${glitterAvatar('lg')}<span>${esc(n)}</span></div>
      <a href="/mi-lista">Mi lista</a>${S.owner ? '<a href="/admin">Panel de control</a>' : ''}<button data-logout>Cerrar sesión</button></div>`;
}
document.addEventListener('click', e => {
  const m = document.querySelector('.acc-menu');
  const btn = e.target.closest('[data-account-menu]');
  if (btn){ const open = m.hidden; m.hidden = !open; btn.setAttribute('aria-expanded', open); return; }
  if (m && !m.hidden && !e.target.closest('.acc-menu')) m.hidden = true;
  if (m && e.target.closest('.acc-menu a, .acc-menu button')) m.hidden = true;
});
const LANDING_LOGO = 'https://kfyxwoezrzboikbvpdtv.supabase.co/storage/v1/object/public/media/sitelogo/mmv6hs7ss2u4hxm.webp';

/* ---------- landing para visitantes ---------- */
const isLanding = () => S.route==='home' && !S.session && !S.q.trim() && !S.noDb && !!S.sb && S.authReady && S.loaded.t && published().length > 0;
function renderLanding(){
  const pubs = published();
  const withPoster = pubs.filter(t => t.poster);
  const cards = pubs.filter(t => t.backdrop || t.bannerImage);
  const fill = (arr, n) => { if (!arr.length) return []; const out = []; while (out.length < n) out.push(...arr); return out; };
  const pool = fill(shuffled(withPoster,'land-wall'), 63);
  const col = c => { const ps = pool.slice(c*7, c*7+7).map(t => `<span class="lw-p" data-src="${esc(t.poster)}"></span>`).join(''); return `<div class="lw-col${c%2?' up':''}" style="--off:${(c*41)%80}px">${ps}${ps}</div>`; };
  const wall = withPoster.length ? `<div class="lw-wall" aria-hidden="true">${[0,1,2,3,4,5,6,7,8].map(col).join('')}</div>` : '';
  const card = t => `<button class="lw-card" data-land-open="${esc(t.id)}" aria-label="${esc(t.title)}"><span class="lw-bg" data-src="${esc(t.backdrop||t.bannerImage)}"></span><span class="lw-sh"></span>${t.logo ? `<img class="lw-logo" alt="${esc(t.title)}" data-img="${esc(t.logo)}" hidden><strong class="lw-name logo-fallback" hidden>${esc(t.title)}</strong>` : `<strong class="lw-name">${esc(t.title)}</strong>`}</button>`;
  const sh = shuffled(cards,'land-rows'); const half = Math.ceil(sh.length/2);
  const mk = (arr, cls) => { const f = fill(arr, 8); return arr.length ? `<div class="lw-mq ${cls}"><div class="lw-trk">${f.map(card).join('')}${f.map(card).join('')}</div></div>` : ''; };
  const rows = mk(sh.slice(0, half), '') + mk(sh.slice(half).length ? sh.slice(half) : sh, 'r');
  const nS = pubs.filter(t=>t.type==='serie').length, nP = pubs.length - nS, nE = pubs.reduce((n,t)=>n+epCount(t),0);
  const round = n => { if (n < 5) return String(n); const step = n < 50 ? 5 : n < 200 ? 10 : n < 1000 ? 50 : 100; return (Math.floor(n/step)*step) + '+'; };
  const faq = [
    ['¿Qué es REALITY VAULT?','Un sitio para ver realities, series, películas, documentales y especiales, todo reunido en un solo lugar y en español.'],
    ['¿Tiene costo?','No. Crear la cuenta es gratis y tarda un minuto.'],
    ['¿Dónde puedo mirar?','Desde el celular, la compu o la tele. Entrás con tu cuenta y listo.'],
    ['¿Retomo donde lo dejé?','Sí. Guardamos tu avance y tu lista, así seguís exactamente donde quedaste.'],
    ['¿Se suman títulos nuevos?','Todo el tiempo. Lo que se agrega al catálogo aparece enseguida.']
  ];
  const bgs = shuffled(cards,'land-end').slice(0,6);
  return `<div class="landing">
    <section class="ld-hero">${wall}<div class="ld-veil"></div>
      <div class="ld-c"><img class="ld-logo" src="${LANDING_LOGO}" alt="REALITY VAULT"><h1>Todo el universo de la reality TV, en un solo lugar.</h1><p>Series, películas y especiales. Mirá cuando quieras, donde quieras.</p>
        <div class="ld-cta"><button class="btn btn-primary ld-btn" data-auth="signup">Crear cuenta</button><button class="btn btn-line ld-btn" data-auth="login">Iniciar sesión</button></div></div>
      <div class="ld-hint">DESLIZÁ ↓</div></section>
    <section class="ld-about ld-rv"><div class="ld-k">¿QUÉ ES REALITY VAULT?</div><h2>El archivo de la reality TV, ahora para mirar.</h2>
      <p>Un lugar propio para ver los realities que marcaron una época y los que están por venir: series completas, películas, documentales y especiales, todo junto y en español.</p>
      <div class="ld-stats"><div><b>${round(nS)}</b><span>series</span></div><div><b>${round(nP)}</b><span>películas</span></div>${nE?`<div><b>${round(nE)}</b><span>episodios</span></div>`:''}</div></section>
    ${rows ? `<section class="ld-rows ld-rv">${rows}</section>` : ''}
    <section class="ld-faq ld-rv"><h2>Preguntas frecuentes</h2>${faq.map(([q,a])=>`<details><summary>${q}</summary><p>${a}</p></details>`).join('')}</section>
    <section class="ld-end ld-rv">${bgs.map((t,i)=>`<div class="ld-endbg${i===0?' on':''}" data-src="${esc(t.backdrop||t.bannerImage)}"></div>`).join('')}<div class="ld-endv"></div>
      <div class="ld-endc"><h2>Empezá en un minuto.</h2><p>Gratis. Entrás y ya está.</p><button class="btn btn-primary ld-btn" data-auth="signup">Crear cuenta</button></div></section>
    <footer class="ld-foot"><img src="${LANDING_LOGO}" alt="REALITY VAULT"><span>© REALITY VAULT</span></footer>
  </div>`;
}
function startLanding(){
  clearInterval(S.landTimer); S.landTimer = null;
  const root = document.querySelector('.landing'); if (!root) return;
  const bgs = [...root.querySelectorAll('.ld-endbg')];
  if (bgs.length > 1){ let i = 0; S.landTimer = setInterval(() => { if (!document.body.contains(bgs[0])){ clearInterval(S.landTimer); return; } bgs[i].classList.remove('on'); i = (i+1) % bgs.length; bgs[i].classList.add('on'); }, 4500); }
  if ('IntersectionObserver' in window){
    const io = new IntersectionObserver(es => es.forEach(en => { if (en.isIntersecting){ en.target.classList.add('in'); io.unobserve(en.target); } }), { threshold:.12 });
    root.querySelectorAll('.ld-rv').forEach(el => io.observe(el));
  } else root.querySelectorAll('.ld-rv').forEach(el => el.classList.add('in'));
}
function openLandingTitle(id, seasonIdx){
  const t = byId(id); if (!t) return;
  const seasons = (t.seasons||[]).slice().sort((a,b)=>(a.number||0)-(b.number||0));
  const si = Math.min(seasonIdx||0, Math.max(0, seasons.length-1)); const sn = seasons[si];
  const eps = sn ? (sn.episodes||[]).slice().sort((a,b)=>(a.number||0)-(b.number||0)) : [];
  const first = !!overlayRoot.querySelector('.lp');
  const art = t.backdrop || t.bannerImage || t.poster;
  overlayRoot.innerHTML = `<div class="lp-ov${first?' still':''}" data-close-bg><article class="lp" role="dialog" aria-modal="true" aria-label="${esc(t.title)}">
    <div class="lp-hero" ${art?`data-src="${esc(art)}"`:''}><div class="lp-hv"></div><button class="lp-x" data-close aria-label="Cerrar">✕</button>
      <div class="lp-ht">${t.logo ? `<img class="lp-hlogo" alt="${esc(t.title)}" data-img="${esc(t.logo)}" hidden><h2 class="logo-fallback" hidden>${esc(t.title)}</h2>` : `<h2>${esc(t.title)}</h2>`}
        <div class="lp-meta">${[t.type==='serie'?'Serie':'Película', t.year, t.rating, t.type==='serie' ? ((t.seasons||[]).length ? (t.seasons.length+' temporada'+(t.seasons.length>1?'s':'')) : '') : t.duration].filter(Boolean).map(x=>`<span>${esc(x)}</span>`).join('')}</div></div></div>
    <div class="lp-body">
      ${(t.genres||[]).length ? `<div class="lp-gen">${t.genres.slice(0,4).map(g=>`<span>${esc(g)}</span>`).join('')}</div>` : ''}
      <p class="lp-desc">${esc(t.description || '')}</p>
      ${t.type==='serie' && seasons.length ? `<div class="lp-eph"><h3>Episodios</h3>${seasons.length>1 ? `<div class="lp-ss">${seasons.map((x,i)=>`<button data-lp-season="${i}" data-lp-id="${esc(t.id)}" aria-pressed="${i===si}">T${esc(x.number||i+1)}</button>`).join('')}</div>` : ''}</div>
        <div class="lp-eps">${eps.slice(0,30).map((ep,i)=>`<div class="lp-ep" style="--i:${Math.min(i,12)}"><span class="lp-et" ${ep.thumb?`data-src="${esc(ep.thumb)}"`:''}><b>${esc(ep.number||i+1)}</b></span><div><strong>${esc(ep.title||('Episodio '+(ep.number||i+1)))}</strong>${ep.description?`<small>${esc(ep.description)}</small>`:''}</div>${ep.duration?`<em>${esc(ep.duration)}</em>`:''}</div>`).join('') || '<p class="lp-none">Los episodios llegan pronto.</p>'}</div>` : ''}
      <div class="lp-cta"><button class="btn btn-primary" data-auth="signup">Crear cuenta para ver</button><button class="btn btn-line" data-auth="login">Ya tengo cuenta</button></div>
    </div></article></div>`;
  document.body.style.overflow = 'hidden';
  hydrate(overlayRoot); logoFallbacks(overlayRoot);
}
document.addEventListener('click', e => {
  const o = e.target.closest('[data-land-open]'); if (o){ e.preventDefault(); openLandingTitle(o.dataset.landOpen); return; }
  const ss = e.target.closest('[data-lp-season]'); if (ss){ const box = overlayRoot.querySelector('.lp'); const y = box?.scrollTop||0; openLandingTitle(ss.dataset.lpId, +ss.dataset.lpSeason); const b2 = overlayRoot.querySelector('.lp'); if (b2) b2.scrollTop = y; }
});
function openAuth(opts = {}){
  S.authCtx = { mode: opts.mode || 'signup', step: 1, email:'', name:'', favs:[], reason: opts.reason || '', href: opts.href || '', titleId: opts.titleId || '', tid: opts.tid || '', s: opts.s ?? '', e: opts.e ?? '', msg:'' };
  renderAuth();
}
function authSideTitle(){
  const pool = published().filter(t => t.backdrop || t.bannerImage);
  return pool.length ? shuffled(pool, 'auth-side')[0] : null;
}
function renderAuth(){
  const c = S.authCtx; const signup = c.mode === 'signup';
  const side = c._side || (c._side = authSideTitle());
  const lead = c.reason === 'watch' ? 'Para ver necesitás una cuenta. Es gratis y tarda un minuto.' : c.reason === 'list' ? 'Creá una cuenta gratis para guardar tus series y películas en Mi lista.' : '';
  let body;
  if (c.done){
    body = `<h2>¡Listo${c.doneName ? ', ' + esc(c.doneName) : ''}!</h2><p class="au-sub">${esc(c.doneMsg)}</p>
      ${c.href ? `<a class="btn btn-primary au-btn" data-watch ${c.tid ? `data-tid="${esc(c.tid)}"` : ''} ${c.s !== '' ? `data-s="${esc(c.s)}" data-e="${esc(c.e)}"` : ''} href="${esc(c.href)}" target="_blank" rel="noopener">▶ Ver ahora</a>` : `<button class="btn btn-primary au-btn" data-close>Seguir</button>`}`;
  } else if (signup && c.step === 3){
    const pool = shuffled(published().filter(t => t.poster), 'au-fav').slice(0, 12);
    body = `<div class="au-step">PASO 3 DE 3</div><div class="au-prog"><i class="on"></i><i class="on"></i><i class="on"></i></div>
      <h2>Armemos tu inicio</h2><p class="au-sub">Elegí lo que te gusta y lo guardamos en Mi lista.</p>
      <div class="au-pk">${pool.map(t => `<button type="button" class="au-pp${c.favs.includes(t.id)?' s':''}" data-au-fav="${esc(t.id)}" aria-pressed="${c.favs.includes(t.id)}" aria-label="${esc(t.title)}"><span data-src="${esc(t.poster)}"></span><b>✓</b></button>`).join('')}</div>
      <button class="btn btn-primary au-btn" data-au-finish>${c.favs.length ? 'Guardar y entrar' : 'Entrar a REALITY VAULT'}</button>
      <div class="au-foot">${c.favs.length ? `${c.favs.length} elegido${c.favs.length>1?'s':''}` : 'Elegí los que quieras'} · <button class="au-link" data-au-finish data-skip>Saltar</button></div>`;
  } else if (signup && c.step === 2){
    body = `<div class="au-step">PASO 2 DE 3</div><div class="au-prog"><i class="on"></i><i class="on"></i><i></i></div>
      <h2>Casi listo</h2><p class="au-sub">Un último paso para tener tu cuenta.</p>
      <form id="authForm" class="au-form" novalidate>
        <label class="au-field"><span>¿Cómo te llamamos?</span><input class="au-in" id="au-name" autocomplete="name" required maxlength="40" value="${esc(c.name)}"></label>
        <label class="au-field"><span>Contraseña</span><input class="au-in" id="au-pass" type="password" autocomplete="new-password" minlength="6" required><em>Mínimo 6 caracteres</em></label>
        <button class="btn btn-primary au-btn" type="submit">Crear mi cuenta</button>
        <p class="auth-msg" id="authMsg">${esc(c.msg||'')}</p>
      </form><div class="au-foot"><button class="au-link" data-au-back>← Volver</button></div>`;
  } else if (signup){
    body = `<div class="au-step">PASO 1 DE 3</div><div class="au-prog"><i class="on"></i><i></i><i></i></div>
      <h2>Creá tu cuenta gratis</h2><p class="au-sub">${esc(lead || 'Es gratis y tarda un minuto.')}</p>
      <form id="authForm" class="au-form" novalidate>
        <label class="au-field"><span>Email</span><input class="au-in" id="au-email" type="email" autocomplete="email" required value="${esc(c.email)}" placeholder="vos@email.com"></label>
        <button class="btn btn-primary au-btn" type="submit">Continuar</button>
        <p class="auth-msg" id="authMsg">${esc(c.msg||'')}</p>
      </form><div class="au-foot">¿Ya tenés cuenta? <button class="au-link" data-auth-mode="login">Ingresá</button></div>`;
  } else {
    body = `<h2>Qué bueno verte de nuevo</h2><p class="au-sub">${esc(lead || 'Entrá con tu cuenta.')}</p>
      <form id="authForm" class="au-form" novalidate>
        <label class="au-field"><span>Email</span><input class="au-in" id="au-email" type="email" autocomplete="email" required value="${esc(c.email)}"></label>
        <label class="au-field"><span>Contraseña</span><input class="au-in" id="au-pass" type="password" autocomplete="current-password" required></label>
        <button class="btn btn-primary au-btn" type="submit">Ingresar</button>
        <p class="auth-msg" id="authMsg">${esc(c.msg||'')}</p>
      </form><div class="au-foot">¿No tenés cuenta? <button class="au-link" data-auth-mode="signup">Creá una gratis</button></div>`;
  }
  const wide = signup && c.step === 3 && !c.done;
  const prev = overlayRoot.querySelector('.au-full');
  if (prev){
    const box = prev.querySelector('.au-box');
    box.className = 'au-box' + (wide ? ' wide' : '');
    box.innerHTML = body; box.style.animation = 'none'; void box.offsetWidth; box.style.animation = '';
    hydrate(box);
    (box.querySelector('#au-name') || box.querySelector('#au-email') || box.querySelector('.au-btn'))?.focus();
    return;
  }
  overlayRoot.innerHTML = `<div class="au-full" role="dialog" aria-modal="true" aria-label="${signup?'Crear cuenta':'Ingresar'}">
    <div class="au-side" ${side ? `data-src="${esc(side.backdrop||side.bannerImage)}"` : ''}><div class="au-side-t">${side ? (side.logo ? `<img alt="${esc(side.title)}" data-img="${esc(side.logo)}" hidden><h3 class="logo-fallback" hidden>${esc(side.title)}</h3>` : `<h3>${esc(side.title)}</h3>`) : ''}<p>${esc(side && side.tagline ? side.tagline : 'Series, películas y especiales, todo en un solo lugar.')}</p></div></div>
    <img class="au-logo" src="${LANDING_LOGO}" alt="REALITY VAULT">
    <button class="au-x" data-close aria-label="Cerrar">✕</button>
    <div class="au-form-wrap"><div class="au-box${wide?' wide':''}" key="${c.mode}${c.step}${c.done?'d':''}">${body}</div></div>
  </div>`;
  document.body.style.overflow = 'hidden';
  hydrate(overlayRoot); logoFallbacks(overlayRoot);
  (overlayRoot.querySelector('#au-name') || overlayRoot.querySelector('#au-email') || overlayRoot.querySelector('.au-btn'))?.focus();
}
overlayRoot.addEventListener('click', async e => {
  const c = S.authCtx; if (!c || !overlayRoot.querySelector('.au-full')) return;
  const m = e.target.closest('[data-auth-mode]'); if (m){ c.mode = m.dataset.authMode; c.step = 1; c.msg=''; renderAuth(); return; }
  if (e.target.closest('[data-au-back]')){ c.step = 1; c.msg=''; renderAuth(); return; }
  const f = e.target.closest('[data-au-fav]'); if (f){ const id = f.dataset.auFav; const adding = !c.favs.includes(id); c.favs = adding ? [...c.favs, id] : c.favs.filter(x=>x!==id); f.classList.toggle('s'); f.setAttribute('aria-pressed', adding);
    f.classList.remove('pop'); void f.offsetWidth; if (adding){ f.classList.add('pop'); for (let i=0;i<10;i++){ const p = document.createElement('i'); p.className='au-spark'; const a = (i/10)*Math.PI*2 + Math.random()*.5, d = 38 + Math.random()*34; p.style.setProperty('--dx', Math.cos(a)*d+'px'); p.style.setProperty('--dy', Math.sin(a)*d+'px'); p.style.background = ['#ff2e93','#ff9a5a','#fff','#ffd36b'][i%4]; f.appendChild(p); setTimeout(()=>p.remove(), 700); } }
    const fin = overlayRoot.querySelector('.au-btn[data-au-finish]'); if (fin) fin.textContent = c.favs.length ? 'Guardar y entrar' : 'Entrar a REALITY VAULT';
    const ft = overlayRoot.querySelector('.au-foot'); if (ft) ft.firstChild.textContent = (c.favs.length ? `${c.favs.length} elegido${c.favs.length>1?'s':''}` : 'Elegí los que quieras') + ' · '; return; }
  const fin = e.target.closest('[data-au-finish]'); if (fin){
    if (!fin.hasAttribute('data-skip') && c.favs.length && S.session){
      const rows = c.favs.filter(id => !S.myList.has(id)).map(id => ({ user_id: S.session.user.id, title_id: id }));
      if (rows.length){ const { error } = await S.sb.from('user_lists').insert(rows); if (!error){ S.myList = new Set([...rows.map(r=>r.title_id), ...S.myList]); toast('Guardamos tus favoritas en Mi lista'); } }
    }
    if (c.href){ c.done = true; c.doneMsg = 'Tu cuenta está creada.'; renderAuth(); } else closeDetail();
    return; }
});
overlayRoot.addEventListener('submit', async e => {
  if (e.target.id !== 'authForm') return;
  e.preventDefault();
  const c = S.authCtx; const btn = e.target.querySelector('button[type="submit"]'); const msg = document.getElementById('authMsg');
  try {
    if (c.mode === 'signup' && c.step === 1){
      const email = document.getElementById('au-email').value.trim();
      if (!/^\S+@\S+\.\S+$/.test(email)){ msg.textContent = 'Revisá el email, no parece válido.'; return; }
      c.email = email; c.step = 2; c.msg = ''; renderAuth(); return;
    }
    btn.disabled = true; msg.textContent = '';
    if (c.mode === 'signup'){
      const name = document.getElementById('au-name').value.trim(); const password = document.getElementById('au-pass').value; c.name = name;
      if (!name){ msg.textContent = 'Decinos cómo te llamamos.'; btn.disabled = false; return; }
      if (password.length < 6){ msg.textContent = 'La contraseña necesita al menos 6 caracteres.'; btn.disabled = false; return; }
      const { data, error } = await S.sb.auth.signUp({ email:c.email, password, options:{ data:{ name } } });
      if (error) throw error;
      if (!data.session){ c.done = true; c.doneName = name; c.doneMsg = 'Te mandamos un email para confirmar tu cuenta. Abrilo, tocá el enlace y después ingresá.'; c.href=''; renderAuth(); return; }
      c.doneName = name; await afterLogin();
      if (c.titleId && c.reason === 'list' && !S.myList.has(c.titleId)) await toggleList(c.titleId);
      c.step = 3; renderAuth(); return;
    }
    const email = document.getElementById('au-email').value.trim(), password = document.getElementById('au-pass').value; c.email = email;
    const { error } = await S.sb.auth.signInWithPassword({ email, password });
    if (error) throw error;
    c.done = true; c.doneName = ''; c.doneMsg = 'Ya ingresaste.';
    await afterLogin();
    if (c.titleId && c.reason === 'list' && !S.myList.has(c.titleId)) await toggleList(c.titleId);
    if (!c.href){ closeDetail(); return; }
    renderAuth();
  } catch(err){
    const m = String(err.message||'');
    if (msg) msg.textContent = /Invalid login/i.test(m) ? 'Email o contraseña incorrectos.' : /already registered|already been registered/i.test(m) ? 'Ese email ya tiene una cuenta. Tocá “Ingresá”.' : /Email not confirmed/i.test(m) ? 'Tenés que confirmar tu email antes de ingresar. Revisá tu correo.' : /signups not allowed|Signups not allowed/i.test(m) ? 'El registro está desactivado por ahora.' : /rate limit/i.test(m) ? 'Demasiados intentos. Probá en unos minutos.' : (m || 'No se pudo completar.');
    if (btn) btn.disabled = false;
  }
});
async function afterLogin(){
  await checkOwner(); await loadMyList(); await loadProgress(); render();
}
// remember what each user opens, for "Seguir viendo"
document.addEventListener('click', e => {
  const a = e.target.closest('a[data-watch][data-tid]');
  if (!a || !S.session) return;
  recordWatch(a.dataset.tid, a.dataset.s ?? null, a.dataset.e ?? null);
});
/* ---------- built-in player ---------- */
function embedFor(url){
  let u; try { u = new URL(url); } catch { return null; }
  const h = u.hostname.replace(/^(www|m)\./,'');
  let id = null;
  if (h === 'youtu.be') id = u.pathname.slice(1).split('/')[0];
  else if (h.endsWith('youtube.com') || h === 'youtube-nocookie.com'){
    id = u.searchParams.get('v');
    if (!id){ const m = u.pathname.match(/\/(embed|shorts|live|v)\/([\w-]{6,})/); if (m) id = m[2]; }
    if (!id && u.searchParams.get('list')) return { type:'iframe', provider:'YouTube', src:`https://www.youtube-nocookie.com/embed/videoseries?list=${encodeURIComponent(u.searchParams.get('list'))}&autoplay=1&rel=0` };
  }
  if (id){
    const t = parseInt(u.searchParams.get('t') || u.searchParams.get('start') || '0', 10);
    return { type:'iframe', provider:'YouTube', src:`https://www.youtube-nocookie.com/embed/${encodeURIComponent(id)}?autoplay=1&rel=0&modestbranding=1&playsinline=1${t ? '&start=' + t : ''}` };
  }
  if (h === 'vimeo.com' || h === 'player.vimeo.com'){
    const m = u.pathname.match(/(\d{5,})(?:\/([0-9a-f]{6,}))?/);
    if (m) return { type:'iframe', provider:'Vimeo', src:`https://player.vimeo.com/video/${m[1]}?autoplay=1${m[2] ? '&h=' + m[2] : (u.searchParams.get('h') ? '&h=' + u.searchParams.get('h') : '')}` };
  }
  if (h === 'drive.google.com'){
    const m = u.pathname.match(/\/file\/d\/([^/]+)/); const fid = m ? m[1] : u.searchParams.get('id');
    if (fid) return { type:'iframe', provider:'Google Drive', src:`https://drive.google.com/file/d/${encodeURIComponent(fid)}/preview` };
  }
  if (h === 'dailymotion.com' || h === 'dai.ly'){
    const m = h === 'dai.ly' ? [null, u.pathname.slice(1)] : u.pathname.match(/video\/([a-z0-9]+)/i);
    if (m && m[1]) return { type:'iframe', provider:'Dailymotion', src:`https://www.dailymotion.com/embed/video/${m[1]}?autoplay=1` };
  }
  if (h === 'ok.ru'){ const m = u.pathname.match(/video(?:embed)?\/(\d+)/); if (m) return { type:'iframe', provider:'OK', src:`https://ok.ru/videoembed/${m[1]}` }; }
  if (h === 'archive.org'){ const m = u.pathname.match(/\/(?:details|embed)\/(.+)/); if (m) return { type:'iframe', provider:'Internet Archive', src:`https://archive.org/embed/${m[1]}` }; }
  if (/\.(mp4|webm|ogv|ogg|mov|m4v)$/i.test(u.pathname)) return { type:'video', src:url };
  if (/\.m3u8$/i.test(u.pathname)) return { type:'hls', src:url };
  return { type:'iframe', src:url, generic:true };
}
let hlsLoading = null;
function loadHls(){
  if (window.Hls) return Promise.resolve(window.Hls);
  return hlsLoading ||= new Promise((res, rej) => { const sc = document.createElement('script'); sc.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.15/dist/hls.min.js'; sc.onload = () => res(window.Hls); sc.onerror = rej; document.head.appendChild(sc); });
}
function nextAfter(t, season, episode){
  if (!t || t.type !== 'serie' || season == null || season === '') return null;
  const eps = flatEpisodes(t).filter(x => safeUrl(x.e.url));
  const i = eps.findIndex(x => +x.s.number === +season && +x.e.number === +episode);
  return i >= 0 && i + 1 < eps.length ? eps[i + 1] : null;
}
let playerRoot = null;
function openPlayer({ url, tid, s: season, e: episode }){
  const t = tid ? byId(tid) : null;
  const emb = embedFor(url); if (!emb) return;
  let sub = '';
  if (t && t.type === 'serie' && season !== '' && season != null){
    const ep = flatEpisodes(t).find(x => +x.s.number === +season && +x.e.number === +episode)?.e;
    sub = `T${season}:E${episode}${ep?.title ? ' · ' + ep.title : ''}`;
  }
  const nx = nextAfter(t, season, episode);
  if (!playerRoot){ playerRoot = document.createElement('div'); playerRoot.id = 'playerRoot'; document.body.appendChild(playerRoot); }
  const media = emb.type === 'iframe'
    ? `<iframe src="${esc(emb.src)}" title="${esc(t?.title || 'Video')}" allow="autoplay; fullscreen; picture-in-picture; encrypted-media; clipboard-write" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>`
    : `<video id="plVideo" controls autoplay playsinline ${t?.backdrop ? `poster="${esc(t.backdrop)}"` : ''}></video>`;
  playerRoot.innerHTML = `<div class="player" role="dialog" aria-modal="true" aria-label="Reproductor">
    <div class="pl-top">
      <button class="pl-btn" data-pl-close aria-label="Volver">←</button>
      <div class="pl-title"><strong>${esc(t?.title || '')}</strong>${sub ? `<span>${esc(sub)}</span>` : ''}</div>
      ${nx ? `<button class="pl-next" data-pl-next="${esc(t.id)}" data-s="${esc(nx.s.number)}" data-e="${esc(nx.e.number)}">Siguiente episodio ›</button>` : ''}
      <button class="pl-btn" data-pl-full aria-label="Pantalla completa">⛶</button>
    </div>
    <div class="pl-stage">${media}</div>
    ${emb.generic ? `<p class="pl-note">Si el video no carga, <a href="${esc(url)}" target="_blank" rel="noopener">abrilo en otra pestaña</a>.</p>` : ''}
  </div>`;
  document.body.style.overflow = 'hidden';
  if (!S.playerOpen){ history.pushState({ player:true }, '', location.pathname); }
  S.playerOpen = true;
  const v = playerRoot.querySelector('#plVideo');
  if (v){
    if (emb.type === 'video') v.src = emb.src;
    else if (v.canPlayType('application/vnd.apple.mpegurl')) v.src = emb.src;
    else loadHls().then(Hls => { const hls = new Hls(); hls.loadSource(emb.src); hls.attachMedia(v); v._hls = hls; }).catch(() => toast('No se pudo cargar el video', true));
  }
}
function closePlayer(fromPop){
  if (!S.playerOpen) return;
  S.playerOpen = false;
  const v = playerRoot?.querySelector('#plVideo'); if (v?._hls) v._hls.destroy();
  if (playerRoot) playerRoot.innerHTML = '';
  document.body.style.overflow = '';
  if (!fromPop && history.state?.player){ S.ignorePop = true; history.back(); }
}
document.addEventListener('click', e => {
  if (e.target.closest('[data-pl-close]')){ closePlayer(); return; }
  if (e.target.closest('[data-pl-full]')){ const st = document.querySelector('.player'); (st.requestFullscreen ? st.requestFullscreen() : Promise.reject()).catch(() => {}); return; }
  const n = e.target.closest('[data-pl-next]');
  if (n){
    const t = byId(n.dataset.plNext); const ep = flatEpisodes(t).find(x => +x.s.number === +n.dataset.s && +x.e.number === +n.dataset.e);
    if (!ep) return;
    recordWatch(t.id, n.dataset.s, n.dataset.e);
    S.playerOpen = true; openPlayer({ url: ep.e.url, tid: t.id, s: n.dataset.s, e: n.dataset.e });
  }
});
document.addEventListener('keydown', e => { if (e.key === 'Escape' && S.playerOpen && !document.fullscreenElement) closePlayer(); });
/* --- trailer pop-up (no account needed) --- */
function openTrailer(id){
  const t = byId(id); if (!t || !safeUrl(t.trailer)) return;
  const emb = embedFor(t.trailer); if (!emb) return;
  const box = document.createElement('div'); box.className = 'trailer-pop'; box.setAttribute('role','dialog'); box.setAttribute('aria-modal','true'); box.setAttribute('aria-label','Tráiler de ' + (t.title||''));
  const media = emb.type === 'iframe'
    ? `<iframe src="${esc(emb.src)}" title="Tráiler" allow="autoplay; fullscreen; picture-in-picture; encrypted-media" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>`
    : `<video controls autoplay playsinline src="${esc(emb.src)}"></video>`;
  box.innerHTML = `<div class="tp-card"><div class="tp-head"><strong>Tráiler · ${esc(t.title||'')}</strong><button class="pl-btn" data-trailer-close aria-label="Cerrar">✕</button></div><div class="tp-frame">${media}</div></div>`;
  document.body.appendChild(box); document.body.style.overflow = 'hidden';
  const v = box.querySelector('video');
  if (v && emb.type === 'hls'){ v.removeAttribute('src'); if (v.canPlayType('application/vnd.apple.mpegurl')) v.src = emb.src; else loadHls().then(Hls => { const h = new Hls(); h.loadSource(emb.src); h.attachMedia(v); v._hls = h; }); }
  box.querySelector('[data-trailer-close]').focus();
}
function closeTrailer(){
  const b = document.querySelector('.trailer-pop'); if (!b) return;
  const v = b.querySelector('video'); if (v?._hls) v._hls.destroy();
  b.remove(); if (!S.playerOpen && !overlayRoot.firstChild) document.body.style.overflow = '';
}
document.addEventListener('click', e => {
  const tr = e.target.closest('[data-trailer]'); if (tr){ e.preventDefault(); openTrailer(tr.dataset.trailer); return; }
  if (e.target.closest('[data-trailer-close]') || e.target.classList?.contains('trailer-pop')) closeTrailer();
});
document.addEventListener('keydown', e => { if (e.key === 'Escape' && document.querySelector('.trailer-pop')) closeTrailer(); });

// signed-in viewers: play inside the site instead of leaving it
document.addEventListener('click', e => {
  const a = e.target.closest('a[data-watch]');
  if (!a || !S.session) return;
  e.preventDefault();
  if (a.closest('.auth-card')) closeDetail();
  openPlayer({ url: a.getAttribute('href'), tid: a.dataset.tid || '', s: a.dataset.s ?? '', e: a.dataset.e ?? '' });
}, true);

// gate every "watch" link behind an account (capture phase, before navigation)
document.addEventListener('click', e => {
  const a = e.target.closest('a[data-watch]');
  if (!a || S.session || !S.sb) return;
  if (a.closest('.auth-card')) return;
  e.preventDefault(); e.stopPropagation();
  openAuth({ reason:'watch', href: a.getAttribute('href'), tid: a.dataset.tid || '', s: a.dataset.s ?? '', e: a.dataset.e ?? '' });
}, true);
document.addEventListener('click', e => {
  const cw = e.target.closest('[data-cw-remove]'); if (cw){ e.preventDefault(); e.stopPropagation(); removeProgress(cw.dataset.cwRemove); return; }
  const b = e.target.closest('[data-mylist]'); if (b){ e.preventDefault(); e.stopPropagation(); toggleList(b.dataset.mylist); return; }
  const au = e.target.closest('[data-auth]'); if (au){ e.preventDefault(); openAuth({ mode: au.dataset.auth }); }
}, true);
document.addEventListener('click', async e => {
  if (!e.target.closest('[data-logout]') || e.target.closest('#app .side, #app .lock')) return;
  await doLogout();
});
async function doLogout(){
  try { await Promise.race([ S.sb.auth.signOut({ scope:'local' }), new Promise(r => setTimeout(r, 3000)) ]); } catch {}
  try { Object.keys(localStorage).filter(k => /^sb-.*-auth-token/.test(k)).forEach(k => localStorage.removeItem(k)); } catch {}
  S.session = null; S.owner = false; S.myList = new Set(); S.progress = new Map(); S.editing = null; S.dirty = false;
  toast('Sesión cerrada');
  if (S.route === 'mi-lista' || S.route === 'admin') navigate('/'); else render();
}

/* ---------- activar la tele con el celular (/activar) ---------- */
const ACT_CHARS = 'ABCDEFGHJKLMNPRSTUVWXYZ23456789';
function actCode(){
  if (S.act == null){
    let c = '';
    try { c = (new URLSearchParams(location.search).get('c') || '').toUpperCase().replace(/[^A-Z0-9]/g,'').slice(0,6); } catch {}
    S.act = { code: c, state: 'idle', msg: '' };
  }
  return S.act;
}
const ICON_TV = '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" aria-hidden="true"><path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M8.5 2.5 12 6l3.5-3.5"/></svg>';
function renderActivate(){
  const a = actCode();
  if (a.state === 'done') return `<main class="act"><div class="act-card">
    <div class="act-ok"><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#000" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg></div>
    <h1>¡Tu tele está activada!</h1><p>Ya podés mirar Reality Vault en tu televisor. Mi lista y Seguir viendo aparecen allá también.</p>
    <a class="btn btn-play act-btn" href="/">Volver al inicio</a></div></main>`;
  const boxes = Array.from({length:6}, (_,i) => `${i===3?'<i class="act-sep"></i>':''}<input class="act-in" data-act="${i}" inputmode="text" autocomplete="off" autocapitalize="characters" spellcheck="false" aria-label="Carácter ${i+1} del código" data-v="${esc(a.code[i]||'')}" value="${esc(a.code[i]||'')}">`).join('');
  const email = S.session?.user?.email || '';
  return `<main class="act"><div class="act-card">
    <div class="act-ic">${ICON_TV}</div>
    <h1>Activar tu tele</h1>
    ${!S.session ? `<p>Primero entrá con tu cuenta (o creala gratis). Después escribís el código que aparece en tu televisor.</p>
      <div class="act-actions"><button class="btn btn-play act-btn" data-auth="login">Entrar</button><button class="btn act-ghost" data-auth="signup">Crear cuenta</button></div>`
    : `<p>Escribí el código que aparece en la pantalla de tu televisor.</p>
      <div class="act-code" role="group" aria-label="Código de la tele">${boxes}</div>
      <div class="act-who">Vas a activarla con la cuenta ${esc(email)}</div>
      ${a.msg ? `<div class="act-err" role="alert">${esc(a.msg)}</div>` : ''}
      <button class="btn btn-play act-btn" data-act-go ${a.state==='busy'?'disabled':''}>${a.state==='busy'?'Activando…':'Activar'}</button>`}
  </div></main>`;
}
function actRead(){ return [...document.querySelectorAll('.act-in')].map(i => i.value).join(''); }
function actClean(v){ return String(v||'').toUpperCase().replace(/[^A-Z0-9]/g,''); }
app.addEventListener('input', e => {
  const el = e.target; if (!el.classList?.contains('act-in')) return;
  const ins = [...document.querySelectorAll('.act-in')], i = +el.dataset.act;
  let v = actClean(el.value);
  if (v.length === 2 && e.inputType !== 'insertFromPaste'){ const prev = el.dataset.v || ''; v = v[0] === prev ? v[1] : v[1] === prev ? v[0] : v[1]; }
  if (v.length > 1){ v.split('').slice(0, 6 - i).forEach((ch, k) => { ins[i+k].value = ch; }); ins[Math.min(5, i + v.length)].focus(); }
  else { el.value = v; if (v && i < 5) ins[i+1].focus(); }
  ins.forEach(x => { x.dataset.v = x.value; });
  actCode().code = actRead(); actCode().msg = '';
  document.querySelector('.act-err')?.remove();
});
app.addEventListener('keydown', e => {
  const el = e.target; if (!el.classList?.contains('act-in')) return;
  const ins = [...document.querySelectorAll('.act-in')], i = +el.dataset.act;
  if (e.key === 'Backspace' && !el.value && i > 0){ ins[i-1].focus(); ins[i-1].value = ''; e.preventDefault(); }
  else if (e.key === 'ArrowLeft' && i > 0) ins[i-1].focus();
  else if (e.key === 'ArrowRight' && i < 5) ins[i+1].focus();
  else if (e.key === 'Enter') document.querySelector('[data-act-go]')?.click();
});
app.addEventListener('focusin', e => { const el = e.target; if (el.classList?.contains('act-in')) setTimeout(() => el.select(), 0); });
app.addEventListener('click', async e => {
  if (!e.target.closest('[data-act-go]')) return;
  const a = actCode(); a.code = actRead();
  if (a.code.length !== 6){ a.msg = 'Completá los 6 caracteres del código.'; render(); document.querySelector('.act-in')?.focus(); return; }
  a.state = 'busy'; a.msg = ''; render();
  try {
    const { data, error } = await S.sb.rpc('tv_code_claim', { p_code: a.code });
    if (error) throw error;
    if (data === 'ok'){ a.state = 'done'; }
    else if (data === 'sin-sesion'){ a.state = 'idle'; a.msg = 'Entrá con tu cuenta para activar la tele.'; }
    else { a.state = 'idle'; a.msg = 'Ese código no es válido o ya venció. Fijate el que aparece ahora en la tele.'; }
  } catch(err){ a.state = 'idle'; a.msg = 'No se pudo activar. Probá de nuevo en un momento.'; }
  render();
  if (a.state === 'idle') document.querySelector('.act-in')?.focus();
});

/* ---------- clean URLs: /serie/nombre, /pelicula/nombre, /series, /peliculas, /admin ---------- */
function slugify(str){
  return String(str||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase()
    .replace(/&/g,' y ').replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'') || 'titulo';
}
function slugOf(t){
  const base = slugify(t.title);
  const same = S.titles.filter(x => x.type === t.type && slugify(x.title) === base);
  if (same.length <= 1) return base;
  const withYear = base + (t.year ? '-' + t.year : '');
  const sameYear = same.filter(x => (x.year||'') === (t.year||''));
  return sameYear.length <= 1 ? withYear : withYear + '-' + String(t.id).slice(-4);
}
function titlePath(t){ return (t.type === 'serie' ? '/serie/' : '/pelicula/') + slugOf(t); }
function findBySlug(kind, slug){
  const type = kind === 'serie' ? 'serie' : 'pelicula';
  return S.titles.find(t => (t.type === 'serie' ? 'serie' : 'pelicula') === type && slugOf(t) === slug)
      || S.titles.find(t => slugOf(t) === slug) || byId(slug);
}
function routeFromPath(){
  // old links with # (…/#t-id, …/#admin) still work
  const h = location.hash.replace('#','');
  if (h){
    if (h.startsWith('t-')){ const t = byId(h.slice(2)); if (t){ history.replaceState(null,'',titlePath(t)); } else { S.pendingId = h.slice(2); } }
    else if (['series','peliculas','admin','mi-lista','activar'].includes(h)) history.replaceState(null,'','/'+h);
  }
  const parts = decodeURIComponent(location.pathname).replace(/\/+$/,'').split('/').filter(Boolean);
  if (S.pendingId){ S.titleSlug = null; S.titleKind = null; return 'title'; }
  if (parts.length === 2 && parts[0] === 'persona'){ S.personSlug = parts[1]; return 'persona'; }
  if (parts.length === 2 && (parts[0] === 'serie' || parts[0] === 'pelicula')){ S.titleKind = parts[0]; S.titleSlug = parts[1]; return 'title'; }
  if (parts.length === 1 && ['series','peliculas','admin','mi-lista','activar'].includes(parts[0])) return parts[0];
  return 'home';
}
function currentTitleId(){
  if (S.pendingId){ const t = byId(S.pendingId); if (t){ S.pendingId = null; S.titleKind = t.type === 'serie' ? 'serie' : 'pelicula'; S.titleSlug = slugOf(t); history.replaceState(null,'',titlePath(t)); return t.id; } return S.pendingId; }
  const t = findBySlug(S.titleKind, S.titleSlug); return t ? t.id : null;
}
function onRoute(){
  const r = routeFromPath();
  S.route = r; S.genre=''; S.tpTab=''; S.tpSeason=0;
  if (r==='admin'){ S.q=''; document.getElementById('q').value=''; }
  render(); window.scrollTo(0,0);
}
function navigate(path){
  if (path === location.pathname && !location.hash) { window.scrollTo(0,0); return; }
  history.pushState(null, '', path); onRoute();
}
document.addEventListener('click', e => {
  const a = e.target.closest('a[href]');
  if (!a || a.target === '_blank' || e.metaKey || e.ctrlKey || e.shiftKey || e.button) return;
  const href = a.getAttribute('href');
  if (!href.startsWith('/') || href.startsWith('//')) return;
  e.preventDefault(); navigate(href);
});
window.addEventListener('popstate', () => {
  if (S.ignorePop){ S.ignorePop = false; return; }
  if (S.playerOpen){ closePlayer(true); return; }
  onRoute();
});
let brandRef = null;
function renderBrand(){
  const ref = S.site.logo || '';
  const key = ref + '|' + (S.siteLoaded ? 1 : 0);
  if (key === brandRef) return; brandRef = key;
  const img = document.getElementById('siteLogo'), txt = document.getElementById('brandText');
  if (!ref){ img.hidden = true; txt.style.display = (S.siteLoaded || S.noDb) ? 'contents' : 'none'; return; }
  mediaUrl(ref).then(u => { if (u && brandRef === key){ img.src = u; img.hidden = false; txt.style.display = 'none'; } });
}
function render(){
  clearInterval(S.heroTimer); renderBrand();
  document.body.classList.toggle('home', S.route==='home' && !S.q.trim() && !S.noDb);
  document.body.classList.toggle('in-admin', S.route==='admin');
  document.body.classList.toggle('in-landing', isLanding());
  renderAccount();
  if (S.route==='admin' && S.loaded.t && !S._descRun){ S._descRun = true; setTimeout(()=>fillMissingDescriptions(false), 800); }
  document.querySelectorAll('.nav a').forEach(a => { if (a.dataset.r===S.route && !S.q) a.setAttribute('aria-current','page'); else a.removeAttribute('aria-current'); });
  let html;
  if (S.noDb) html = `<div class="lock"><div class="dial" style="width:64px;height:64px" aria-hidden="true"></div><h1>Falta conectar la base de datos</h1><p>Completá <code>config.js</code> con los datos de tu proyecto de Supabase. Los pasos están en el archivo README.</p></div>`;
  else if ((!S.loaded.t || (S.sb && !S.authReady && S.route==='home')) && S.route!=='admin') html = `<div class="skel" aria-hidden="true"><div class="skel-hero"></div><div class="skel-row"><i></i><i></i><i></i><i></i><i></i><i></i></div></div>`;
  else if (S.route==='admin') html = renderAdmin();
  else if (isLanding()) html = renderLanding();
  else if (S.q.trim()) html = renderSearch();
  else if (S.route==='title'){ const tid = currentTitleId(); html = renderTitlePage(tid); trackView(tid); }
  else if (S.route==='mi-lista') html = renderMyList();
  else if (S.route==='activar') html = renderActivate();
  else if (S.route==='persona') html = renderPerson(S.personSlug);
  else if (S.route==='series' || S.route==='peliculas') html = renderBrowse(S.route);
  else html = renderHome();
  app.innerHTML = html + (S.route==='admin' || isLanding() ? '' : renderFooter());
  hydrate(app); logoFallbacks(app); applyTints(app); startHero(); startLanding();
}

document.getElementById('q').addEventListener('input', e => { S.q = e.target.value; if (S.route==='admin'){ history.pushState(null,'','/'); S.route='home'; } render(); });

/* ---------- header over hero ---------- */
const topEl = document.querySelector('.top');
function syncTop(){
  document.documentElement.style.setProperty('--top-h', topEl.offsetHeight + 'px');
  topEl.classList.toggle('clear', window.scrollY < 24);
}
new ResizeObserver(syncTop).observe(topEl);
window.addEventListener('scroll', syncTop, { passive:true });
syncTop();


/* ---------- boot ---------- */
document.getElementById('searchToggle')?.addEventListener('click', () => {
  const top = document.querySelector('.top'); const on = !top.classList.contains('searching');
  top.classList.toggle('searching', on); if (on) document.getElementById('q').focus(); else { const q = document.getElementById('q'); if (q.value){ q.value=''; S.q=''; render(); } }
});

try { localStorage.removeItem('rv-theme2'); } catch {}
document.documentElement.removeAttribute('data-theme');

/* ---------- instant start: last catalog saved in this browser ---------- */
const CACHE_KEY = 'rv-cache-v1';
function loadCache(){
  try {
    const c = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
    if (!c) return;
    S.titles = c.titles || []; S.banners = c.banners || []; S.site = c.site || {}; S.frows = c.frows || []; S.siteLoaded = true;
    S.loaded.t = true; S.loaded.b = true;
  } catch {}
}
let cacheT;
function saveCache(){
  clearTimeout(cacheT);
  cacheT = setTimeout(() => { try { localStorage.setItem(CACHE_KEY, JSON.stringify({ titles:S.titles, banners:S.banners, site:S.site, frows:S.frows, at:Date.now() })); } catch {} }, 300);
}
loadCache();
S.route = routeFromPath();
render();
mqMobile.addEventListener('change', () => { if (S.route !== 'admin') render(); });

/* ---------- data layer (Supabase) ---------- */
function makeDb(sb){
  const listeners = { titles:new Set(), banners:new Set(), site:new Set() };
  const snapDoc = (id, data) => ({ id, exists: data != null, data: () => data == null ? undefined : data, metadata:{} });
  async function rows(table){ const { data, error } = await sb.from(table).select('id,data'); if (error) throw error; return data || []; }
  const timers = {};
  function refresh(table){
    clearTimeout(timers[table]);
    timers[table] = setTimeout(async () => { try { const r = await rows(table); listeners[table].forEach(fn => fn(r)); } catch(err){ console.error(err); } }, 60);
  }
  sb.channel('rv-changes')
    .on('postgres_changes', { event:'*', schema:'public', table:'titles' }, () => refresh('titles'))
    .on('postgres_changes', { event:'*', schema:'public', table:'banners' }, () => refresh('banners'))
    .on('postgres_changes', { event:'*', schema:'public', table:'site' }, () => refresh('site'))
    .subscribe();
  const fail = error => { const e = new Error(error.message || 'Error de base de datos'); e.code = error.code; throw e; };
  function doc(path){
    const [table, id] = path.split('/');
    return {
      id,
      async get(){ const { data, error } = await sb.from(table).select('data').eq('id', id).maybeSingle(); if (error) fail(error); return snapDoc(id, data ? data.data : null); },
      async set(body){ const { error } = await sb.from(table).upsert({ id, data: body, updated_at: new Date().toISOString() }); if (error) fail(error); refresh(table); },
      async update(patch){
        const cur = await this.get(); if (!cur.exists) throw new Error('No existe');
        const { error } = await sb.from(table).upsert({ id, data: { ...cur.data(), ...patch }, updated_at: new Date().toISOString() }); if (error) fail(error); refresh(table);
      },
      async delete(){ const { error } = await sb.from(table).delete().eq('id', id); if (error) fail(error); refresh(table); },
      onSnapshot(next, onErr){
        const fn = r => { const row = r.find(x => x.id === id); next(snapDoc(id, row ? row.data : null)); };
        listeners[table].add(fn); rows(table).then(fn).catch(err => onErr && onErr(err));
        return () => listeners[table].delete(fn);
      }
    };
  }
  function collection(table){
    return {
      doc: id => doc(table + '/' + id),
      onSnapshot(next, onErr){
        const fn = r => next({ docs: r.map(x => snapDoc(x.id, x.data)), size: r.length, empty: !r.length });
        listeners[table].add(fn); rows(table).then(fn).catch(err => onErr && onErr(err));
        return () => listeners[table].delete(fn);
      }
    };
  }
  return { doc, collection };
}
async function checkOwner(){
  const { data:{ session } } = await S.sb.auth.getSession();
  S.session = session;
  if (!session){ S.owner = false; return; }
  const { data, error } = await S.sb.rpc('is_admin');
  S.owner = !error && data === true;
}
(async () => {
  const cfg = window.RV_CONFIG || {};
  if (!window.supabase || !cfg.supabaseUrl || !cfg.supabaseAnonKey || /TU_|xxxx/i.test(cfg.supabaseUrl + cfg.supabaseAnonKey)){ S.noDb = true; render(); return; }
  S.sb = window.supabase.createClient(cfg.supabaseUrl, cfg.supabaseAnonKey);
  const db = makeDb(S.sb);
  loadTop10(); setInterval(loadTop10, 60000);
  const authReady = (async () => { await checkOwner(); await loadMyList(); await loadProgress(); S.authReady = true; render(); })();
  // Supabase pide no llamar a otras funciones suyas dentro de este aviso: se difiere con setTimeout.
  S.sb.auth.onAuthStateChange((evt) => {
    if (evt === 'TOKEN_REFRESHED' || evt === 'INITIAL_SESSION') return;
    setTimeout(async () => {
      const had = !!S.session, was = S.owner; await checkOwner();
      if (had !== !!S.session){ await loadMyList(); await loadProgress(); render(); }
      else if (was !== S.owner || S.route === 'admin') render();
    }, 0);
  });
  S.db = db;
  db.collection('titles').onSnapshot(snap => {
    S.titles = snap.docs.map(d => ({ ...d.data(), id:d.id }));
    S.loaded.t = true; saveCache();
    if (S.route==='admin' && S.owner){ if (!S.editing) rerenderPanel(); else { const side = document.querySelector('.side'); if (side) side.querySelector('[data-tab="catalogo"] span').textContent = S.titles.length; } }
    else { const y = window.scrollY; render(); if (S.route==='title') window.scrollTo(0,y); }
  }, err => { console.error(err); S.loaded.t = true; render(); });
  db.doc('site/rows').onSnapshot(snap => {
    const rows = snap.exists ? (snap.data().rows || []) : [];
    if (S.route === 'admin' && S.adminTab === 'filas' && document.activeElement?.closest?.('.frow-ed')) return; // don't clobber typing
    S.frows = rows; saveCache();
    if (S.route === 'admin'){ if (S.owner && S.adminTab === 'filas') rerenderPanel(); }
    else if (S.route === 'home' && S.loaded.t){ const y = window.scrollY; render(); window.scrollTo(0, y); }
  }, () => {});
  db.doc('site/config').onSnapshot(snap => {
    S.site = snap.exists ? { ...snap.data() } : {}; S.siteLoaded = true;
    saveCache(); renderBrand();
    const f = app.querySelector('footer'); if (f){ f.outerHTML = renderFooter(); const nf = app.querySelector('footer'); if (nf) hydrate(nf); }
    if (S.route==='admin' && S.owner && S.adminTab==='sitio') rerenderPanel();
  }, () => {});
  db.collection('banners').onSnapshot(snap => {
    S.banners = snap.docs.map(d => ({ ...d.data(), id:d.id }));
    S.loaded.b = true; saveCache();
    if (S.route==='admin' && S.owner){ if (S.adminTab==='banners') rerenderPanel(); }
    else if (S.loaded.t) render();
  }, () => {});
  render();
})();
})();
