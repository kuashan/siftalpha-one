package com.siftalpha.studio

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siftalpha.studio.presentation.NormalProjectPrimaryActionPolicy
import com.siftalpha.studio.project.ProjectStore
import com.siftalpha.studio.runtime.ExternalProviderReadiness
import com.siftalpha.studio.runtime.ProjectRuntimeSelection
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.ui.theme.StudioTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val Ink = Color(0xFF040817)
private val InkSoft = Color(0xFF071027)
private val Panel = Color(0xFF0B1631)
private val PanelStrong = Color(0xFF101D3D)
private val PanelSoft = Color(0xFF0D1730)
private val Blue = Color(0xFF387DFF)
private val ElectricBlue = Color(0xFF5C8CFF)
private val Cyan = Color(0xFF39DFFF)
private val Violet = Color(0xFFA84CFF)
private val Magenta = Color(0xFFD857FF)
private val Green = Color(0xFF34D99A)
private val Amber = Color(0xFFFFC65A)
private val Red = Color(0xFFFF4F72)
private val TextPrimary = Color(0xFFF7FAFF)
private val Muted = Color(0xFF9BAAD0)
private val MutedDeep = Color(0xFF6D7CA6)
private val Border = Color(0xFF23355F)

private val NormalColors = darkColorScheme(
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelStrong,
    onSurfaceVariant = Muted,
    primary = Blue,
    onPrimary = Color.White,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Violet,
    onTertiary = Color.White,
    error = Red,
    onError = Color.White,
)

@Composable
internal fun SiftAlphaNormalTheme(content: @Composable () -> Unit) {
    StudioTheme(darkTheme = true) {
        MaterialTheme(
            colorScheme = NormalColors,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

@Composable
internal fun SiftAlphaBrandTransition(content: @Composable () -> Unit) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    var visible by rememberSaveable { mutableStateOf(true) }
    val timeline = remember { Animatable(if (motion) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (motion) {
            // One continuous native Compose sequence:
            // 01 chaos from the edges -> 02 S-flow convergence ->
            // 03 code/nebula S formation -> 04 real app logo.
            timeline.animateTo(1f, tween(4100, easing = LinearEasing))
            delay(500)
        } else {
            timeline.snapTo(1f)
            delay(3600)
        }
        visible = false
    }

    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(if (motion) 300 else 120)),
        ) {
            SiftAlphaLaunchMotion(
                progress = timeline.value,
                motion = motion,
            )
        }
    }
}

private val BrandCodeLexicon = listOf(
    "AI", "101", "</>", "{}", "0x", "Py", "JS",
    "{ }", "[ ]", "01", "λ", "Σ", "API", "def",
)

private data class BrandCodeParticle(
    val text: String,
    val upper: Boolean,
    val lane: Float,
    val delay: Float,
)

private val BrandCodeParticles = List(112) { index ->
    val upper = index < 56
    val local = if (upper) index else index - 56
    BrandCodeParticle(
        text = BrandCodeLexicon[index % BrandCodeLexicon.size],
        upper = upper,
        lane = local / 55f,
        delay = (local % 14) * .009f + if (upper) 0f else .019f,
    )
}

