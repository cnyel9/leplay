package com.leplay.app

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * One place to decide whether the refractive glass shaders can run. Old Android
 * versions and low-RAM TVs keep the same glass surfaces through a cheaper
 * translucent fallback instead of losing the design language or stuttering.
 */
object GlassQuality {
    @Volatile
    private var deviceSupports = isRuntimeShaderSupported()

    /**
     * True only when the device can draw the shaders *and* the viewer has kept
     * Liquid Glass switched on. Compose observes this state, so flipping the
     * setting instantly swaps every surface between glass and solid Material.
     */
    var effectsSupported by mutableStateOf(deviceSupports)
        private set

    fun init(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        deviceSupports = isRuntimeShaderSupported() && am?.isLowRamDevice != true
        recompute()
    }

    fun setEnabled(enabled: Boolean) {
        recompute()
    }

    /** Recompute the gate from all three inputs: device capability, the Liquid
     *  Glass preference and the Lite Mode (Mode Ringan) interlock. */
    internal fun recompute() {
        effectsSupported = deviceSupports && GlassSettings.enabled && !GlassSettings.liteMode
    }
}

/**
 * User-facing Liquid Glass preference. Stored in the same "appearance" file as
 * the theme so it survives restarts, but kept separate from the theme mode.
 */
object GlassSettings {
    private const val PREFS = "appearance"
    private const val KEY = "liquid_glass"
    private const val KEY_LITE_MODE = "lite_mode"
    private const val KEY_GLASS_REPAIRED = "glass_state_repaired_008"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    /**
     * Lite Mode (Mode Ringan): a persisted performance mode. While on, the
     * glass effect gate closes (surfaces go solid), animations collapse to
     * instant, image crossfades stop and Shorts stops preloading ahead.
     */
    var liteMode by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean(KEY, true)
        liteMode = p.getBoolean(KEY_LITE_MODE, false)
        // Single-switch model (owner decision 8 Nov 2026): Liquid Glass ON is
        // the only glass state, Liquid Glass OFF means Lite Mode. Normalize
        // legacy two-switch installs once so enabled == !liteMode from here on.
        val glassOn = enabled && !liteMode
        enabled = glassOn
        liteMode = !glassOn
        // One-time repair (0.0.8-test3 rebuild 123): test builds before the
        // single-switch model wrote lite_mode=true for installs that had
        // only ever toggled unrelated lite paths, and the normalization
        // above then persisted liquid_glass=false on top of it. A user who
        // explicitly switched Liquid Glass ON in Settings (setGlass writes
        // both keys together) is untouched; only the contradictory
        // both-off state, which no current switch can produce on purpose,
        // is restored to the product default: glass ON, lite OFF.
        if (!enabled && liteMode && !p.getBoolean(KEY_GLASS_REPAIRED, false)) {
            enabled = true
            liteMode = false
        }
        p.edit()
            .putBoolean(KEY, enabled)
            .putBoolean(KEY_LITE_MODE, liteMode)
            .putBoolean(KEY_GLASS_REPAIRED, true)
            .apply()
        GlassQuality.recompute()
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY, value)?.apply()
        GlassQuality.setEnabled(value)
    }

    fun updateLiteMode(value: Boolean) {
        liteMode = value
        prefs?.edit()?.putBoolean(KEY_LITE_MODE, value)?.apply()
        GlassQuality.recompute()
    }

    /** The single user-facing switch: glass on, or Lite Mode. No third state. */
    fun setGlass(on: Boolean) {
        updateEnabled(on)
        updateLiteMode(!on)
    }
}









@Composable
fun glassFill(alpha: Float = 1f): Color {
    if (!GlassQuality.effectsSupported) {
        // Solid Material surface: no translucency, no blur, no backdrop.
        return MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dark = isSystemInDarkTheme()
    return if (dark) Color(0xFF121212).copy(alpha = 0.38f * alpha) else
        Color(0xFFFAFAFA).copy(alpha = 0.34f * alpha)
}

/**
 * Opaque fill for controls drawn directly on top of video. Those controls keep
 * white icons in both themes, so with Liquid Glass off they need a dark solid
 * surface rather than the light Material surface used by sheets and dialogs.
 */
@Composable
fun glassOverlayFill(alpha: Float = 1f): Color =
    if (!GlassQuality.effectsSupported) Color(0xFF1C1C1E) else glassFill(alpha)

/** The backdrop captured behind the player, used by in-player liquid glass controls. */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Immutable
data class GlassSpec(
    val shape: Shape,
    val tint: Color,
    val blur: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val shadow: Shadow?,
    val highlight: Highlight?,
    val innerShadow: InnerShadow?,
    val chromaticAberration: Boolean,
)

@Composable
fun glassSpec(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    tint: Color = glassFill(),
    blur: Dp = 18.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 22.dp,
    shadow: Shadow? = Shadow.Default,
    highlight: Highlight? = Highlight.Default,
    innerShadow: InnerShadow? = InnerShadow.Default.copy(radius = 10.dp, alpha = 0.5f),
    chromaticAberration: Boolean = true,
): GlassSpec = GlassSpec(
    shape = shape,
    // With Liquid Glass off every tint becomes a solid Material fill, even when
    // a caller supplied a translucent colour for the shader path.
    tint = if (GlassQuality.effectsSupported) tint else tint.copy(alpha = 1f),
    blur = blur,
    refractionHeight = refractionHeight,
    refractionAmount = refractionAmount,
    shadow = shadow,
    highlight = highlight,
    innerShadow = innerShadow,
    chromaticAberration = chromaticAberration,
)


fun Modifier.liquidGlass(backdrop: Backdrop, spec: GlassSpec): Modifier =
    if (GlassQuality.effectsSupported) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { spec.shape },
            effects = {
                vibrancy()
                blur(spec.blur.toPx())
                lens(
                    spec.refractionHeight.toPx(),
                    spec.refractionAmount.toPx(),
                    chromaticAberration = spec.chromaticAberration,
                )
            },
            highlight = spec.highlight?.let { h -> { h } },
            shadow = spec.shadow?.let { s -> { s } },
            innerShadow = spec.innerShadow?.let { s -> { s } },
            onDrawSurface = {
                if (spec.tint.isSpecified) drawRect(spec.tint)
            },
        )
    } else {
        fallbackGlass(spec)
    }

