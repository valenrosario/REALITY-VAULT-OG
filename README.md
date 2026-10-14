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

## Paso 7 · "Seguir viendo"

En Supabase → **SQL Editor → New query**, pegá el contenido de `supabase/03-seguir-viendo.sql` y tocá **Run**. Desde ahí, cada usuario ve en el inicio la fila **Seguir viendo** con lo último que abrió, y el botón de cada serie pasa a decir **Continuar T1:E3**.

## Paso 8 · "Top 10 hoy"

En Supabase → **SQL Editor → New query**, pegá el contenido de `supabase/04-top-10.sql` y tocá **Run**. Después hacé lo mismo con `supabase/05-top-10-visitas.sql`. En el inicio aparece la fila **Top 10 hoy** con los títulos cuya página abrieron más personas en las últimas 24 horas (con o sin cuenta). Se actualiza sola cada minuto.

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
- Al subir un archivo se abre el **recorte**: arrastrás la imagen y acercás para elegir qué parte se ve. Los logos se suben sin recortar.
- **Respaldo:** en **Panel → Sitio → Descargar respaldo** bajás todo el catálogo en un archivo. Conviene hacerlo de vez en cuando.
- **Cerrar sesión:** botón al final del menú del panel.

## App de Android (APK)

En la carpeta `app-android/` está la app. Trae las pantallas del sitio **adentro del teléfono** (no abre la página web) y se conecta directo a la misma base de datos de Supabase: todo lo que cargues en el panel aparece solo en la app.

Cada vez que hay un cambio en GitHub, se compila sola una APK nueva (pestaña **Actions** del repositorio, tarda unos 5 minutos). Se descarga siempre desde el mismo enlace:

`https://github.com/valenrosario/REALITY-VAULT-OG/releases/download/apk/reality-vault.apk`

Para instalarla en Android: abrí el enlace desde el celular, descargá el archivo y aceptá **Instalar apps de origen desconocido** cuando lo pida.

## Límites del plan gratis de Supabase

- 500 MB de base de datos y 1 GB de imágenes: alcanza para cientos de títulos.
- Si el proyecto pasa **7 días sin visitas**, Supabase lo pausa. Se reactiva desde el panel de Supabase con un clic (**Restore project**), sin perder nada.

## Probarlo en tu computadora (opcional)

Con [Node.js](https://nodejs.org) instalado, dentro de la carpeta del proyecto:

```
npx serve .
```

y abrí el enlace que muestra.