private fun cubicBezier(
    start: Float,
    control1: Float,
    control2: Float,
    end: Float,
    t: Float,
): Float {
    val u = 1f - t
    return u * u * u * start +
        3f * u * u * t * control1 +
        3f * u * t * t * control2 +
        t * t * t * end
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun flowEnvelope(phase: Float): Float {
    val enter = smoothStep((phase / .18f).coerceIn(0f, 1f))
    val exit = 1f - smoothStep(((phase - .86f) / .14f).coerceIn(0f, 1f))
    return enter * exit
}

private fun sceneAlpha(
    progress: Float,
    start: Float,
    inEnd: Float,
    outStart: Float,
    end: Float,
): Float {
    val enter =
        if (inEnd <= start) 1f
        else smoothStep(((progress - start) / (inEnd - start)).coerceIn(0f, 1f))
    val exit =
        if (end <= outStart) 1f
        else 1f -
            smoothStep(
                ((progress - outStart) / (end - outStart)).coerceIn(0f, 1f),
            )
    return enter * exit
}

@Composable
private fun SiftAlphaLaunchMotion(
    progress: Float,
    motion: Boolean,
) {
    val p = progress.coerceIn(0f, 1f)
    val stage1Alpha = sceneAlpha(p, 0f, .025f, .21f, .31f)
    val stage2Alpha = sceneAlpha(p, .18f, .27f, .48f, .59f)
    val stage3Alpha = sceneAlpha(p, .46f, .55f, .76f, .86f)
    val stage4Alpha = smoothStep(((p - .73f) / .18f).coerceIn(0f, 1f))

    val flowTransition = rememberInfiniteTransition(label = "siftalpha-launch-flow")
    val upperClock by if (motion) {
        flowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2050, easing = LinearEasing),
            ),
            label = "launch-upper",
        )
    } else {
        remember { mutableStateOf(.54f) }
    }
    val lowerClock by if (motion) {
        flowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2320, easing = LinearEasing),
            ),
            label = "launch-lower",
        )
    } else {
        remember { mutableStateOf(.48f) }
    }
    val breathe by if (motion) {
        flowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1050),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "launch-logo-breathe",
        )
    } else {
        remember { mutableStateOf(.5f) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050A15),
                        Color(0xFF020711),
                        Color.Black,
                    ),
                ),
            ),
    ) {
        val upperEndpointX = .50f + 34.dp.value / maxWidth.value
        val upperEndpointY = .445f - 36.dp.value / maxHeight.value
        val lowerEndpointX = .50f - 34.dp.value / maxWidth.value
        val lowerEndpointY = .445f + 36.dp.value / maxHeight.value

        fun codeSPoint(u: Float): Offset {
            val t = u.coerceIn(0f, 1f)
            return when {
                t < .34f -> {
                    val q = t / .34f
                    Offset(
                        cubicBezier(
                            upperEndpointX,
                            .50f,
                            .405f,
                            .425f,
                            q,
                        ),
                        cubicBezier(
                            upperEndpointY,
                            .355f,
                            .365f,
                            .430f,
                            q,
                        ),
                    )
                }
                t < .68f -> {
                    val q = (t - .34f) / .34f
                    Offset(
                        cubicBezier(
                            .425f,
                            .385f,
                            .615f,
                            .575f,
                            q,
                        ),
                        cubicBezier(
                            .430f,
                            .455f,
                            .480f,
                            .505f,
                            q,
                        ),
                    )
                }
                else -> {
                    val q = (t - .68f) / .32f
                    Offset(
                        cubicBezier(
                            .575f,
                            .600f,
                            .500f,
                            lowerEndpointX,
                            q,
                        ),
                        cubicBezier(
                            .505f,
                            .545f,
                            .565f,
                            lowerEndpointY,
                            q,
                        ),
                    )
                }
            }
        }

        fun stage1Point(
            upper: Boolean,
            lane: Float,
            t: Float,
            clock: Float,
        ): Offset {
            val eased = smoothStep(t)
            val laneSigned = lane * 2f - 1f
            val startX = if (upper) 1.10f else -.10f
            val startY =
                if (upper) .02f + lane * .34f
                else .64f + lane * .34f
            val endX = if (upper) .57f else .43f
            val endY = if (upper) .44f else .56f
            val chaos = (1f - eased)
            val angle =
                clock * 2f * PI.toFloat() +
                    lane * 7.4f +
                    eased * 2.2f

            return Offset(
                cubicBezier(
                    startX,
                    if (upper) .91f else .09f,
                    if (upper) .70f else .30f,
                    endX,
                    eased,
                ) +
                    kotlin.math.cos(angle) *
                        (.045f + .025f * kotlin.math.abs(laneSigned)) *
                        chaos,
                cubicBezier(
                    startY,
                    if (upper) .10f + lane * .26f else .88f - lane * .26f,
                    if (upper) .34f + lane * .10f else .66f - lane * .10f,
                    endY,
                    eased,
                ) +
                    sin(angle) * .033f * chaos +
                    laneSigned * .026f * chaos,
            )
        }

        fun stage2Point(
            upper: Boolean,
            lane: Float,
            t: Float,
            clock: Float,
        ): Offset {
            val eased = smoothStep(t)
            val laneSigned = lane * 2f - 1f
            val targetX = if (upper) lowerEndpointX else upperEndpointX
            val targetY = if (upper) lowerEndpointY else upperEndpointY

            // Cross-target S flow: upper-right wraps around right/bottom to the lower S end;
            // lower-left wraps around left/top to the upper S end.
            val split = .56f
            val base =
                if (eased < split) {
                    val q = eased / split
                    if (upper) {
                        Offset(
                            cubicBezier(1.10f, .98f, .84f, .665f, q),
                            cubicBezier(
                                .03f + .22f * lane,
                                .10f + .11f * lane,
                                .49f + .07f * lane,
                                .615f + .025f * laneSigned,
                                q,
                            ),
                        )
                    } else {
                        Offset(
                            cubicBezier(-.10f, .02f, .16f, .335f, q),
                            cubicBezier(
                                .73f + .20f * lane,
                                .80f - .10f * lane,
                                .40f - .06f * lane,
                                .275f - .025f * laneSigned,
                                q,
                            ),
                        )
                    }
                } else {
                    val q = (eased - split) / (1f - split)
                    if (upper) {
                        Offset(
                            cubicBezier(.665f, .625f, .485f, targetX, q),
                            cubicBezier(
                                .615f + .025f * laneSigned,
                                .675f,
                                .635f,
                                targetY,
                                q,
                            ),
                        )
                    } else {
                        Offset(
                            cubicBezier(.335f, .375f, .515f, targetX, q),
                            cubicBezier(
                                .275f - .025f * laneSigned,
                                .215f,
                                .255f,
                                targetY,
                                q,
                            ),
                        )
                    }
                }

            val taper = (1f - eased) * (1f - eased)
            val direction = if (upper) 1f else -1f
            val angle =
                direction *
                    (
                        eased * 1.64f * PI.toFloat() +
                            clock * 2f * PI.toFloat()
                        ) +
                    laneSigned * 1.28f
            val orbit =
                (.034f + .014f * kotlin.math.abs(laneSigned)) * taper

            return Offset(
                base.x +
                    kotlin.math.cos(angle) * orbit +
                    laneSigned * .020f * taper,
                base.y +
                    sin(angle) * orbit * .68f +
                    laneSigned * .022f * taper,
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w * .50f, h * .445f)

            // Deep-space glow shared by all stages.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Cyan.copy(alpha = .065f),
                        Blue.copy(alpha = .035f),
                        Color.Transparent,
                    ),
                    center = Offset(w * .76f, h * .16f),
                    radius = size.maxDimension * .50f,
                ),
                radius = size.maxDimension * .48f,
                center = Offset(w * .76f, h * .16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Violet.copy(alpha = .055f),
                        Color.Transparent,
                    ),
                    center = Offset(w * .18f, h * .82f),
                    radius = size.maxDimension * .42f,
                ),
                radius = size.maxDimension * .40f,
                center = Offset(w * .18f, h * .82f),
            )

            fun drawFlowBundle(
                alpha: Float,
                convergence: Boolean,
            ) {
                if (alpha <= .004f) return
                val strands = if (convergence) 38 else 32
                val samples = if (convergence) 30 else 24

                repeat(2) { side ->
                    val upper = side == 0
                    val clock = if (upper) upperClock else lowerClock

                    repeat(strands) { strand ->
                        val lane = strand / (strands - 1f)
                        val highlightPhase =
                            (
                                clock +
                                    strand *
                                        if (upper) .037f else .043f +
                                    lane * if (upper) .15f else .19f
                                ) % 1f

                        fun point(t: Float): Offset {
                            val n =
                                if (convergence) {
                                    stage2Point(
                                        upper,
                                        lane,
                                        t,
                                        clock,
                                    )
                                } else {
                                    stage1Point(
                                        upper,
                                        lane,
                                        t,
                                        clock,
                                    )
                                }
                            return Offset(n.x * w, n.y * h)
                        }

                        var previous = point(0f)
                        for (sample in 1..samples) {
                            val t = sample / samples.toFloat()
                            val current = point(t)
                            val distance =
                                kotlin.math.abs(t - highlightPhase)
                            val wrapped =
                                kotlin.math.min(distance, 1f - distance)
                            val pulse =
                                1f -
                                    (wrapped / .15f).coerceIn(0f, 1f)
                            val edgeFade =
                                smoothStep((t / .06f).coerceIn(0f, 1f)) *
                                    (
                                        1f -
                                            smoothStep(
                                                ((t - .985f) / .015f)
                                                    .coerceIn(0f, 1f),
                                            )
                                        )
                            val color =
                                if (upper) {
                                    when (strand % 4) {
                                        0 -> Cyan
                                        1 -> ElectricBlue
                                        2 -> Blue
                                        else -> Color.White
                                    }
                                } else {
                                    when (strand % 4) {
                                        0 -> Violet
                                        1 -> Cyan
                                        2 -> ElectricBlue
                                        else -> Color.White
                                    }
                                }

                            drawLine(
                                color = color.copy(
                                    alpha =
                                        alpha *
                                            edgeFade *
                                            (.055f + .40f * pulse),
                                ),
                                start = previous,
                                end = current,
                                strokeWidth =
                                    (.42f + (strand % 6) * .10f).dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            previous = current
                        }
                    }
                }
            }

            drawFlowBundle(stage1Alpha, convergence = false)
            drawFlowBundle(stage2Alpha, convergence = true)

            // High-speed glowing points with short tails for stage 1.
            if (stage1Alpha > .004f) {
                val count = 156
                repeat(count) { index ->
                    val upper = index % 2 == 0
                    val lane = (index % 52) / 51f
                    val raw =
                        (
                            (if (upper) upperClock else lowerClock) +
                                index * .027f +
                                lane * .13f
                            ) % 1f
                    val t = smoothStep(raw)
                    val n = stage1Point(
                        upper,
                        lane,
                        t,
                        if (upper) upperClock else lowerClock,
                    )
                    val prior = stage1Point(
                        upper,
                        lane,
                        (t - .025f).coerceAtLeast(0f),
                        if (upper) upperClock else lowerClock,
                    )
                    val life = flowEnvelope(raw) * stage1Alpha
                    val color =
                        if (upper) {
                            if (index % 3 == 0) Cyan else ElectricBlue
                        } else {
                            if (index % 3 == 0) Violet else Cyan
                        }
                    val point = Offset(n.x * w, n.y * h)
                    val tail = Offset(prior.x * w, prior.y * h)

                    drawLine(
                        color = color.copy(alpha = .23f * life),
                        start = tail,
                        end = point,
                        strokeWidth = .85.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = color.copy(alpha = .70f * life),
                        radius = (.75f + (index % 4) * .28f).dp.toPx(),
                        center = point,
                    )
                }
            }

            // Stage 3: dense nebula/data fragments build a volumetric S.
            if (stage3Alpha > .004f) {
                val fragmentCount = 330
                repeat(fragmentCount) { index ->
                    val u = index / (fragmentCount - 1f)
                    val base = codeSPoint(u)
                    val rotation =
                        index * 2.399963f +
                            upperClock * 2f * PI.toFloat() *
                                (.35f + (index % 5) * .04f)
                    val depth =
                        sin(index * 1.27f) * .5f + .5f
                    val thickness =
                        (
                            .010f +
                                (index % 9) * .0017f
                            ) *
                            (.72f + .36f * depth)
                    val x =
                        (base.x +
                            kotlin.math.cos(rotation) * thickness) * w
                    val y =
                        (base.y +
                            sin(rotation) * thickness * .72f) * h
                    val twinkle =
                        .48f +
                            .52f *
                                kotlin.math.abs(
                                    sin(
                                        lowerClock * 2f * PI.toFloat() +
                                            index * .31f,
                                    ),
                                )
                    val color =
                        when {
                            u < .43f ->
                                if (index % 3 == 0) Cyan else ElectricBlue
                            u < .62f ->
                                if (index % 2 == 0) ElectricBlue else Violet
                            else ->
                                if (index % 3 == 0) Blue else Violet
                        }

                    if (index % 4 == 0) {
                        drawRoundRect(
                            color = color.copy(
                                alpha =
                                    stage3Alpha *
                                        (.27f + .55f * twinkle),
                            ),
                            topLeft = Offset(x, y),
                            size = Size(
                                (2.2f + (index % 5) * .72f).dp.toPx(),
                                (1.9f + (index % 4) * .62f).dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(
                                1.0.dp.toPx(),
                                1.0.dp.toPx(),
                            ),
                        )
                    } else {
                        drawCircle(
                            color = color.copy(
                                alpha =
                                    stage3Alpha *
                                        (.24f + .52f * twinkle),
                            ),
                            radius =
                                (.72f + (index % 5) * .31f).dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }

                // Sparse orbiting dust makes the S feel like a small galaxy/nebula.
                repeat(76) { index ->
                    val angle =
                        index * .83f +
                            upperClock * 2f * PI.toFloat() *
                                if (index % 2 == 0) 1f else -1f
                    val radius =
                        size.minDimension *
                            (.11f + (index % 13) / 13f * .15f)
                    val x =
                        center.x +
                            kotlin.math.cos(angle) * radius
                    val y =
                        center.y +
                            sin(angle) * radius * .78f
                    val color = if (index % 2 == 0) Cyan else Violet
                    drawCircle(
                        color = color.copy(
                            alpha =
                                stage3Alpha *
                                    (.08f + (index % 5) * .018f),
                        ),
                        radius = (.45f + (index % 4) * .18f).dp.toPx(),
                        center = Offset(x, y),
                    )
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = .18f * stage3Alpha),
                            Violet.copy(alpha = .12f * stage3Alpha),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * .31f,
                    ),
                    radius = size.minDimension * .29f,
                    center = center,
                )
            }

            // Stage 4 final breathing halo behind the real app logo.
            if (stage4Alpha > .004f) {
                val breath =
                    .72f + .28f * breathe
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = .28f * breath * stage4Alpha),
                            Blue.copy(alpha = .17f * breath * stage4Alpha),
                            Violet.copy(alpha = .13f * breath * stage4Alpha),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * .39f,
                    ),
                    radius =
                        size.minDimension *
                            (.34f + .018f * breathe),
                    center = center,
                )
            }
        }

        // Required code glyphs ride stages 1/2 and then become part of the stage-3 S.
        if (motion && p < .88f) {
            BrandCodeParticles.forEachIndexed { index, particle ->
                val lane = particle.lane
                val clock = if (particle.upper) upperClock else lowerClock
                val rawPhase =
                    (
                        clock +
                            index * .061f +
                            lane * .131f +
                            particle.delay
                        ) % 1f
                val t = smoothStep(rawPhase)
                val life = flowEnvelope(rawPhase)

                val startN =
                    stage1Point(
                        particle.upper,
                        lane,
                        t,
                        clock,
                    )
                val convN =
                    stage2Point(
                        particle.upper,
                        lane,
                        t,
                        clock,
                    )
                val convergenceBlend =
                    smoothStep(((p - .18f) / .32f).coerceIn(0f, 1f))
                val streamX =
                    startN.x * (1f - convergenceBlend) +
                        convN.x * convergenceBlend
                val streamY =
                    startN.y * (1f - convergenceBlend) +
                        convN.y * convergenceBlend

                val u =
                    index.toFloat() /
                        (BrandCodeParticles.size - 1).toFloat()
                val sBase = codeSPoint(u)
                val formAngle =
                    index * 1.81f +
                        clock * 2f * PI.toFloat() * .42f
                val spread =
                    .008f + (index % 7) * .00155f
                val formX =
                    sBase.x + kotlin.math.cos(formAngle) * spread
                val formY =
                    sBase.y + sin(formAngle) * spread * .72f
                val formationBlend =
                    smoothStep(((p - .43f) / .30f).coerceIn(0f, 1f))

                val x =
                    streamX * (1f - formationBlend) +
                        formX * formationBlend
                val y =
                    streamY * (1f - formationBlend) +
                        formY * formationBlend
                val alpha =
                    (
                        kotlin.math.max(stage1Alpha, stage2Alpha) *
                            life *
                            (1f - formationBlend) +
                            stage3Alpha *
                                formationBlend *
                                (.60f + .40f * life)
                        ).coerceIn(0f, 1f)

                Text(
                    text = particle.text,
                    color =
                        when {
                            u < .43f ->
                                if (index % 3 == 0) Cyan else ElectricBlue
                            u < .62f ->
                                if (index % 2 == 0) ElectricBlue else Violet
                            else ->
                                if (index % 3 == 0) Blue else Violet
                        },
                    fontSize =
                        when {
                            particle.text.length >= 4 -> 7.sp
                            particle.text.length == 3 -> 8.sp
                            else -> 10.sp
                        },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .offset(
                            x = maxWidth * x - 16.dp,
                            y = maxHeight * y - 8.dp,
                        )
                        .alpha(alpha)
                        .graphicsLayer {
                            val scale =
                                .96f - .14f * formationBlend
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        // Only the final stage shows brand copy. No stage titles, sequence numbers or captions.
        if (stage4Alpha > .004f) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-10).dp)
                    .alpha(stage4Alpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(196.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val pulse = .70f + .30f * breathe
                        drawRoundRect(
                            color = Cyan.copy(
                                alpha = .24f * pulse * stage4Alpha,
                            ),
                            topLeft = Offset(
                                6.dp.toPx(),
                                6.dp.toPx(),
                            ),
                            size = Size(
                                size.width - 12.dp.toPx(),
                                size.height - 12.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(
                                42.dp.toPx(),
                                42.dp.toPx(),
                            ),
                            style = Stroke(1.6.dp.toPx()),
                        )
                    }
                    OriginalLogoMark(
                        modifier = Modifier
                            .size(154.dp)
                            .graphicsLayer {
                                val scale =
                                    .965f + .035f * breathe
                                scaleX = scale
                                scaleY = scale
                            },
                        alpha = stage4Alpha,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "INTELLIGENCE IN MOTION",
                    color = Color(0xFFB8C3E4),
                    fontSize = 10.sp,
                    letterSpacing = 3.8.sp,
                )
            }
        }
    }
}