/**
 * Fallback for every liquidGlass call when the shader path is closed.
 * Two different worlds share this branch and must not be conflated:
 * (0.0.8-test3 audit)
 * - Liquid Glass OFF (or Lite Mode): the owner's rule is zero glass
 *   anywhere, so the surface is a plain fully opaque Material fill.
 * - Liquid Glass ON but the device cannot run runtime shaders (Android
 *   12 and older, low-RAM devices): glass was requested, so the frosted
 *   approximation (translucent fill, top sheen, hairline edge) is kept.
 * The branch is chosen from the user preference, never from
 * effectsSupported alone, so an old device with glass ON keeps its
 * frosted look while a shader-capable device with glass OFF gets none.
 */
private fun Modifier.fallbackGlass(spec: GlassSpec): Modifier =
    if (!GlassSettings.enabled || GlassSettings.liteMode) {
        // Liquid Glass is switched off: a plain, fully opaque Material surface.
        this
            .clip(spec.shape)
            .background(spec.tint.copy(alpha = 1f))
    } else {
        // Android 12 and older (plus low-end TVs without runtime shaders) cannot
        // render the refractive glass shader. Keep the same frosted surface look
        // with a translucent fill, soft top sheen and hairline edge instead of
        // removing the glass entirely.
        this
            .clip(spec.shape)
            .background(spec.tint)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.05f),
                    ),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.16f), spec.shape)
    }

/**
 * Glass surface for content regions (cards, chips, bars) that have no
 * backdrop of their own: refracts the app-wide page backdrop when Liquid
 * Glass is on, and resolves to exactly the given solid colour when it is
 * off, so call sites keep one code path for both worlds (0.0.8-test3).
 * The backdrop is null when no provider is in scope; the surface then
 * degrades to the solid colour instead of crashing.
 */
@Composable
fun Modifier.pageGlass(shape: Shape, solid: Color, tintAlpha: Float = 0.45f): Modifier {
    val backdrop = if (GlassQuality.effectsSupported) LocalGlassBackdrop.current else null
    return if (backdrop != null) {
        this.liquidGlass(
            backdrop,
            glassSpec(
                shape = shape,
                tint = solid.copy(alpha = tintAlpha),
                blur = 18.dp,
                refractionHeight = 14.dp,
                refractionAmount = 22.dp,
            ),
        )
    } else if (GlassQuality.effectsSupported) {
        // Glass is ON but this subtree has no backdrop it may refract.
        // Some screens live INSIDE the layer another backdrop records
        // (Settings sits inside the page backdrop's layer): drawing that
        // backdrop from in there feeds the layer into itself and crashes
        // the app on open (0.0.8-test3 crash, fixed in the 122 rebuild).
        // The frosted approximation gives those screens the glass look
        // with no backdrop at all.
        this
            .clip(shape)
            .background(solid.copy(alpha = 0.55f))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.05f),
                    ),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    } else {
        this.clip(shape).background(solid)
    }
}

/**
 * Refractive glass for a surface sitting on its own screen-local backdrop
 * layer, with exactly the 0.0.1 engine parameters (0.0.8-test3, owner
 * verdict: the expanded glass must look like 0.0.1, not like the frosted
 * approximation). The caller owns a [rememberLayerBackdrop] layer that
 * wraps that screen's background and content and consumes it OUTSIDE the
 * recorded layer, the CollapsingHeaderLayout pattern; never pass a
 * backdrop whose recorded layer contains the drawing surface itself (that
 * feedback crashed Settings in the first 0.0.8-test3 build). With glass
 * OFF the surface is the plain solid colour; on a shader-less device with
 * glass ON it degrades to the shared frosted approximation.
 */
@Composable
fun Modifier.ownLayerGlass(backdrop: Backdrop, shape: Shape, solid: Color, tint: Color? = null): Modifier {
    return if (GlassQuality.effectsSupported) {
        this.liquidGlass(
            backdrop,
            glassSpec(
                shape = shape,
                tint = tint ?: glassFill(),
                blur = 18.dp,
                refractionHeight = 14.dp,
                refractionAmount = 22.dp,
            ),
        )
    } else if (GlassSettings.enabled && !GlassSettings.liteMode) {
        this
            .clip(shape)
            .background(glassFill())
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.05f),
                    ),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    } else {
        this.clip(shape).background(solid)
    }
}
