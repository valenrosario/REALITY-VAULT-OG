package com.realityvault.mobile

import android.graphics.Color as AColor
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.realityvault.tv.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        Cast.init(this)
        com.realityvault.tv.Updater.init(this)
        // imágenes: caché en memoria y en disco para que todo aparezca al instante
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(300L * 1024 * 1024).build() }
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .build()
        )
        // El catálogo se actualiza solo: al abrir, al volver a la app y cada 3 minutos mientras está visible.
        // Cuando la app va al fondo se frena (no gasta datos ni batería).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    Store.refreshCatalog()
                    Store.refreshUser()
                    autoCheckUpdates()
                    delay(3 * 60_000)
                }
            }
        }
        setContent {
            val nav = remember { Nav() }
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(nav) { finish() }
            }
        }
    }

    // Si estás viendo algo y salís de la app (botón de inicio), el video sigue en una ventanita flotante.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.inPip = isInPictureInPictureMode
    }
}
