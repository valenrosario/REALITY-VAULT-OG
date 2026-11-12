#!/usr/bin/env python3
"""Lee ../config.js (la misma conexión de Supabase del sitio) y escribe source/Config.brs. Uso: python3 tools/gen_config.py"""
import re, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
cfg = (root.parent / 'config.js').read_text()
g = lambda k: re.search(k + r"\s*:\s*'([^']+)'", cfg).group(1)
(root / 'source' / 'Config.brs').write_text(f'''' Generado por tools/gen_config.py desde ../config.js — no editar a mano.
function Cfg() as Object
    return {{
        supa: "{g('supabaseUrl')}",
        key: "{g('supabaseAnonKey')}",
        site: "https://reality-vault.vercel.app",
        updateUrl: "https://github.com/valenrosario/REALITY-VAULT-OG/releases/download/roku/update.json"
    }}
end function
''')
print('Config.brs ok')