@Composable
private fun SiftRibbonMark(
    modifier: Modifier = Modifier,
    reveal: Float = 1f,
) {
    val progress = reveal.coerceIn(0f, 1f)
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 100f
        fun path(build: Path.() -> Unit): Path = Path().apply(build)

        val upper = path {
            moveTo(72f * sx, 7f * sy)
            cubicTo(54f * sx, 4f * sy, 33f * sx, 10f * sy, 22f * sx, 23f * sy)
            cubicTo(14f * sx, 33f * sy, 18f * sx, 42f * sy, 35f * sx, 48f * sy)
            lineTo(49f * sx, 53f * sy)
            cubicTo(39f * sx, 47f * sy, 31f * sx, 40f * sy, 35f * sx, 32f * sy)
            cubicTo(40f * sx, 23f * sy, 56f * sx, 21f * sy, 71f * sx, 24f * sy)
            lineTo(87f * sx, 10f * sy)
            cubicTo(82f * sx, 8f * sy, 77f * sx, 7f * sy, 72f * sx, 7f * sy)
            close()
        }
        val fold = path {
            moveTo(35f * sx, 32f * sy)
            cubicTo(45f * sx, 29f * sy, 60f * sx, 26f * sy, 71f * sx, 24f * sy)
            cubicTo(79f * sx, 29f * sy, 86f * sx, 36f * sy, 88f * sx, 43f * sy)
            cubicTo(91f * sx, 51f * sy, 86f * sx, 55f * sy, 79f * sx, 51f * sy)
            lineTo(54f * sx, 39f * sy)
            cubicTo(48f * sx, 36f * sy, 41f * sx, 34f * sy, 35f * sx, 32f * sy)
            close()
        }
        val lower = path {
            moveTo(48f * sx, 50f * sy)
            cubicTo(62f * sx, 53f * sy, 75f * sx, 57f * sy, 79f * sx, 65f * sy)
            cubicTo(86f * sx, 79f * sy, 74f * sx, 91f * sy, 59f * sx, 96f * sy)
            cubicTo(43f * sx, 101f * sy, 27f * sx, 95f * sy, 17f * sx, 85f * sy)
            lineTo(32f * sx, 69f * sy)
            cubicTo(42f * sx, 78f * sy, 56f * sx, 81f * sy, 65f * sx, 76f * sy)
            cubicTo(72f * sx, 72f * sy, 68f * sx, 66f * sy, 56f * sx, 62f * sy)
            lineTo(39f * sx, 56f * sy)
            cubicTo(42f * sx, 54f * sy, 45f * sx, 52f * sy, 48f * sx, 50f * sy)
            close()
        }

        val glow = 0.20f + 0.24f * progress
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Cyan.copy(alpha = glow), Violet.copy(alpha = glow * .55f), Color.Transparent),
                center = Offset(size.width * .54f, size.height * .50f),
                radius = size.minDimension * .66f,
            ),
            radius = size.minDimension * .56f,
            center = Offset(size.width * .52f, size.height * .52f),
        )

        val upperAlpha = (progress / .48f).coerceIn(0f, 1f)
        val foldAlpha = ((progress - .20f) / .50f).coerceIn(0f, 1f)
        val lowerAlpha = ((progress - .34f) / .66f).coerceIn(0f, 1f)

        drawPath(
            upper,
            brush = Brush.linearGradient(
                colors = listOf(Violet.copy(alpha = upperAlpha), ElectricBlue.copy(alpha = upperAlpha), Cyan.copy(alpha = upperAlpha)),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            ),
        )
        drawPath(
            fold,
            brush = Brush.linearGradient(
                colors = listOf(Cyan.copy(alpha = foldAlpha), Blue.copy(alpha = foldAlpha), Violet.copy(alpha = foldAlpha)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
        )
        drawPath(
            lower,
            brush = Brush.linearGradient(
                colors = listOf(Cyan.copy(alpha = lowerAlpha), ElectricBlue.copy(alpha = lowerAlpha), Magenta.copy(alpha = lowerAlpha)),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            ),
        )

        drawPath(
            upper,
            color = Color.White.copy(alpha = .18f * upperAlpha),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            lower,
            color = Color.White.copy(alpha = .12f * lowerAlpha),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val transition = rememberInfiniteTransition(label = "siftalpha-aurora")
    val phase by if (animate && motion) {
        transition.animateFloat(
            initialValue = -0.06f,
            targetValue = 0.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(6200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "aurora-phase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Canvas(modifier.background(Ink)) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF173D88).copy(alpha = .48f), Color.Transparent),
                center = Offset(size.width * (.82f + phase), size.height * .10f),
                radius = size.maxDimension * .72f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF491B75).copy(alpha = .36f), Color.Transparent),
                center = Offset(size.width * .08f, size.height * (.80f - phase)),
                radius = size.maxDimension * .58f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0E6A91).copy(alpha = .20f), Color.Transparent),
                center = Offset(size.width * .72f, size.height * .72f),
                radius = size.maxDimension * .48f,
            ),
        )

        val waveY = size.height * .78f
        val p1 = Path().apply {
            moveTo(-80f, waveY)
            cubicTo(size.width * .20f, size.height * (.63f + phase), size.width * .55f, size.height * (.94f - phase), size.width + 80f, size.height * .70f)
        }
        val p2 = Path().apply {
            moveTo(-100f, size.height * .86f)
            cubicTo(size.width * .28f, size.height * (.70f - phase), size.width * .63f, size.height * (.98f + phase), size.width + 100f, size.height * .78f)
        }
        val p3 = Path().apply {
            moveTo(-40f, size.height * .72f)
            cubicTo(size.width * .28f, size.height * (.90f + phase), size.width * .72f, size.height * (.57f - phase), size.width + 70f, size.height * .73f)
        }
        drawPath(
            p1,
            brush = Brush.linearGradient(listOf(Violet.copy(.08f), Blue.copy(.55f), Cyan.copy(.72f), Color.Transparent)),
            style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            p2,
            brush = Brush.linearGradient(listOf(Color.Transparent, Violet.copy(.58f), Blue.copy(.42f), Color.Transparent)),
            style = Stroke(18.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            p3,
            brush = Brush.linearGradient(listOf(Color.Transparent, Cyan.copy(.26f), Violet.copy(.34f), Color.Transparent)),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun GlowCard(
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (prominent) Cyan.copy(alpha = .46f) else Border
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (prominent) Color(0xFF0E2F63).copy(alpha = .92f) else Panel.copy(alpha = .94f),
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun GradientPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Red else Blue,
            contentColor = Color.White,
            disabledContainerColor = PanelStrong,
            disabledContentColor = MutedDeep,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun OriginalLogoMark(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Image(
        painter = painterResource(R.drawable.siftalpha_launcher_art),
        contentDescription = stringResource(R.string.brand_mark_content_description),
        modifier = modifier.alpha(alpha.coerceIn(0f, 1f)).clip(RoundedCornerShape(24.dp)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun TinyBrandMark(modifier: Modifier = Modifier) {
    OriginalLogoMark(modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SiftAlphaNormalHomeScreen(
    state: HomeState,
    visibleProjects: List<ProjectStore.ProjectSummary>,
    query: String,
    filterIndex: Int,
    projectDirectoryReady: Boolean,
    versionName: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Int) -> Unit,
    onImport: () -> Unit,
    onNewProject: () -> Unit,
    onProjectLocation: () -> Unit,
    onOpenProject: (ProjectStore.ProjectSummary) -> Unit,
    onShowDetails: (ProjectStore.ProjectSummary) -> Unit,
    onDeleteProject: (ProjectStore.ProjectSummary) -> Unit,
    onMore: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TinyBrandMark(Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onMore) {
                            Text(
                                text = stringResource(R.string.home_more_title),
                                color = Cyan,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )
            },
        ) { pad ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.brand_home_hello),
                            color = TextPrimary,
                            fontSize = 27.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.brand_normal_intro),
                            color = TextPrimary,
                            fontSize = 25.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = stringResource(R.string.brand_tagline_secondary),
                            color = Muted,
                            fontSize = 13.sp,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HomeHeroAction(
                            title = stringResource(R.string.home_import_title),
                            subtitle = stringResource(R.string.brand_import_short),
                            kind = HomeHeroKind.IMPORT,
                            modifier = Modifier.weight(1f),
                            onClick = onImport,
                        )
                        HomeHeroAction(
                            title = stringResource(R.string.brand_new_project_title),
                            subtitle = stringResource(R.string.brand_new_project_hint),
                            kind = HomeHeroKind.NEW,
                            modifier = Modifier.weight(1f),
                            onClick = onNewProject,
                        )
                    }
                }

                item {
                    LocationPrompt(
                        rootName = state.rootName,
                        error = state.projectError,
                        onClick = onProjectLocation,
                    )
                }

                item {
                    GlowCard {
                        Text(
                            text = stringResource(R.string.home_all_projects, state.projects.size),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.home_project_search_hint)) },
                            singleLine = true,
                            enabled = projectDirectoryReady,
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                R.string.home_filter_all,
                                R.string.home_filter_python,
                                R.string.home_filter_node,
                            ).forEachIndexed { index, labelRes ->
                                FilterChip(
                                    selected = filterIndex == index,
                                    onClick = { onFilterChange(index) },
                                    enabled = projectDirectoryReady,
                                    label = { Text(stringResource(labelRes), fontSize = 11.sp) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.home_project_count_filtered,
                                visibleProjects.size,
                                state.projects.size,
                            ),
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.brand_my_projects),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )

                    }
                }

                when {
                    !state.rootSelected -> item {
                        EmptyProjectCard(
                            title = stringResource(R.string.brand_no_projects_title),
                            detail = stringResource(R.string.home_project_location_help),
                            action = stringResource(R.string.home_project_location_action),
                            onAction = onProjectLocation,
                        )
                    }
                    state.projectError != null -> item {
                        EmptyProjectCard(
                            title = stringResource(R.string.home_root_access_failed),
                            detail = stringResource(R.string.home_root_read_failed, state.projectError),
                            action = stringResource(R.string.home_project_location_action),
                            onAction = onProjectLocation,
                            error = true,
                        )
                    }
                    state.projects.isEmpty() -> item {
                        EmptyProjectCard(
                            title = stringResource(R.string.brand_no_projects_title),
                            detail = stringResource(R.string.brand_empty_project_hint),
                            action = stringResource(R.string.home_import_title),
                            onAction = onImport,
                        )
                    }
                    visibleProjects.isEmpty() -> item {
                        EmptyProjectCard(
                            title = stringResource(R.string.home_no_matching_projects),
                            detail = stringResource(R.string.home_project_count_filtered, 0, state.projects.size),
                            action = stringResource(R.string.home_refresh_projects),
                            onAction = { onQueryChange("") },
                        )
                    }
                    else -> items(visibleProjects, key = { it.documentId }) { project ->
                        ProjectCardNormal(
                            project = project,
                            onOpen = { onOpenProject(project) },
                            onDetails = { onShowDetails(project) },
                            onDelete = { onDeleteProject(project) },
                        )
                    }
                }

                item {
                    Text(
                        text = versionName,
                        color = MutedDeep,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private enum class HomeHeroKind { IMPORT, NEW }

@Composable
private fun HomeHeroAction(
    title: String,
    subtitle: String,
    kind: HomeHeroKind,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val gradient = if (kind == HomeHeroKind.IMPORT) {
        Brush.linearGradient(listOf(Color(0xFF044B72), Color(0xFF0875A1), Color(0xFF173A88)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF2E357E), Color(0xFF5B2ED1), Color(0xFF7E2DEB)))
    }
    Surface(
        modifier = modifier.heightIn(min = 142.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (kind == HomeHeroKind.IMPORT) Cyan.copy(.45f) else Violet.copy(.50f)),
    ) {
        Box(Modifier.fillMaxSize().background(gradient).padding(16.dp)) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).size(44.dp).background(Color.White.copy(.10f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (kind == HomeHeroKind.IMPORT) FolderGlyph(Modifier.size(25.dp), Color.White)
                else PlusGlyph(Modifier.size(24.dp), Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Color.White.copy(.74f), fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun LocationPrompt(rootName: String?, error: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        color = PanelSoft.copy(.92f),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (error != null) Red.copy(.45f) else Border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LocationGlyph(Modifier.size(22.dp), if (error != null) Red else Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_project_location_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        error != null -> error
                        !rootName.isNullOrBlank() -> rootName
                        else -> stringResource(R.string.home_project_location_action)
                    },
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChevronGlyph(Modifier.size(18.dp), Muted)
        }
    }
}

@Composable
private fun ProjectCardNormal(
    project: ProjectStore.ProjectSummary,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val ready = project.run.isNotBlank()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelSoft.copy(.95f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Border.copy(alpha = .92f)),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(46.dp).background(
                        brush = Brush.linearGradient(
                            if (ready) {
                                listOf(Color(0xFF0C6B50), Green)
                            } else {
                                listOf(Color(0xFF5633C5), Violet)
                            },
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    FolderGlyph(Modifier.size(24.dp), Color.White)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        project.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = project.description.ifBlank {
                            stringResource(
                                if (ready) {
                                    R.string.brand_project_ready
                                } else {
                                    R.string.brand_project_needs_setup
                                },
                            )
                        },
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ChevronGlyph(Modifier.size(18.dp), Muted)
            }

            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.home_open), fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onDetails,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Border),
                ) {
                    Text(
                        stringResource(R.string.home_details),
                        color = TextPrimary,
                        fontSize = 11.sp,
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Red.copy(alpha = .42f)),
                ) {
                    Text(
                        stringResource(R.string.home_delete),
                        color = Red,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectCard(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    error: Boolean = false,
) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TinyBrandMark(Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(detail, color = if (error) Red else Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientPrimaryButton(action, onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SiftAlphaNormalWorkspaceScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onConfigure: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onSaveConfiguration: (Map<String, String>, Boolean) -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onOpenDeveloper: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    val resultReady =
        state.openEnabled && state.runtimeState == RuntimeState.EXITED_SUCCESS
    val errorState =
        state.runtimeState == RuntimeState.EXITED_ERROR ||
            state.runtimeState == RuntimeState.ENVIRONMENT_ERROR

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(Modifier.fillMaxSize())
        when {
            state.runtimeState == RuntimeState.PREPARING -> PrepareProjectScreen(
                state = state,
                onBack = onBack,
                onStop = onStop,
                onOpen = onOpen,
                onRefresh = onRefresh,
                onSelectRuntime = onSelectRuntime,
                onRequestPermission = onRequestPermission,
                onOpenTermux = onOpenTermux,
                onRecheckExternal = onRecheckExternal,
            )

            state.runtimeState == RuntimeState.STARTING ||
                state.runtimeState == RuntimeState.RUNNING ||
                state.primaryAction == NormalProjectPrimaryActionPolicy.Action.STOP -> RunProjectScreen(
                state = state,
                onBack = onBack,
                onStop = onStop,
                onOpen = onOpen,
                onRefresh = onRefresh,
                onSelectRuntime = onSelectRuntime,
                onRequestPermission = onRequestPermission,
                onOpenTermux = onOpenTermux,
                onRecheckExternal = onRecheckExternal,
            )

            resultReady -> ResultProjectScreen(
                state = state,
                onBack = onBack,
                onOpen = onOpen,
                onRun = onRun,
                onRefresh = onRefresh,
                onSelectRuntime = onSelectRuntime,
                onRequestPermission = onRequestPermission,
                onOpenTermux = onOpenTermux,
                onRecheckExternal = onRecheckExternal,
            )

            errorState -> RecoveryProjectScreen(
                state = state,
                onBack = onBack,
                onPrepare = onPrepare,
                onRun = onRun,
                onOpen = onOpen,
                onRefresh = onRefresh,
                onSelectRuntime = onSelectRuntime,
                onRequestPermission = onRequestPermission,
                onOpenTermux = onOpenTermux,
                onRecheckExternal = onRecheckExternal,
            )

            state.primaryAction == NormalProjectPrimaryActionPolicy.Action.CONFIGURE ->
                ConfigurationProjectScreen(
                    state = state,
                    onBack = onBack,
                    onSaveConfiguration = onSaveConfiguration,
                    onFallbackConfigure = onConfigure,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )

            state.primaryAction == NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT ->
                PrepareProjectScreen(
                    state = state,
                    onBack = onBack,
                    onPrepare = onPrepare,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )

            else -> ReadyProjectScreen(
                state = state,
                onBack = onBack,
                onRun = onRun,
                onOpen = onOpen,
                onRefresh = onRefresh,
                onSelectRuntime = onSelectRuntime,
                onOpenDeveloper = onOpenDeveloper,
                onRequestPermission = onRequestPermission,
                onOpenTermux = onOpenTermux,
                onRecheckExternal = onRecheckExternal,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            Surface(
                modifier = Modifier.padding(start = 8.dp).size(44.dp).clickable(role = Role.Button, onClick = onBack),
                color = Color.Transparent,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BackGlyph(Modifier.size(22.dp), TextPrimary)
                }
            }
        },
    )
}

@Composable
private fun ProjectIdentityCard(state: NormalProjectWorkspaceActivity.ScreenState) {
    val accent = when (state.runtimeState) {
        RuntimeState.RUNNING -> Cyan
        RuntimeState.EXITED_SUCCESS -> Green
        RuntimeState.EXITED_ERROR, RuntimeState.ENVIRONMENT_ERROR -> Red
        RuntimeState.PREPARING, RuntimeState.STARTING -> Violet
        RuntimeState.STOPPED_BY_USER -> Amber
        RuntimeState.UNKNOWN -> Blue
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelStrong.copy(alpha = .92f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = .16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .34f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.activityIndicatorVisible) {
                            DotPulse(Modifier.size(12.dp))
                        } else if (state.runtimeState == RuntimeState.EXITED_SUCCESS) {
                            CheckGlyph(Modifier.size(22.dp), accent)
                        } else {
                            TinyBrandMark(Modifier.size(34.dp))
                        }
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.statusLabel,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        state.projectName,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            state.message
                ?.takeUnless { it.contains("SIFTALPHA_") }
                ?.takeIf { it.isNotBlank() }
                ?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(message, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            if (state.activityIndicatorVisible) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = Color.White.copy(alpha = .07f),
                )
            }
        }
    }
}

@Composable
private fun PrepareProjectScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onPrepare: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    val detecting = stringResource(R.string.normal_prepare_phase_detecting)
    val compatibility = stringResource(R.string.normal_prepare_phase_compatibility)
    val environment = stringResource(R.string.normal_prepare_phase_environment)
    val installing = stringResource(R.string.normal_prepare_phase_installing)
    val verifying = stringResource(R.string.normal_prepare_phase_verifying)
    val ready = stringResource(R.string.normal_prepare_phase_ready)
    val phaseIndex = when (state.preparePhaseTitle) {
        detecting -> 0
        compatibility -> 1
        environment -> 2
        installing -> 3
        verifying -> 4
        ready -> 5
        else -> 0
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { NormalTopBar(stringResource(R.string.brand_prepare_title), onBack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            item { ProjectIdentityCard(state) }
            item {
                PrepareStepper(
                    activeIndex = phaseIndex,
                    active = state.runtimeState == RuntimeState.PREPARING,
                )
            }

            if (state.runtimeState == RuntimeState.PREPARING) {
                item {
                    GlowCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DocumentGlyph(Modifier.size(32.dp), ElectricBlue)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = state.preparePhaseTitle
                                        ?: stringResource(R.string.brand_prepare_working),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = state.preparePhaseDetail
                                        ?: stringResource(R.string.brand_prepare_working_detail),
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Cyan,
                            trackColor = Color.White.copy(alpha = .08f),
                        )
                    }
                }
                if (onStop != null) {
                    item {
                        GradientPrimaryButton(
                            stringResource(R.string.brand_action_stop),
                            onStop,
                            danger = true,
                        )
                    }
                }
            } else if (onPrepare != null) {
                item {
                    GradientPrimaryButton(
                        stringResource(R.string.brand_action_prepare),
                        onPrepare,
                        enabled = !state.busy,
                    )
                }
            }

            item {
                ProjectBaselineUtilities(
                    state = state,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )
            }
        }
    }
}

@Composable
private fun PrepareStepper(activeIndex: Int, active: Boolean) {
    val labels = listOf(
        stringResource(R.string.normal_prepare_phase_detecting),
        stringResource(R.string.normal_prepare_phase_compatibility),
        stringResource(R.string.normal_prepare_phase_environment),
        stringResource(R.string.normal_prepare_phase_installing),
        stringResource(R.string.normal_prepare_phase_verifying),
        stringResource(R.string.normal_prepare_phase_ready),
    )
    val subtitles = listOf(
        stringResource(R.string.normal_prepare_detecting_detail),
        stringResource(R.string.brand_prepare_step_analyze_detail),
        stringResource(R.string.normal_prepare_environment_detail),
        stringResource(R.string.normal_prepare_installing_detail),
        stringResource(R.string.normal_prepare_verifying_detail),
        stringResource(R.string.normal_prepare_ready_detail),
    )
    GlowCard {
        labels.forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StepBubble(index = index, activeIndex = activeIndex, active = active)
                    if (index < labels.lastIndex) {
                        Box(
                            Modifier.width(2.dp).height(26.dp).background(
                                if (index < activeIndex) Green.copy(.55f) else Border,
                            ),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp, bottom = 7.dp)) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (index <= activeIndex) TextPrimary else Muted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitles[index],
                        fontSize = 11.sp,
                        color = if (index == activeIndex) Cyan else MutedDeep,
                    )
                    if (index == activeIndex && active) {
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
                            color = Cyan,
                            trackColor = Color.White.copy(.07f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBubble(index: Int, activeIndex: Int, active: Boolean) {
    val completed = index < activeIndex || (!active && index <= activeIndex && activeIndex >= 5)
    val current = index == activeIndex
    val bg = when {
        completed -> Green
        current -> Blue
        else -> PanelStrong
    }
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, if (current) Cyan.copy(.7f) else Border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (completed) CheckGlyph(Modifier.size(14.dp), Color.White)
            else Text((index + 1).toString(), color = if (current) Color.White else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RuntimeLocationCompact(
    selection: ProjectRuntimeSelection,
    enabled: Boolean,
    onSelect: (ProjectRuntimeSelection) -> Unit,
) {
    GlowCard {
        Text(stringResource(R.string.brand_run_location), fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeChoice(
                label = stringResource(R.string.normal_runtime_internal_short),
                selected = selection == ProjectRuntimeSelection.EMBEDDED_R,
                enabled = enabled && selection != ProjectRuntimeSelection.EMBEDDED_R,
                modifier = Modifier.weight(1f),
            ) { onSelect(ProjectRuntimeSelection.EMBEDDED_R) }
            RuntimeChoice(
                label = stringResource(R.string.normal_runtime_external_short),
                selected = selection == ProjectRuntimeSelection.TERMUX,
                enabled = enabled && selection != ProjectRuntimeSelection.TERMUX,
                modifier = Modifier.weight(1f),
            ) { onSelect(ProjectRuntimeSelection.TERMUX) }
        }
        if (!enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.normal_runtime_selection_locked),
                color = MutedDeep,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun RuntimeChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = if (selected) Blue.copy(.24f) else PanelStrong,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Cyan.copy(.55f) else Border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) TextPrimary else Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ExternalRecoveryCard(
    readiness: ExternalProviderReadiness,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheck: () -> Unit,
) {
    GlowCard {
        Text(
            stringResource(R.string.brand_external_attention),
            color = Amber,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.brand_external_attention_detail),
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(12.dp))

        when (readiness) {
            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED -> {
                GradientPrimaryButton(
                    stringResource(R.string.normal_external_provider_allow),
                    onRequestPermission,
                )
            }

            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            -> {
                OutlinedButton(
                    onClick = onOpenTermux,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Border),
                ) {
                    Text(
                        stringResource(R.string.normal_external_provider_open_termux_action),
                        color = TextPrimary,
                    )
                }
            }

            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED -> {
                Text(
                    stringResource(R.string.normal_external_provider_check_required),
                    color = Muted,
                    fontSize = 11.sp,
                )
            }

            ExternalProviderReadiness.BRIDGE_CHECKING -> {
                Text(
                    stringResource(R.string.normal_external_provider_checking),
                    color = Muted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Cyan,
                    trackColor = Color.White.copy(alpha = .08f),
                )
            }

            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE -> {
                Text(
                    stringResource(R.string.normal_external_provider_no_response),
                    color = Muted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenTermux,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Text(
                            stringResource(R.string.normal_external_provider_open_termux_action),
                            color = TextPrimary,
                            fontSize = 11.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = onRecheck,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Text(
                            stringResource(R.string.normal_external_provider_recheck),
                            color = TextPrimary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            ExternalProviderReadiness.UNAVAILABLE -> {
                Text(
                    stringResource(R.string.normal_external_provider_unavailable),
                    color = Muted,
                    fontSize = 11.sp,
                )
            }

            ExternalProviderReadiness.READY -> Unit
        }
    }
}

@Composable
private fun ProjectBaselineUtilities(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
    includeOpen: Boolean = true,
    includeRefresh: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (includeOpen || includeRefresh) {
            GlowCard {
                Text(
                    text = stringResource(R.string.normal_project_actions_title),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (includeOpen) {
                        OutlinedButton(
                            onClick = onOpen,
                            enabled = state.openEnabled && !state.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Border),
                        ) {
                            Text(
                                stringResource(R.string.normal_project_open_action),
                                color = if (state.openEnabled && !state.busy) {
                                    TextPrimary
                                } else {
                                    MutedDeep
                                },
                            )
                        }
                    }
                    if (includeRefresh) {
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Border),
                        ) {
                            Text(
                                stringResource(R.string.normal_project_refresh_action),
                                color = if (!state.busy) TextPrimary else MutedDeep,
                            )
                        }
                    }
                }
            }
        }

        RuntimeLocationCompact(
            selection = state.runtimeSelection,
            enabled = state.runtimeSelectionCanChange && !state.busy,
            onSelect = onSelectRuntime,
        )

        if (
            state.runtimeSelection == ProjectRuntimeSelection.TERMUX &&
            state.externalReadiness != null &&
            state.externalReadiness != ExternalProviderReadiness.READY
        ) {
            ExternalRecoveryCard(
                readiness = state.externalReadiness,
                onRequestPermission = onRequestPermission,
                onOpenTermux = onOpenTermux,
                onRecheck = onRecheckExternal,
            )
        }
    }
}

@Composable
private fun ConfigurationProjectScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onSaveConfiguration: (Map<String, String>, Boolean) -> Unit,
    onFallbackConfigure: () -> Unit,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    val edits = remember(state.projectName, state.configurationFields) {
        mutableStateMapOf<String, String>()
    }
    val requiredMissing = state.configurationFields.any { field ->
        field.required &&
            !field.configured &&
            edits[field.key].orEmpty().isBlank()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { NormalTopBar(stringResource(R.string.brand_configuration_title), onBack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ProjectIdentityCard(state)
            }

            item {
                GlowCard {
                    Text(
                        stringResource(R.string.brand_configuration_intro),
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            if (state.configurationFields.isEmpty()) {
                item {
                    GlowCard {
                        Text(
                            stringResource(R.string.brand_configuration_none),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            stringResource(R.string.brand_configuration_none_detail),
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onFallbackConfigure,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.brand_action_configure))
                        }
                    }
                }
            } else {
                val requiredFields = state.configurationFields.filter { it.required }
                val optionalFields = state.configurationFields.filterNot { it.required }

                if (requiredFields.isNotEmpty()) {
                    item {
                        SectionHeader(
                            stringResource(R.string.brand_configuration_required),
                            stringResource(R.string.brand_configuration_required_detail),
                        )
                    }
                    items(requiredFields, key = { "required:${it.key}" }) { field ->
                        ConfigurationFieldCard(
                            field = field,
                            value = edits[field.key].orEmpty(),
                            onValueChange = { edits[field.key] = it },
                        )
                    }
                }

                if (optionalFields.isNotEmpty()) {
                    item {
                        SectionHeader(
                            stringResource(R.string.brand_configuration_optional),
                            stringResource(R.string.brand_configuration_optional_detail),
                        )
                    }
                    items(optionalFields, key = { "optional:${it.key}" }) { field ->
                        ConfigurationFieldCard(
                            field = field,
                            value = edits[field.key].orEmpty(),
                            onValueChange = { edits[field.key] = it },
                        )
                    }
                }

                item {
                    GradientPrimaryButton(
                        label = stringResource(R.string.brand_configuration_save),
                        onClick = { onSaveConfiguration(edits.toMap(), false) },
                        enabled = !requiredMissing && !state.busy,
                    )
                }
            }

            item {
                ProjectBaselineUtilities(
                    state = state,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ConfigurationFieldCard(
    field: NormalProjectWorkspaceActivity.ConfigurationFieldState,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val label = humanizeConfigurationKey(field.key)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelSoft.copy(.95f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (field.required && !field.configured && value.isBlank()) Violet.copy(.42f) else Border),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LockGlyph(Modifier.size(20.dp), if (field.required) Violet else Muted)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(field.key, color = MutedDeep, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    color = if (field.required) Violet.copy(.18f) else PanelStrong,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (field.required) Violet.copy(.40f) else Border),
                ) {
                    Text(
                        text = stringResource(if (field.required) R.string.runtime_configuration_required_section else R.string.runtime_configuration_item_optional),
                        color = if (field.required) Color(0xFFC8A7FF) else Muted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (field.description.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(field.description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        if (field.configured) stringResource(R.string.brand_configuration_keep_existing) else stringResource(R.string.brand_configuration_enter_value),
                    )
                },
                visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

private fun humanizeConfigurationKey(key: String): String = key
    .trim()
    .lowercase()
    .split('_', '-', '.')
    .filter { it.isNotBlank() }
    .joinToString(" ") { token -> token.replaceFirstChar { c -> c.uppercase() } }
    .ifBlank { key }

@Composable
private fun RunProjectScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { NormalTopBar(stringResource(R.string.brand_run_title), onBack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ProjectIdentityCard(state) }
            item {
                RunOrb(
                    running = state.runtimeState == RuntimeState.RUNNING,
                    resultAvailable = state.openEnabled,
                )
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.runtimeState == RuntimeState.STARTING) {
                            stringResource(R.string.brand_run_starting)
                        } else {
                            stringResource(R.string.brand_run_running)
                        },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(state.projectName, color = Muted, fontSize = 11.sp)
                }
            }
            item {
                RunPhaseList(
                    starting = state.runtimeState == RuntimeState.STARTING,
                    resultAvailable = state.openEnabled,
                )
            }
            item {
                GradientPrimaryButton(
                    stringResource(R.string.brand_action_stop),
                    onStop,
                    enabled = !state.busy || state.runtimeState == RuntimeState.RUNNING,
                )
            }
            item {
                ProjectBaselineUtilities(
                    state = state,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.brand_run_background_hint),
                    color = MutedDeep,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun RunOrb(
    running: Boolean,
    resultAvailable: Boolean,
) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val transition = rememberInfiniteTransition(label = "run-orb")

    val angle by if (motion && !running) {
        transition.animateFloat(
            initialValue = -90f,
            targetValue = 270f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
            ),
            label = "run-start-angle",
        )
    } else {
        remember { mutableStateOf(-90f) }
    }

    val breath by if (motion && running) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1080),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "run-breath",
        )
    } else {
        remember { mutableStateOf(.5f) }
    }

    val flow by if (motion && running && resultAvailable) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
            ),
            label = "run-inner-flow",
        )
    } else {
        remember { mutableStateOf(.5f) }
    }

    Box(
        modifier = Modifier
            .size(224.dp)
            .graphicsLayer {
                if (running && motion) {
                    val scale = .985f + .015f * breath
                    scaleX = scale
                    scaleY = scale
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val baseStroke = 10.dp.toPx()
            val pulse = breath.coerceIn(0f, 1f)

            drawCircle(
                color = Color(0xFF1D356B),
                radius = (size.minDimension - baseStroke) / 2f,
                center = center,
                style = Stroke(baseStroke),
            )

            when {
                running && resultAvailable -> {
                    // Once the existing R48-D2 Open fact is true, the launch/result surface is
                    // ready. Close the ring completely rather than leaving a loading gap.
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(Cyan, Blue, Violet, Cyan),
                        ),
                        radius = (size.minDimension - baseStroke) / 2f,
                        center = center,
                        style = Stroke(
                            width = baseStroke + 2.dp.toPx() * pulse,
                            cap = StrokeCap.Round,
                        ),
                        alpha = .76f + .20f * pulse,
                    )

                    // Three restrained "thinking/data" lines. Their control points move slightly
                    // and a tiny highlight travels along each curve; no dense wireframe clutter.
                    val lineColors = listOf(
                        Cyan.copy(alpha = .34f),
                        ElectricBlue.copy(alpha = .30f),
                        Violet.copy(alpha = .27f),
                    )
                    val top = size.height * .39f
                    val gap = size.height * .105f

                    fun pointOnCubic(
                        p0: Offset,
                        p1: Offset,
                        p2: Offset,
                        p3: Offset,
                        t: Float,
                    ): Offset {
                        val u = 1f - t
                        return Offset(
                            x = u * u * u * p0.x +
                                3f * u * u * t * p1.x +
                                3f * u * t * t * p2.x +
                                t * t * t * p3.x,
                            y = u * u * u * p0.y +
                                3f * u * u * t * p1.y +
                                3f * u * t * t * p2.y +
                                t * t * t * p3.y,
                        )
                    }

                    repeat(3) { index ->
                        val y = top + gap * index
                        val direction = if (index % 2 == 0) 1f else -1f
                        val shift = (flow - .5f) * 10.dp.toPx() * direction
                        val p0 = Offset(size.width * .25f, y)
                        val p1 = Offset(
                            size.width * .39f,
                            y - 13.dp.toPx() * direction + shift,
                        )
                        val p2 = Offset(
                            size.width * .61f,
                            y + 13.dp.toPx() * direction - shift,
                        )
                        val p3 = Offset(size.width * .75f, y)
                        val path = Path().apply {
                            moveTo(p0.x, p0.y)
                            cubicTo(
                                p1.x,
                                p1.y,
                                p2.x,
                                p2.y,
                                p3.x,
                                p3.y,
                            )
                        }
                        drawPath(
                            path = path,
                            color = lineColors[index],
                            style = Stroke(
                                width = 1.15.dp.toPx(),
                                cap = StrokeCap.Round,
                            ),
                        )

                        val dotT = (flow + index * .31f) % 1f
                        val dot = pointOnCubic(p0, p1, p2, p3, dotT)
                        drawCircle(
                            color = lineColors[index].copy(alpha = .80f),
                            radius = 1.8.dp.toPx(),
                            center = dot,
                        )
                    }
                }

                running -> {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Cyan, Blue, Violet, Cyan),
                        ),
                        startAngle = -90f,
                        sweepAngle = 332f,
                        useCenter = false,
                        topLeft = Offset(baseStroke / 2, baseStroke / 2),
                        size = Size(
                            size.width - baseStroke,
                            size.height - baseStroke,
                        ),
                        style = Stroke(
                            width = baseStroke + 2.dp.toPx() * pulse,
                            cap = StrokeCap.Round,
                        ),
                        alpha = .74f + .22f * pulse,
                    )
                }

                else -> {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Cyan, Blue, Violet, Cyan),
                        ),
                        startAngle = angle,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(baseStroke / 2, baseStroke / 2),
                        size = Size(
                            size.width - baseStroke,
                            size.height - baseStroke,
                        ),
                        style = Stroke(baseStroke, cap = StrokeCap.Round),
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (running) {
                    stringResource(R.string.brand_run_live)
                } else {
                    stringResource(R.string.brand_run_starting_short)
                },
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.brand_run_task_active),
                color = Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun RunPhaseList(
    starting: Boolean,
    resultAvailable: Boolean,
) {
    val executionCompleted = resultAvailable
    val executionActive = !starting && !resultAvailable

    GlowCard {
        RunPhaseRow(
            label = stringResource(R.string.brand_run_phase_start),
            completed = !starting,
            active = starting,
        )
        Spacer(Modifier.height(12.dp))
        RunPhaseRow(
            label = stringResource(R.string.brand_run_phase_execute),
            completed = executionCompleted,
            active = executionActive,
        )
        Spacer(Modifier.height(12.dp))
        RunPhaseRow(
            label = stringResource(R.string.brand_run_phase_result),
            completed = resultAvailable,
            active = false,
            completedText = if (resultAvailable) {
                stringResource(R.string.brand_state_available)
            } else {
                null
            },
        )
    }
}

@Composable
private fun RunPhaseRow(
    label: String,
    completed: Boolean,
    active: Boolean,
    completedText: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = when {
                completed -> Green
                active -> Blue
                else -> PanelStrong
            },
            border = BorderStroke(
                1.dp,
                if (active) Cyan.copy(.6f) else Border,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (completed) {
                    CheckGlyph(Modifier.size(12.dp), Color.White)
                } else if (active) {
                    DotPulse(Modifier.size(10.dp))
                } else {
                    Text("·", color = Muted)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (completed || active) TextPrimary else MutedDeep,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                completed -> completedText ?: stringResource(R.string.brand_state_completed)
                active -> stringResource(R.string.brand_state_running)
                else -> stringResource(R.string.brand_state_waiting)
            },
            color = when {
                completed -> Green
                active -> Cyan
                else -> MutedDeep
            },
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun DotPulse(modifier: Modifier) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by if (motion) {
        transition.animateFloat(
            .35f,
            1f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse-alpha",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(modifier.background(Cyan.copy(alpha = alpha), CircleShape))
}

@Composable
private fun ResultProjectScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { NormalTopBar(stringResource(R.string.brand_result_title), onBack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ProjectIdentityCard(state) }
            item { ResultCelebrationIcon() }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.brand_result_completed),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.brand_result_ready_detail),
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            item {
                GlowCard {
                    ResultRow(
                        title = stringResource(R.string.brand_result_primary_item),
                        subtitle = state.projectName,
                        tone = Blue,
                    )
                    Spacer(Modifier.height(10.dp))
                    ResultRow(
                        title = stringResource(R.string.brand_result_status_item),
                        subtitle = state.statusLabel,
                        tone = Green,
                    )
                }
            }
            item {
                GradientPrimaryButton(
                    stringResource(R.string.brand_result_open),
                    onOpen,
                    enabled = state.openEnabled && !state.busy,
                )
            }
            item {
                OutlinedButton(
                    onClick = onRun,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, Border),
                ) {
                    Text(stringResource(R.string.brand_result_run_again), color = TextPrimary)
                }
            }
            item {
                ProjectBaselineUtilities(
                    state = state,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                    includeOpen = false,
                )
            }
        }
    }
}

@Composable
private fun ResultCelebrationIcon() {
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val points = listOf(
                Offset(size.width * .16f, size.height * .30f),
                Offset(size.width * .82f, size.height * .24f),
                Offset(size.width * .12f, size.height * .68f),
                Offset(size.width * .86f, size.height * .72f),
                Offset(size.width * .50f, size.height * .08f),
            )
            val colors = listOf(Cyan, Violet, Green, Magenta, Amber)
            points.forEachIndexed { i, point ->
                drawCircle(colors[i].copy(alpha = .9f), radius = 4.dp.toPx(), center = point)
            }
        }
        Surface(
            modifier = Modifier.size(82.dp),
            color = Blue.copy(.18f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Cyan.copy(.50f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                DocumentGlyph(Modifier.size(42.dp), ElectricBlue)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).size(34.dp),
            shape = CircleShape,
            color = Green,
        ) {
            Box(contentAlignment = Alignment.Center) { CheckGlyph(Modifier.size(18.dp), Color.White) }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(tone.copy(.18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            DocumentGlyph(Modifier.size(20.dp), tone)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DownloadGlyph(Modifier.size(18.dp), Muted)
    }
}

@Composable
private fun RecoveryProjectScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onRun: () -> Unit,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { NormalTopBar(state.projectName, onBack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ProjectIdentityCard(state) }
            item {
                GlowCard {
                    Surface(
                        Modifier.size(48.dp),
                        shape = CircleShape,
                        color = Red.copy(.17f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            WarningGlyph(Modifier.size(25.dp), Red)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.brand_recovery_title),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.message
                            ?.takeUnless { it.contains("SIFTALPHA_") }
                            ?: stringResource(R.string.brand_recovery_detail),
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            item {
                GradientPrimaryButton(
                    label = stringResource(
                        if (state.runtimeState == RuntimeState.ENVIRONMENT_ERROR) {
                            R.string.brand_action_prepare
                        } else {
                            R.string.brand_recovery_retry
                        },
                    ),
                    onClick = if (state.runtimeState == RuntimeState.ENVIRONMENT_ERROR) {
                        onPrepare
                    } else {
                        onRun
                    },
                    enabled = !state.busy,
                )
            }
            item {
                ProjectBaselineUtilities(
                    state = state,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )
            }
        }
    }
}

@Composable
private fun ReadyProjectScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onOpenDeveloper: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { NormalTopBar(state.projectName, onBack) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ProjectIdentityCard(state) }

            if (state.primaryAction == NormalProjectPrimaryActionPolicy.Action.RUN) {
                item {
                    GlowCard(prominent = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                Modifier.size(46.dp),
                                shape = CircleShape,
                                color = Green.copy(.18f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    CheckGlyph(Modifier.size(24.dp), Green)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.brand_ready_title),
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    stringResource(R.string.brand_ready_detail),
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
                item {
                    GradientPrimaryButton(
                        stringResource(R.string.brand_action_run),
                        onRun,
                        enabled = !state.busy,
                    )
                }
            } else {
                item {
                    GlowCard {
                        Text(
                            state.statusLabel,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.message ?: stringResource(R.string.normal_project_waiting_action),
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            item {
                ProjectBaselineUtilities(
                    state = state,
                    onOpen = onOpen,
                    onRefresh = onRefresh,
                    onSelectRuntime = onSelectRuntime,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onRecheckExternal = onRecheckExternal,
                )
            }

            if (state.developerModeEnabled) {
                item {
                    TextButton(
                        onClick = onOpenDeveloper,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.normal_project_open_developer), color = Muted)
                    }
                }
            }
        }
    }
}

// --- Small vector glyphs// --- Small vector glyphs ----------------------------------------------------

@Composable
private fun FolderGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * .10f, size.height * .28f)
            lineTo(size.width * .42f, size.height * .28f)
            lineTo(size.width * .52f, size.height * .40f)
            lineTo(size.width * .90f, size.height * .40f)
            lineTo(size.width * .90f, size.height * .80f)
            lineTo(size.width * .10f, size.height * .80f)
            close()
        }
        drawPath(path, color = color.copy(alpha = .22f))
        drawPath(path, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun PlusGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * .5f, size.height * .22f), Offset(size.width * .5f, size.height * .78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .22f, size.height * .5f), Offset(size.width * .78f, size.height * .5f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun HomeGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        val p = Path().apply {
            moveTo(size.width * .14f, size.height * .46f)
            lineTo(size.width * .50f, size.height * .16f)
            lineTo(size.width * .86f, size.height * .46f)
            lineTo(size.width * .78f, size.height * .46f)
            lineTo(size.width * .78f, size.height * .84f)
            lineTo(size.width * .58f, size.height * .84f)
            lineTo(size.width * .58f, size.height * .62f)
            lineTo(size.width * .42f, size.height * .62f)
            lineTo(size.width * .42f, size.height * .84f)
            lineTo(size.width * .22f, size.height * .84f)
            lineTo(size.width * .22f, size.height * .46f)
        }
        drawPath(p, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun ToolsGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        drawLine(color, Offset(size.width * .25f, size.height * .18f), Offset(size.width * .78f, size.height * .72f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .72f, size.height * .22f), Offset(size.width * .24f, size.height * .78f), stroke, StrokeCap.Round)
        drawCircle(color, radius = size.minDimension * .12f, center = Offset(size.width * .22f, size.height * .22f), style = Stroke(stroke))
        drawCircle(color, radius = size.minDimension * .12f, center = Offset(size.width * .78f, size.height * .78f), style = Stroke(stroke))
    }
}

@Composable
private fun PersonGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        drawCircle(color, radius = size.minDimension * .18f, center = Offset(size.width * .5f, size.height * .33f), style = Stroke(stroke))
        drawArc(color, 200f, 140f, false, Offset(size.width * .18f, size.height * .43f), Size(size.width * .64f, size.height * .42f), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun BackGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * .70f, size.height * .18f), Offset(size.width * .34f, size.height * .50f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .34f, size.height * .50f), Offset(size.width * .70f, size.height * .82f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ChevronGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(size.width * .34f, size.height * .22f), Offset(size.width * .66f, size.height * .50f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .66f, size.height * .50f), Offset(size.width * .34f, size.height * .78f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun LocationGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        drawCircle(color, radius = size.minDimension * .13f, center = Offset(size.width * .5f, size.height * .38f), style = Stroke(stroke))
        val p = Path().apply {
            moveTo(size.width * .50f, size.height * .88f)
            cubicTo(size.width * .28f, size.height * .63f, size.width * .18f, size.height * .45f, size.width * .24f, size.height * .29f)
            cubicTo(size.width * .31f, size.height * .10f, size.width * .69f, size.height * .10f, size.width * .76f, size.height * .29f)
            cubicTo(size.width * .82f, size.height * .45f, size.width * .72f, size.height * .63f, size.width * .50f, size.height * .88f)
        }
        drawPath(p, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun CheckGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * .18f, size.height * .52f), Offset(size.width * .42f, size.height * .74f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .42f, size.height * .74f), Offset(size.width * .84f, size.height * .28f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun DocumentGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        val p = Path().apply {
            moveTo(size.width * .22f, size.height * .10f)
            lineTo(size.width * .62f, size.height * .10f)
            lineTo(size.width * .80f, size.height * .28f)
            lineTo(size.width * .80f, size.height * .90f)
            lineTo(size.width * .22f, size.height * .90f)
            close()
        }
        drawPath(p, color = color.copy(.16f))
        drawPath(p, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color.copy(.8f), Offset(size.width * .34f, size.height * .48f), Offset(size.width * .68f, size.height * .48f), stroke, StrokeCap.Round)
        drawLine(color.copy(.8f), Offset(size.width * .34f, size.height * .62f), Offset(size.width * .68f, size.height * .62f), stroke, StrokeCap.Round)
        drawLine(color.copy(.8f), Offset(size.width * .34f, size.height * .76f), Offset(size.width * .58f, size.height * .76f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun LockGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(color.copy(.14f), Offset(size.width * .20f, size.height * .40f), Size(size.width * .60f, size.height * .45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        drawRoundRect(color, Offset(size.width * .20f, size.height * .40f), Size(size.width * .60f, size.height * .45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(stroke))
        drawArc(color, 180f, 180f, false, Offset(size.width * .30f, size.height * .10f), Size(size.width * .40f, size.height * .52f), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun WarningGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        drawCircle(color, radius = size.minDimension * .38f, center = center, style = Stroke(stroke))
        drawLine(color, Offset(size.width * .5f, size.height * .28f), Offset(size.width * .5f, size.height * .58f), stroke, StrokeCap.Round)
        drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(size.width * .5f, size.height * .72f))
    }
}

@Composable
private fun DownloadGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        drawLine(color, Offset(size.width * .50f, size.height * .16f), Offset(size.width * .50f, size.height * .62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .32f, size.height * .46f), Offset(size.width * .50f, size.height * .64f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .68f, size.height * .46f), Offset(size.width * .50f, size.height * .64f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .24f, size.height * .82f), Offset(size.width * .76f, size.height * .82f), stroke, StrokeCap.Round)
    }
}
