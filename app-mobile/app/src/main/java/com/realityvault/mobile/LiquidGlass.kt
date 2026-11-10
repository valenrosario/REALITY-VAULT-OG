package com.realityvault.mobile

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Vidrio líquido de verdad (como el de iOS): no es un fondo borroso con borde, es una lente.
 *  - Lo que pasa por detrás se ve a través del vidrio, apenas desenfocado, y en el borde se CURVA
 *    (refracción), como en el canto redondeado de un vidrio grueso, con una pizca de separación de
 *    colores (rojo/azul) justo en el filo.
 *  - El filo tiene un brillo que depende de hacia dónde mira (más fuerte arriba a la izquierda, un
 *    reflejo más suave en el lado opuesto).
 *  - La pestaña elegida es una segunda lente, más clara, que se desliza por encima.
 * Todo eso lo dibuja la placa de video con un shader (AGSL), así que necesita Android 13 o más nuevo;
 * en equipos más viejos la barra usa el vidrio esmerilado de antes.
 */
val GLASS_OK = Build.VERSION.SDK_INT >= 33

/** La "foto en vivo" de lo que hay en pantalla, que el vidrio usa como fondo. */
class GlassBackdrop(val layer: GraphicsLayer) { var origin by mutableStateOf(Offset.Zero) }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop { val l = rememberGraphicsLayer(); return remember(l) { GlassBackdrop(l) } }

/** Va en el contenido que tiene que verse a través del vidrio: lo graba (y lo dibuja normal). */
fun Modifier.glassSource(b: GlassBackdrop): Modifier = this
    .onGloballyPositioned { b.origin = it.positionInRoot() }
    .drawWithContent {
        b.layer.record { this@drawWithContent.drawContent() }
        drawLayer(b.layer)
    }

private const val GLASS_SRC = """
uniform shader content;
uniform float4 bar;       // izquierda, arriba, derecha, abajo del vidrio (px)
uniform float radius;
uniform float4 thumb;     // la lente de la pestaña elegida
uniform float thumbRad;
uniform float thumbA;
uniform float4 thumbTint; // color de la lente elegida (rgb) y cuánto tiñe (a): 0 = vidrio claro
uniform float sc;         // escala del efecto: 1 para una barra alta, menos para una fina
uniform float dp;

float sdRR(float2 p, float4 r, float rad) {
    float2 c = (r.xy + r.zw) * 0.5; float2 h = (r.zw - r.xy) * 0.5;
    float2 q = abs(p - c) - h + rad;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
}
float2 nrm(float2 p, float4 r, float rad) {
    float2 g = float2(sdRR(p + float2(1.0, 0.0), r, rad) - sdRR(p - float2(1.0, 0.0), r, rad),
                      sdRR(p + float2(0.0, 1.0), r, rad) - sdRR(p - float2(0.0, 1.0), r, rad));
    return g / max(length(g), 0.0001);
}
// perfil del canto redondeado: casi nada adentro, se dispara justo en el filo
float lens(float depth, float band) { float t = clamp(1.0 - depth / band, 0.0, 1.0); return 1.0 - sqrt(max(1.0 - t * t, 0.0)); }

half4 main(float2 xy) {
    float d = sdRR(xy, bar, radius);
    float aa = clamp(0.5 - d, 0.0, 1.0);
    // sombra suave que el vidrio proyecta hacia abajo (afuera del vidrio)
    float ds = sdRR(xy - float2(0.0, 7.0 * dp * sc), bar, radius);
    float sh = 0.34 * sc * exp(-max(ds, 0.0) / (9.0 * dp * sc)) * (1.0 - aa);
    if (aa <= 0.0) { return half4(0.0, 0.0, 0.0, half(sh)); }

    float2 n = nrm(xy, bar, radius);
    float depth = max(-d, 0.0);
    float k = lens(depth, 22.0 * dp * sc);
    float2 bc = (bar.xy + bar.zw) * 0.5;
    // el canto curva lo de atrás hacia adentro; el centro agranda apenas, como una lente gruesa
    float2 off = -n * k * 34.0 * dp * sc + (bc - xy) * 0.035 * sc;

    float dT = sdRR(xy, thumb, thumbRad);
    float inT = clamp(0.5 - dT, 0.0, 1.0) * thumbA;
    float2 nT = nrm(xy, thumb, thumbRad);
    float depthT = max(-dT, 0.0);
    float kT = lens(depthT, 13.0 * dp * sc);
    float2 tc = (thumb.xy + thumb.zw) * 0.5;
    off += inT * (-nT * kT * 16.0 * dp * sc + (tc - xy) * 0.09);

    float3 col = float3(content.eval(xy + off * 1.14).r, content.eval(xy + off).g, content.eval(xy + off * 0.86).b);
    float l = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(l), col, 1.22);                         // un poco más de color, como el vidrio de Apple
    // tinte oscuro para que los íconos blancos se lean: leve sobre fondos oscuros, más fuerte sobre fondos claros
    col = mix(col, float3(0.06, 0.06, 0.075), 0.24 + 0.34 * smoothstep(0.25, 0.85, l));
    col += 0.035 * (1.0 - l);                                // sobre fondos muy oscuros el vidrio igual se distingue
    // la lente elegida: más clara, con una sombrita alrededor que la despega del vidrio
    col += inT * (0.10 + 0.05 * (1.0 - l)) * (1.0 - thumbTint.a);
    // ...o de color (vidrio teñido): deja pasar un poco de lo de atrás, así se nota que es vidrio y no una mancha plana
    col = mix(col, thumbTint.rgb * (0.97 + 0.10 * l), thumbTint.a * inT);
    float ring = (1.0 - clamp(0.5 - dT, 0.0, 1.0)) * exp(-max(dT, 0.0) / (5.0 * dp)) * thumbA;
    col *= 1.0 - 0.22 * ring;

    float2 L = normalize(float2(-0.5, -0.86));
    // brillo del filo: una línea fina, fuerte donde mira a la luz y con un reflejo más suave del lado opuesto
    float rim = smoothstep(1.5 * dp, 0.0, depth);
    float sp = pow(max(dot(n, L), 0.0), 1.3) * 1.0 + pow(max(dot(n, -L), 0.0), 2.2) * 0.5 + 0.12;
    col += rim * sp * 0.8;
    // grosor: el canto se aclara apenas hacia la luz y se oscurece apenas del otro lado
    col += k * (0.10 + 0.10 * max(dot(n, L), 0.0));
    col *= 1.0 - 0.16 * smoothstep(7.0 * dp, 1.5 * dp, depth) * max(dot(n, -L), 0.0) * (1.0 - rim);

    float rimT = smoothstep(1.3 * dp, 0.0, depthT) * inT;
    float spT = pow(max(dot(nT, L), 0.0), 1.3) * 0.95 + pow(max(dot(nT, -L), 0.0), 2.2) * 0.45 + 0.10;
    col += rimT * spT * 0.7 + inT * kT * (0.06 + 0.08 * max(dot(nT, L), 0.0));
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * aa), half(aa + sh));
}
"""

