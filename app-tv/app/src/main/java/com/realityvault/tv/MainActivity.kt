package com.realityvault.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // imágenes: caché en memoria y en disco para que todo aparezca al instante
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.3).build() }
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(400L * 1024 * 1024).build() }
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .build()
        )
        // el catálogo se actualiza solo: al abrir, al volver a la app y cada 3 minutos
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    Store.refreshCatalog()
                    Store.refreshUser()
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
}
