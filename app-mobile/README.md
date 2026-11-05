# Reality Vault · app nativa para Android (celular)

App 100 % nativa (Kotlin + Jetpack Compose + Media3/ExoPlayer). **Sin WebView.**
Usa los mismos datos reales que la web y la app de TV: lo que cargues en el panel de administración
(títulos, temporadas, episodios, extras, banners, colecciones, subtítulos) aparece solo en el celular.

## Qué comparte con la app de TV
`Data.kt` y `Embed.kt` se copian desde `../app-tv` al compilar (tarea `copyShared` en `app/build.gradle.kts`):
modelos, conexión con Supabase (`config.js`), sesión, Mi lista, progreso y minuto por el que vas.
La única diferencia es el flag `BuildConfig.TV_LINKS`: en el celular se usa el enlace normal (el mismo que la web;
si es un `.mkv` se usa su copia `.web.mp4` cuando existe) y no el "Enlace para la TV".

## Estructura (`app/src/main/java/com/realityvault/mobile`)
| Archivo | Qué hace |
|---|---|
| `MainActivity.kt` | Actividad, caché de imágenes, refresco del catálogo (al abrir, al volver y cada 3 min), imagen en imagen |
| `Nav.kt` / `App.kt` | Navegación, transiciones, barra de abajo, botón atrás |
| `Splash.kt` | Intro "muro que se apaga" (mismos tiempos y efectos que la TV) |
| `Home.kt` | Portada con carrusel, filas (seguir viendo, top 10, destacados, colecciones…) |
| `Detail.kt` | Ficha: temporadas, episodios, extras, reparto, relacionados |
| `Browse.kt` | Series, Películas, Mi lista, buscador tolerante a errores, colecciones, persona |
| `Account.kt` | Entrar / crear cuenta, mi cuenta |
| `Player.kt` | Reproductor (ver abajo) |

## Reproductor
Play/pausa, barra de progreso con zona cargada, tiempos, ±10 s (botones y doble toque), brillo (arrastrar a la izquierda)
y volumen (a la derecha), bloqueo de controles, ocultar controles solos, girar pantalla (horizontal automático),
subtítulos `.vtt` en español (priorizados sobre los del MKV), selección de audio / subtítulos / calidad (si hay varias) / velocidad,
siguiente episodio automático con cuenta regresiva, reintentos ante cortes de red, imagen en imagen,
guarda el minuto cada 15 s y al salir, se pausa al minimizar.
Los links de YouTube/Vimeo (tráilers) se abren en su app, no dentro de la app.

## Compilar
```bash
cd app-mobile
gradle :app:assembleDebug      # APK de prueba: app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease    # APK firmado (usa el keystore de CI si existe, si no el de debug)
```
Requisitos: JDK 17, Android SDK (compileSdk 34) y Gradle 8.9. Crear `local.properties` con `sdk.dir=/ruta/al/sdk`.

## APK listo para instalar
Cada cambio en `app-mobile/` (o en los archivos compartidos) dispara `.github/workflows/android-mobile.yml`,
que publica el APK en el release **mobile** de GitHub: `reality-vault.apk`.