/**
 * El vidrio en sí (sin íconos: van encima, en otro Box). Ocupa todo el lugar que le den; el vidrio se
 * dibuja con [margin] de aire alrededor (ese aire es de donde la lente toma lo que curva en el borde).
 * [thumb]: rectángulo de la lente de la pestaña elegida, en dp, relativo al vidrio.
 */
@RequiresApi(33)
@Composable
fun LiquidGlass(
    backdrop: GlassBackdrop, modifier: Modifier, margin: Dp, radius: Dp, thumb: () -> Rect, thumbRadius: Dp, thumbAlpha: () -> Float,
    // rectángulo del vidrio en dp dentro de este Box (si no se pasa: todo el Box menos el margen). En ese caso [thumb] también va en dp del Box.
    bar: (() -> Rect)? = null, scale: Float = 1f, thumbTint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent, blur: Dp = 4.dp
) {
    val shader = remember { RuntimeShader(GLASS_SRC) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { pos = it.positionInRoot() }
            .graphicsLayer {
                val m = margin.toPx(); val t = thumb(); val d = density
                val b = bar?.invoke()
                if (b == null) {
                    shader.setFloatUniform("bar", m, m, size.width - m, size.height - m)
                    shader.setFloatUniform("thumb", m + t.left * d, m + t.top * d, m + t.right * d, m + t.bottom * d)
                } else {
                    shader.setFloatUniform("bar", b.left * d, b.top * d, b.right * d, b.bottom * d)
                    shader.setFloatUniform("thumb", t.left * d, t.top * d, t.right * d, t.bottom * d)
                }
                shader.setFloatUniform("radius", radius.toPx())
                shader.setFloatUniform("thumbTint", thumbTint.red, thumbTint.green, thumbTint.blue, thumbTint.alpha)
                shader.setFloatUniform("sc", scale)
                shader.setFloatUniform("thumbRad", thumbRadius.toPx())
                shader.setFloatUniform("thumbA", thumbAlpha())
                shader.setFloatUniform("dp", d)
                val blurPx = blur.toPx()
                renderEffect = RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content"),
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                ).asComposeRenderEffect()
            }
            .drawBehind { translate(backdrop.origin.x - pos.x, backdrop.origin.y - pos.y) { drawLayer(backdrop.layer) } }
    )
}
