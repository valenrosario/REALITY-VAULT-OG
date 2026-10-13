# REALITY VAULT

Sitio de streaming con catálogo de series y películas, banners, fichas estilo Disney+ y un panel de control privado.

Es un sitio estático (HTML + CSS + JavaScript, sin compilar). Los datos, las imágenes y el inicio de sesión los maneja **Supabase** (gratis). El sitio se publica en **Vercel** (gratis).

```
index.html            la página
app.js                toda la lógica del sitio y del panel
config.js             ← acá van tus datos de Supabase
fonts/                MTV Gravity Grotesk (Bold, Book, Light)
supabase/schema.sql   crea las tablas, permisos y la carpeta de imágenes
respaldo-inicial.json lo que ya tenías cargado (títulos, banners, logo)
```

---

## Paso 1 · Crear la base de datos en Supabase

1. Entrá a [supabase.com](https://supabase.com), creá una cuenta y tocá **New project**. Elegí un nombre (por ejemplo `reality-vault`), una contraseña para la base y la región **South America (São Paulo)**.
2. Esperá a que termine de crearse (1–2 minutos).
3. Abrí `supabase/schema.sql`, **cambiá `TU_EMAIL@gmail.com` por tu email** (está al final del archivo) y copiá todo el contenido.
4. En Supabase andá a **SQL Editor → New query**, pegalo y tocá **Run**. Tiene que decir *Success*.

## Paso 2 · Crear tu usuario de administrador

1. En Supabase andá a **Authentication → Users → Add user → Create new user**.
2. Poné **el mismo email** que escribiste en el paso 1 y una contraseña. Marcá **Auto Confirm User**.
3. Las cuentas de visitantes se activan en el paso 6. Solo tu email puede usar el panel, aunque otras personas se registren.

## Paso 3 · Conectar el sitio con Supabase

1. En Supabase andá a **Project Settings → API** (o **Data API**).
2. Copiá **Project URL** y la clave **anon public**.
3. Abrí `config.js` en GitHub (ícono del lápiz para editar) y reemplazá `https://TU_PROYECTO.supabase.co` y `TU_ANON_PUBLIC_KEY` por esos dos datos. Guardá con **Commit changes**.

> La clave *anon public* puede estar a la vista: los permisos del paso 1 hacen que solo tu email pueda modificar algo. **Nunca** pegues la clave *service_role*.

## Paso 4 · Publicar en Vercel

1. Entrá a [vercel.com](https://vercel.com) con tu cuenta de GitHub.
2. **Add New → Project**, elegí el repositorio `reality-vault` y tocá **Import**.
3. No cambies nada (Framework Preset: *Other*) y tocá **Deploy**.
4. Al terminar te da un enlace del tipo `reality-vault.vercel.app`. Ese es tu sitio. Si querés un dominio propio, lo agregás en **Settings → Domains**.

Cada vez que cambies algo en GitHub, Vercel vuelve a publicar solo.

## Paso 5 · Entrar al panel y recuperar lo que ya tenías

1. Abrí tu sitio y agregá `/admin` al final del enlace (por ejemplo `reality-vault.vercel.app/admin`).
2. Entrá con el email y la contraseña del paso 2. A partir de ahí vas a ver el botón **Panel** arriba a la derecha.
3. Andá a **Panel → Sitio → Importar respaldo** y elegí el archivo `respaldo-inicial.json` (descargalo desde GitHub). Se suben tus títulos, banners, imágenes y el logo del sitio.

## Paso 6 · Cuentas de usuarios y "Mi lista"

Para ver un capítulo o una película, el sitio pide crear una cuenta gratis. Con la cuenta, cada persona guarda títulos en **Mi lista**.

1. En Supabase → **SQL Editor → New query**, pegá el contenido de `supabase/02-cuentas-y-mi-lista.sql` y tocá **Run**.
2. En **Authentication → Sign In / Providers**, activá **Allow new users to sign up**.
3. En la misma página, dentro de **Email**, desactivá **Confirm email** y guardá. Así la cuenta queda lista al instante: el correo gratuito de Supabase solo manda unos pocos emails por hora, y con la confirmación activada muchos usuarios no podrían terminar de registrarse.

---

## Cómo se usa

### Importar series y películas desde TMDB

En el panel, al crear o editar un título, tocá **Importar de TMDB**. Completa título, descripción, años, géneros, clasificación, reparto con fotos, póster, fondo, logo y todas las temporadas con sus capítulos.

La primera vez te pide una clave gratuita:

1. Creá una cuenta en [themoviedb.org](https://www.themoviedb.org/signup).
2. Entrá a **Configuración → API** ([enlace directo](https://www.themoviedb.org/settings/api)) y pedí una clave de tipo **Developer** para uso personal.
3. Copiá la **Clave de la API** (o el **Token de acceso de lectura**) y pegala en el panel. Queda guardada solo en tu navegador.

Después de importar, revisá los datos, cargá los enlaces para ver y tocá **Guardar**.

### Imágenes

- En cada campo de imagen podés tocar para **subir un archivo**, o el botón 🔗 para **pegar un enlace** (por ejemplo una imagen de Blogger).
- **Respaldo:** en **Panel → Sitio → Descargar respaldo** bajás todo el catálogo en un archivo. Conviene hacerlo de vez en cuando.
- **Cerrar sesión:** botón al final del menú del panel.

## Límites del plan gratis de Supabase

- 500 MB de base de datos y 1 GB de imágenes: alcanza para cientos de títulos.
- Si el proyecto pasa **7 días sin visitas**, Supabase lo pausa. Se reactiva desde el panel de Supabase con un clic (**Restore project**), sin perder nada.

## Probarlo en tu computadora (opcional)

Con [Node.js](https://nodejs.org) instalado, dentro de la carpeta del proyecto:

```
npx serve .
```

y abrí el enlace que muestra.
