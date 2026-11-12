# REALITY VAULT · Roku

App para Roku (BrightScript + SceneGraph, 1080p). Usa el mismo catálogo en vivo (Supabase) que la web y la app de la TV.

## Instalar (una sola vez)
1. En el Roku: control remoto → Inicio ×3, Arriba ×2, Derecha, Izquierda, Derecha, Izquierda, Derecha → activar **Modo desarrollador** y poner una contraseña.
2. Bajá `reality-vault-roku.zip` de la sección Releases (tag `roku`).
3. Abrí `http://IP-DEL-ROKU` en el navegador (usuario `rokudev`), **Upload** → **Install**.

## Que se actualice sola
Una app instalada por modo desarrollador NO se actualiza sola (y se borra al reiniciar el Roku en algunos modelos si no se reinstala). Para actualizaciones automáticas:
1. Entrá a https://developer.roku.com → **Manage My Channels** → **Create channel** → tipo *Non-certified channel* (privado).
2. Subí el zip una vez, instalá el canal desde el enlace que te da Roku (se agrega como cualquier canal).
3. Cuando salga una versión nueva, subí el nuevo zip a ese mismo canal (**Update package**): los Roku lo reciben solos.

El catálogo (títulos, banners, filas) es en vivo, no necesita actualización de la app.

## Desarrollo
- `python3 tools/gen_config.py` → genera `source/Config.brs` desde `../config.js`.
- `python3 tools/make_assets.py` → regenera imágenes.
- Validar: `npx brighterscript --project bsconfig.json`.
