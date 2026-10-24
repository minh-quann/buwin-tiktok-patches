/* Kyant0 component customized in MEBIECO; source hash in third-party/kyant0-backdrop/mebieco.sha256.json. */
package app.morphe.extension.tiktok.glass.mebieco.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.morphe.extension.tiktok.glass.mebieco.utils.DampedDragAnimation
import app.morphe.extension.tiktok.glass.mebieco.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isDark: Boolean = isSystemInDarkTheme(),
    liquidGlassEnabled: Boolean = true,
    activeTabColor: String? = null,
    blurRadiusDp: Float = 4f,
    modifier: Modifier = Modifier,
    activeContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isDark
    // On Android < 12 (API < 31), RenderEffect blur is not supported. Auto-fallback to solid bar.
    val isHardwareBlurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val isGlassActive = liquidGlassEnabled && isHardwareBlurSupported

    val parsedActiveColor = remember(activeTabColor) {
        activeTabColor?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (_: Exception) {
                null
            }
        }
    }
    val defaultAccentColor =
        if (isLightTheme) Color(0xFFE65100)
        else Color(0xFFE65100)
    val accentColor = parsedActiveColor ?: defaultAccentColor
    // ponytail: opacity higher than Kyant original (0.4f) because canvas capture
    // may not fully capture RN Fabric content. Upgrade: use PixelCopy with SurfaceControl API.
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()
    val solidBarBackdrop = rememberLayerBackdrop()
    val effectiveBackdrop = if (isGlassActive) backdrop else solidBarBackdrop

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        // Horizontal padding in px for pill area (matches padding(horizontal = 4.dp))
        val horizontalPaddingPx = with(density) { 4f.dp.toPx() }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 67f / 48f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    onTabSelected(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        val targetTabIndex = selectedTabIndex()
        LaunchedEffect(targetTabIndex) {
            dampedDragAnimation.animateToValue(targetTabIndex.toFloat())
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 1. Tab bar background layer
        if (isGlassActive) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            vibrancy()
                            blur(blurRadiusDp.dp.toPx())
                            lens(21f.dp.toPx(), 21f.dp.toPx())
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow(
                                radius = 16f.dp,
                                offset = DpOffset(0f.dp, 8f.dp),
                                color = if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.35f)
                            )
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp)
            )
        } else {
            // Solid color bar (no screen capture needed from RN, 100% GPU inside Compose)
            val solidContainerColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
            val borderColor = if (isLightTheme) Color(0x1A000000) else Color(0x30FFFFFF)
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = 16f
                        shape = ContinuousCapsule
                        clip = false
                    }
                    .layerBackdrop(solidBarBackdrop)
                    .background(solidContainerColor, ContinuousCapsule)
                    .border(0.5.dp, borderColor, ContinuousCapsule)
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp)
            )
        }

        // 2. Icon layer with lens punch-out
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                    compositingStrategy = CompositingStrategy.Offscreen
                    val progress = dampedDragAnimation.pressProgress
                    val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                    scaleX = scale
                    scaleY = scale
                }
                .height(56f.dp)
                .fillMaxWidth()
                .padding(4f.dp)
                .drawWithContent {
                    drawContent()
                    
                    val currentTabX = if (isLtr) {
                        dampedDragAnimation.value * tabWidth
                    } else {
                        size.width - (dampedDragAnimation.value + 1f) * tabWidth
                    }
                    
                    var scaleX = dampedDragAnimation.scaleX
                    var scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)

                    val rectWidth = tabWidth * scaleX
                    val rectHeight = size.height * scaleY
                    val rectLeft = currentTabX + (tabWidth - rectWidth) / 2f
                    val rectTop = (size.height - rectHeight) / 2f

                    translate(rectLeft, rectTop) {
                        val outline = ContinuousCapsule.createOutline(
                            androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
                            layoutDirection,
                            this@drawWithContent
                        )
                        when (outline) {
                            is androidx.compose.ui.graphics.Outline.Rectangle -> drawRect(
                                color = Color.Black,
                                topLeft = outline.rect.topLeft,
                                size = outline.rect.size,
                                blendMode = BlendMode.Clear
                            )
                            is androidx.compose.ui.graphics.Outline.Rounded -> {
                                drawPath(
                                    path = androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) },
                                    color = Color.Black,
                                    blendMode = BlendMode.Clear
                                )
                            }
                            is androidx.compose.ui.graphics.Outline.Generic -> drawPath(
                                path = outline.path,
                                color = Color.Black,
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // 3. Active tinted icon inside the lens
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = effectiveBackdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            if (isGlassActive) {
                                blur(blurRadiusDp.dp.toPx())
                            }
                            lens(
                                20f.dp.toPx() * progress,
                                20f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = null,
                        onDrawSurface = {
                            if (isGlassActive) {
                                drawRect(containerColor)
                            } else {
                                val solidContainerColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
                                drawRect(solidContainerColor)
                            }
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(48f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = activeContent ?: content
            )
        }

        // 4. THE LIQUID GLASS PILL 
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(effectiveBackdrop, tabsBackdrop),
                    shape = { ContinuousCapsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        val baseLens = if (isGlassActive) 0f else 5f.dp.toPx()
                        lens(
                            baseLens + 8.5f.dp.toPx() * progress,
                            baseLens + 12f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        val baseHighlight = if (isGlassActive) 0f else 0.5f
                        Highlight.Default.copy(alpha = (baseHighlight + (1f - baseHighlight) * progress).coerceIn(0f, 1f))
                    },
                    shadow = {
                        if (isGlassActive) {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(
                                radius = 16f.dp,
                                color = if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.2f),
                                alpha = progress
                            )
                        } else {
                            Shadow(alpha = 0f)
                        }
                    },
                    innerShadow = {
                        if (isGlassActive) {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 4f.dp + 4f.dp * progress,
                                alpha = progress
                            )
                        } else {
                            InnerShadow(alpha = 0f)
                        }
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        if (isGlassActive) {
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.1f)
                                else Color.White.copy(0.1f),
                                alpha = 1f - progress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        } else {
                            // On solid bar: translucent liquid glass reflection with glossy sheen
                            drawRect(
                                if (isLightTheme) Color.Black.copy(alpha = 0.06f)
                                else Color.White.copy(alpha = 0.14f),
                                alpha = 1f - progress
                            )
                            drawRect(
                                if (isLightTheme) Color.Black.copy(alpha = 0.03f * progress)
                                else Color.White.copy(alpha = 0.06f * progress)
                            )
                        }
                    }
                )
                .height(48f.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        // Touch overlay: captures touch anywhere on the tab bar,
        // moves pill to finger position continuously, switches tab only on release
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(tabWidth, isLtr, tabsCount) {
                    awaitEachGesture {
                        // Wait for initial touch
                        val down = awaitFirstDown(requireUnconsumed = false)

                        // Convert touch X to fractional tab value (continuous, not snapped)
                        fun xToTabValue(x: Float): Float {
                            val clampedX = (x - horizontalPaddingPx)
                                .coerceIn(0f, tabWidth * tabsCount)
                            val raw = if (isLtr) {
                                clampedX / tabWidth - 0.5f
                            } else {
                                (tabWidth * tabsCount - clampedX) / tabWidth - 0.5f
                            }
                            return raw.fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        }

                        // Start press animation and move pill to finger
                        val initialTabValue = xToTabValue(down.position.x)
                        dampedDragAnimation.press()
                        dampedDragAnimation.updateValue(initialTabValue)

                        // Track finger movement
                        var pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes
                                .fastFirstOrNull { it.id == pointerId }
                                ?: break

                            if (change.changedToUpIgnoreConsumed()) {
                                // Finger released: snap to nearest tab and switch
                                val finalValue = xToTabValue(change.position.x)
                                val targetIndex = finalValue
                                    .fastRoundToInt()
                                    .fastCoerceIn(0, tabsCount - 1)
                                dampedDragAnimation.animateToValue(targetIndex.toFloat())
                                onTabSelected(targetIndex)
                                dampedDragAnimation.release()
                                break
                            }

                            // Finger is still down: move pill to current finger position
                            if (change.positionChange() != Offset.Zero || change.pressed) {
                                val tabValue = xToTabValue(change.position.x)
                                dampedDragAnimation.updateValue(tabValue)
                            }
                            pointerId = change.id
                        }
                    }
                }
        )
    }
}
