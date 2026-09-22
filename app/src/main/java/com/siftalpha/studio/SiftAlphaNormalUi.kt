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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
            // Four continuous scenes from the approved concept:
            // 01 edges -> 02 convergence -> 03 fragment formation -> 04 real logo.
            timeline.animateTo(1f, tween(4400, easing = LinearEasing))
            delay(260)
        } else {
            // Reduced-motion presents the completed real logo without auto-running decorative motion.
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
            exit = fadeOut(tween(if (motion) 320 else 120)),
        ) {
            BrandLaunchSequence(
                progress = timeline.value,
                motion = motion,
            )
        }
    }
}

private val BrandCodeLexicon = listOf(
    "101", "0101", "</>", "{ }", "[ ]", "AI", "Py", "JS",
    "0x", "λ", "Σ", "R", "def", "fn", "API", "01",
    "&&", "::", "{}", "<>", "ML", "X", "run", "data",
)

private data class BrandCodeParticle(
    val text: String,
    val upper: Boolean,
    val lane: Float,
    val delay: Float,
)

private val BrandCodeParticles = List(72) { index ->
    val upper = index < 36
    val local = if (upper) index else index - 36
    BrandCodeParticle(
        text = BrandCodeLexicon[index % BrandCodeLexicon.size],
        upper = upper,
        lane = local / 35f,
        delay = (local % 12) * .010f + if (upper) 0f else .021f,
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
    val fadeIn = if (inEnd <= start) {
        1f
    } else {
        smoothStep(((progress - start) / (inEnd - start)).coerceIn(0f, 1f))
    }
    val fadeOut = if (end <= outStart) {
        1f
    } else {
        1f -
            smoothStep(
                ((progress - outStart) / (end - outStart)).coerceIn(0f, 1f),
            )
    }
    return fadeIn * fadeOut
}

private fun finalSceneAlpha(progress: Float): Float =
    smoothStep(((progress - .72f) / .16f).coerceIn(0f, 1f))

@Composable
private fun BrandLaunchSequence(
    progress: Float,
    motion: Boolean,
) {
    val p = progress.coerceIn(0f, 1f)
    val scene1 = sceneAlpha(p, 0f, .045f, .20f, .31f)
    val scene2 = sceneAlpha(p, .16f, .27f, .47f, .59f)
    val scene3 = sceneAlpha(p, .45f, .56f, .73f, .84f)
    val scene4 = finalSceneAlpha(p)

    val flowTransition = rememberInfiniteTransition(label = "launch-flow")
    val upperClock by if (motion) {
        flowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2050, easing = LinearEasing),
            ),
            label = "launch-upper-clock",
        )
    } else {
        remember { mutableStateOf(.56f) }
    }
    val lowerClock by if (motion) {
        flowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2310, easing = LinearEasing),
            ),
            label = "launch-lower-clock",
        )
    } else {
        remember { mutableStateOf(.49f) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020A1B),
                        Color(0xFF03102A),
                        Ink,
                        Color(0xFF010615),
                    ),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w * .50f, h * .48f)

            // Subtle deep-space glow shared by all four scenes.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF123D8C).copy(alpha = .22f),
                        Color.Transparent,
                    ),
                    center = Offset(w * .78f, h * .18f),
                    radius = size.maxDimension * .58f,
                ),
                radius = size.maxDimension * .56f,
                center = Offset(w * .78f, h * .18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF541A8B).copy(alpha = .17f),
                        Color.Transparent,
                    ),
                    center = Offset(w * .15f, h * .80f),
                    radius = size.maxDimension * .46f,
                ),
                radius = size.maxDimension * .44f,
                center = Offset(w * .15f, h * .80f),
            )

            fun edgePoint(
                upper: Boolean,
                lane: Float,
                t: Float,
                clock: Float,
            ): Offset {
                val eased = smoothStep(t)
                val laneSigned = lane * 2f - 1f
                val edgeTaper = (1f - eased) * (1f - eased)
                val end = if (upper) {
                    Offset(w * .57f, h * .43f)
                } else {
                    Offset(w * .43f, h * .57f)
                }

                val baseX = if (upper) {
                    cubicBezier(w * 1.12f, w * .94f, w * .76f, end.x, eased)
                } else {
                    cubicBezier(w * -.12f, w * .06f, w * .24f, end.x, eased)
                }
                val baseY = if (upper) {
                    cubicBezier(
                        h * (.14f + .19f * lane),
                        h * (.17f + .15f * lane),
                        h * (.31f + .10f * lane),
                        end.y,
                        eased,
                    )
                } else {
                    cubicBezier(
                        h * (.67f + .19f * lane),
                        h * (.74f - .12f * lane),
                        h * (.67f - .10f * lane),
                        end.y,
                        eased,
                    )
                }

                val angle =
                    clock * 2f * PI.toFloat() +
                        eased * 1.15f * PI.toFloat() +
                        laneSigned * 1.2f

                return Offset(
                    x = baseX +
                        kotlin.math.cos(angle) * w * .020f * edgeTaper +
                        laneSigned * w * .028f * edgeTaper,
                    y = baseY +
                        sin(angle) * h * .013f * edgeTaper +
                        laneSigned * h * .031f * edgeTaper,
                )
            }

            fun convergencePoint(
                upper: Boolean,
                lane: Float,
                t: Float,
                clock: Float,
            ): Offset {
                val eased = smoothStep(t)
                val laneSigned = lane * 2f - 1f
                val taper = (1f - eased) * (1f - eased)
                val target = if (upper) {
                    Offset(center.x + 4.dp.toPx(), center.y - 3.dp.toPx())
                } else {
                    Offset(center.x - 4.dp.toPx(), center.y + 3.dp.toPx())
                }

                val baseX = if (upper) {
                    cubicBezier(w * 1.08f, w * .83f, w * .35f, target.x, eased)
                } else {
                    cubicBezier(w * -.08f, w * .17f, w * .65f, target.x, eased)
                }
                val baseY = if (upper) {
                    cubicBezier(
                        h * (.16f + .15f * lane),
                        h * (.19f + .11f * lane),
                        h * (.40f - .04f * lane),
                        target.y,
                        eased,
                    )
                } else {
                    cubicBezier(
                        h * (.69f + .15f * lane),
                        h * (.73f - .11f * lane),
                        h * (.56f + .04f * lane),
                        target.y,
                        eased,
                    )
                }

                val direction = if (upper) 1f else -1f
                val angle =
                    direction *
                        (
                            eased * 1.78f * PI.toFloat() +
                                clock * 2f * PI.toFloat()
                            ) +
                        laneSigned * 1.25f
                val radius =
                    w * (.036f + .014f * kotlin.math.abs(laneSigned)) * taper

                return Offset(
                    x = baseX +
                        kotlin.math.cos(angle) * radius +
                        laneSigned * w * .022f * taper,
                    y = baseY +
                        sin(angle) * radius * .78f +
                        laneSigned * h * .027f * taper,
                )
            }

            fun drawBundle(
                alpha: Float,
                convergence: Boolean,
            ) {
                if (alpha <= .005f) return
                val strands = if (convergence) 26 else 24
                val samples = if (convergence) 24 else 20

                repeat(2) { side ->
                    val upper = side == 0
                    val clock = if (upper) upperClock else lowerClock
                    repeat(strands) { strand ->
                        val lane = strand / (strands - 1f)
                        val highlightPhase =
                            (
                                clock +
                                    strand * if (upper) .041f else .046f
                                ) % 1f

                        fun point(t: Float): Offset =
                            if (convergence) {
                                convergencePoint(upper, lane, t, clock)
                            } else {
                                edgePoint(upper, lane, t, clock)
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
                                1f - (wrapped / .17f).coerceIn(0f, 1f)
                            val edgeFade =
                                smoothStep((t / .07f).coerceIn(0f, 1f)) *
                                    (
                                        1f -
                                            smoothStep(
                                                ((t - .96f) / .04f)
                                                    .coerceIn(0f, 1f),
                                            )
                                        )
                            val color = if (upper) {
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
                                            (.08f + .34f * pulse),
                                ),
                                start = previous,
                                end = current,
                                strokeWidth =
                                    (.52f + (strand % 5) * .12f).dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            previous = current
                        }
                    }
                }
            }

            // 01: broad streams remain separated around the center.
            drawBundle(alpha = scene1, convergence = false)
            // 02: the same visual language curls into a luminous S-shaped convergence.
            drawBundle(alpha = scene2, convergence = true)

            // 03: deterministic code/data fragments assemble into a luminous S silhouette.
            if (scene3 > .005f) {
                val assembly =
                    smoothStep(((p - .48f) / .26f).coerceIn(0f, 1f))
                val fragmentCount = 184
                repeat(fragmentCount) { index ->
                    val u = index / (fragmentCount - 1f)
                    val segment = when {
                        u < .34f -> 0
                        u < .68f -> 1
                        else -> 2
                    }
                    val local = when (segment) {
                        0 -> u / .34f
                        1 -> (u - .34f) / .34f
                        else -> (u - .68f) / .32f
                    }

                    val targetX = when (segment) {
                        0 -> cubicBezier(
                            w * .66f,
                            w * .47f,
                            w * .34f,
                            w * .42f,
                            local,
                        )
                        1 -> cubicBezier(
                            w * .42f,
                            w * .36f,
                            w * .68f,
                            w * .61f,
                            local,
                        )
                        else -> cubicBezier(
                            w * .61f,
                            w * .72f,
                            w * .52f,
                            w * .35f,
                            local,
                        )
                    }
                    val targetY = when (segment) {
                        0 -> cubicBezier(
                            h * .36f,
                            h * .31f,
                            h * .39f,
                            h * .45f,
                            local,
                        )
                        1 -> cubicBezier(
                            h * .45f,
                            h * .49f,
                            h * .51f,
                            h * .56f,
                            local,
                        )
                        else -> cubicBezier(
                            h * .56f,
                            h * .61f,
                            h * .68f,
                            h * .66f,
                            local,
                        )
                    }

                    val scatterAngle = index * 2.399963f
                    val scatterRadius =
                        size.minDimension *
                            (.08f + (index % 13) / 13f * .27f)
                    val scatterX =
                        center.x + kotlin.math.cos(scatterAngle) * scatterRadius
                    val scatterY =
                        center.y + sin(scatterAngle) * scatterRadius

                    val thickness =
                        size.minDimension *
                            (.012f + (index % 7) * .0024f)
                    val normalX = kotlin.math.cos(index * 1.73f) * thickness
                    val normalY = sin(index * 1.91f) * thickness

                    val x =
                        scatterX * (1f - assembly) +
                            (targetX + normalX) * assembly
                    val y =
                        scatterY * (1f - assembly) +
                            (targetY + normalY) * assembly

                    val flicker =
                        .55f +
                            .45f *
                                kotlin.math.abs(
                                    sin(
                                        upperClock * 2f * PI.toFloat() +
                                            index * .37f,
                                    ),
                                )
                    val color = when (index % 5) {
                        0 -> Color.White
                        1 -> Cyan
                        2 -> ElectricBlue
                        3 -> Violet
                        else -> Blue
                    }

                    if (index % 3 == 0) {
                        drawRoundRect(
                            color = color.copy(
                                alpha = scene3 * (.30f + .45f * flicker),
                            ),
                            topLeft = Offset(x, y),
                            size = Size(
                                width = (2.6f + (index % 4) * .9f).dp.toPx(),
                                height = (2.6f + (index % 3) * .8f).dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(
                                1.2.dp.toPx(),
                                1.2.dp.toPx(),
                            ),
                        )
                    } else {
                        drawCircle(
                            color = color.copy(
                                alpha = scene3 * (.24f + .48f * flicker),
                            ),
                            radius = (.9f + (index % 4) * .38f).dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = .22f * scene3),
                            Violet.copy(alpha = .13f * scene3),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * .34f,
                    ),
                    radius = size.minDimension * .30f,
                    center = center,
                )
            }

            // 04: final halo and lower-stage light under the real logo.
            if (scene4 > .005f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = .28f * scene4),
                            Blue.copy(alpha = .18f * scene4),
                            Violet.copy(alpha = .12f * scene4),
                            Color.Transparent,
                        ),
                        center = Offset(w * .5f, h * .49f),
                        radius = size.minDimension * .44f,
                    ),
                    radius = size.minDimension * .40f,
                    center = Offset(w * .5f, h * .49f),
                )
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = .46f * scene4),
                            Blue.copy(alpha = .18f * scene4),
                            Color.Transparent,
                        ),
                        center = Offset(w * .5f, h * .735f),
                        radius = w * .30f,
                    ),
                    topLeft = Offset(w * .23f, h * .715f),
                    size = Size(w * .54f, h * .045f),
                )
            }
        }

        // Code glyphs travel with scenes 01/02, then peel into the scene-03 S formation.
        if (motion && p < .86f) {
            val convergeBlend =
                smoothStep(((p - .17f) / .34f).coerceIn(0f, 1f))
            val formationBlend =
                smoothStep(((p - .49f) / .25f).coerceIn(0f, 1f))
            val streamAlpha =
                kotlin.math.max(scene1, scene2) *
                    (1f - smoothStep(((p - .58f) / .17f).coerceIn(0f, 1f)))

            BrandCodeParticles.forEachIndexed { index, particle ->
                val clock = if (particle.upper) upperClock else lowerClock
                val rawPhase =
                    (
                        clock +
                            index * .057f +
                            particle.lane * .129f +
                            particle.delay
                        ) % 1f
                val t = smoothStep(rawPhase)
                val life = flowEnvelope(rawPhase)
                val lane = particle.lane
                val laneSigned = lane * 2f - 1f

                val edgeX = if (particle.upper) {
                    cubicBezier(1.08f, .92f, .73f, .57f, t)
                } else {
                    cubicBezier(-.08f, .08f, .27f, .43f, t)
                }
                val edgeY = if (particle.upper) {
                    cubicBezier(
                        .14f + .19f * lane,
                        .17f + .15f * lane,
                        .31f + .10f * lane,
                        .43f,
                        t,
                    )
                } else {
                    cubicBezier(
                        .67f + .19f * lane,
                        .74f - .12f * lane,
                        .67f - .10f * lane,
                        .57f,
                        t,
                    )
                }

                val convX = if (particle.upper) {
                    cubicBezier(1.06f, .82f, .37f, .505f, t)
                } else {
                    cubicBezier(-.06f, .18f, .63f, .495f, t)
                }
                val convY = if (particle.upper) {
                    cubicBezier(
                        .16f + .15f * lane,
                        .19f + .11f * lane,
                        .40f - .04f * lane,
                        .477f,
                        t,
                    )
                } else {
                    cubicBezier(
                        .69f + .15f * lane,
                        .73f - .11f * lane,
                        .56f + .04f * lane,
                        .483f,
                        t,
                    )
                }

                val streamX =
                    edgeX * (1f - convergeBlend) + convX * convergeBlend
                val streamY =
                    edgeY * (1f - convergeBlend) + convY * convergeBlend

                val formationU = index / (BrandCodeParticles.size - 1f)
                val formationSegment = when {
                    formationU < .34f -> 0
                    formationU < .68f -> 1
                    else -> 2
                }
                val formationLocal = when (formationSegment) {
                    0 -> formationU / .34f
                    1 -> (formationU - .34f) / .34f
                    else -> (formationU - .68f) / .32f
                }
                val formX = when (formationSegment) {
                    0 -> cubicBezier(.66f, .47f, .34f, .42f, formationLocal)
                    1 -> cubicBezier(.42f, .36f, .68f, .61f, formationLocal)
                    else -> cubicBezier(.61f, .72f, .52f, .35f, formationLocal)
                } +
                    kotlin.math.cos(index * 1.61f) *
                        (.018f + (index % 5) * .002f)
                val formY = when (formationSegment) {
                    0 -> cubicBezier(.36f, .31f, .39f, .45f, formationLocal)
                    1 -> cubicBezier(.45f, .49f, .51f, .56f, formationLocal)
                    else -> cubicBezier(.56f, .61f, .68f, .66f, formationLocal)
                } +
                    sin(index * 1.91f) *
                        (.012f + (index % 4) * .002f)

                val x =
                    streamX * (1f - formationBlend) +
                        formX * formationBlend
                val y =
                    streamY * (1f - formationBlend) +
                        formY * formationBlend

                val streamOrbit =
                    (1f - t) *
                        (
                            kotlin.math.cos(
                                rawPhase * 2f * PI.toFloat() + index * .51f,
                            ) *
                                .010f +
                                laneSigned * .006f
                            )
                val displayX = x + streamOrbit * (1f - formationBlend)

                Text(
                    text = particle.text,
                    color = when (index % 4) {
                        0 -> Color.White
                        1 -> Cyan
                        2 -> ElectricBlue
                        else -> Violet
                    },
                    fontSize = when {
                        particle.text.length >= 4 -> 7.sp
                        particle.text.length == 3 -> 8.sp
                        else -> 10.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .offset(
                            x = maxWidth * displayX - 15.dp,
                            y = maxHeight * y - 7.dp,
                        )
                        .alpha(
                            (
                                streamAlpha * life * (1f - formationBlend) +
                                    scene3 * formationBlend * .88f
                                ).coerceIn(0f, 1f),
                        )
                        .graphicsLayer {
                            val scale =
                                1f - .14f * formationBlend
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        // Scene 03 gets a faint ribbon silhouette so fragments read as an S before the real
        // app logo appears. It is transitional only; the final mark remains the actual asset.
        if (scene3 > .01f) {
            SiftRibbonMark(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 6.dp)
                    .size(236.dp)
                    .alpha(scene3 * .22f),
                reveal = smoothStep(((p - .52f) / .20f).coerceIn(0f, 1f)),
            )
        }

        // Scene 04 uses only the actual application logo asset.
        if (scene4 > .005f) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 18.dp)
                    .alpha(scene4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(244.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Cyan.copy(alpha = .22f * scene4),
                            radius = size.minDimension * .50f,
                            center = center,
                            style = Stroke(1.3.dp.toPx()),
                        )
                        drawCircle(
                            color = Violet.copy(alpha = .13f * scene4),
                            radius = size.minDimension * .44f,
                            center = center,
                            style = Stroke(.8.dp.toPx()),
                        )
                    }
                    OriginalLogoMark(
                        modifier = Modifier
                            .size(204.dp)
                            .graphicsLayer {
                                val scale =
                                    .90f + .10f * scene4
                                scaleX = scale
                                scaleY = scale
                            },
                        alpha = scene4,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = buildAnnotatedString {
                        append("SiftAlpha ")
                        pushStyle(SpanStyle(color = Cyan))
                        append("X")
                        pop()
                    },
                    color = TextPrimary,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(15.dp))
                Text(
                    text = "INTELLIGENCE\nIN MOTION",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 4.6.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        BrandLaunchChrome(
            scene1 = scene1,
            scene2 = scene2,
            scene3 = scene3,
            scene4 = scene4,
        )
    }
}

@Composable
private fun BrandLaunchChrome(
    scene1: Float,
    scene2: Float,
    scene3: Float,
    scene4: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 18.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    append("SiftAlpha ")
                    pushStyle(SpanStyle(color = Cyan))
                    append("X")
                    pop()
                },
                color = TextPrimary,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "FROM CODE TO POSSIBILITY",
                color = Color(0xFFA6A8E8),
                fontSize = 10.sp,
                letterSpacing = 4.3.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Intelligence Converges. A Smarter Tomorrow.",
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = .2.sp,
            )
        }

        BrandPhaseHeader(
            number = "01",
            title = stringResource(R.string.brand_launch_phase_1_title),
            english = "FROM THE EDGES",
            alpha = scene1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 122.dp),
        )
        BrandPhaseHeader(
            number = "02",
            title = stringResource(R.string.brand_launch_phase_2_title),
            english = "FLOW TOGETHER",
            alpha = scene2,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 122.dp),
        )
        BrandPhaseHeader(
            number = "03",
            title = stringResource(R.string.brand_launch_phase_3_title),
            english = "SHAPE THE FUTURE",
            alpha = scene3,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 122.dp),
        )
        BrandPhaseHeader(
            number = "04",
            title = stringResource(R.string.brand_launch_phase_4_title),
            english = "READY FOR WHAT'S NEXT",
            alpha = scene4,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 122.dp),
        )

        BrandPhaseCaption(
            main = stringResource(R.string.brand_launch_phase_1_caption),
            english = "CODE FLOWS IN FROM BOTH SIDES",
            alpha = scene1,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        BrandPhaseCaption(
            main = stringResource(R.string.brand_launch_phase_2_caption),
            english = "FRAGMENTS TRAVEL AND CONVERGE",
            alpha = scene2,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        BrandPhaseCaption(
            main = stringResource(R.string.brand_launch_phase_3_caption),
            english = "FRAGMENTS ASSEMBLE INTO A HIGHER INTELLIGENCE",
            alpha = scene3,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        BrandPhaseCaption(
            main = stringResource(R.string.brand_launch_phase_4_caption),
            english = "THE LOGO EMERGES. A BRIGHTER TOMORROW BEGINS",
            alpha = scene4,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BrandPhaseHeader(
    number: String,
    title: String,
    english: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number,
            color = Blue,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.size(width = 1.dp, height = 33.dp),
            color = Color(0xFF7789C2).copy(alpha = .65f),
        ) {}
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "·",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = english,
            color = Color(0xFFA6A8E8),
            fontSize = 9.sp,
            letterSpacing = 2.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrandPhaseCaption(
    main: String,
    english: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = main,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = english,
            color = Color(0xFFA6A8E8),
            fontSize = 8.sp,
            lineHeight = 12.sp,
            letterSpacing = 2.1.sp,
            textAlign = TextAlign.Center,
        )
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
