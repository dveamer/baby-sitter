package com.dveamer.babysitter.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dveamer.babysitter.R
import com.dveamer.babysitter.tutorial.TutorialStep
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TutorialTargetKey {
    SETTINGS_TAB,
    SOUND_ROW,
    MOTION_ROW,
    WEB_SERVICE_ROW
}

fun Modifier.captureTutorialBounds(
    key: TutorialTargetKey,
    onBoundsChanged: (TutorialTargetKey, Rect) -> Unit
): Modifier {
    return this.onGloballyPositioned { coordinates ->
        onBoundsChanged(key, coordinates.boundsInRoot())
    }
}

@Composable
fun AppTutorialOverlay(
    step: TutorialStep,
    targetBounds: Map<TutorialTargetKey, Rect>,
    onDismissWelcome: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSoundMotion: () -> Unit,
    onDismissRemote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = tutorialContent(step)
    val targets = resolveTargets(step, targetBounds)
    val anchorRect = unionRect(targets)
    val pulse = rememberInfiniteTransition(label = "tutorialPulse").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val action = when (step) {
        TutorialStep.WELCOME -> onDismissWelcome
        TutorialStep.SETTINGS_TAB -> onOpenSettings
        TutorialStep.SOUND_MOTION -> onDismissSoundMotion
        TutorialStep.REMOTE_WEB_SERVICE -> onDismissRemote
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val marginPx = with(density) { 20.dp.toPx() }
        val gapPx = with(density) { 26.dp.toPx() }
        val bubbleMaxWidth = when {
            maxWidth > 40.dp -> minOf(340.dp, maxWidth - 40.dp)
            else -> maxWidth
        }
        var bubbleSize by remember(step) { mutableStateOf(IntSize.Zero) }
        val bubbleOffset = calculateBubbleOffset(
            step = step,
            anchorRect = anchorRect,
            bubbleSize = bubbleSize,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            marginPx = marginPx,
            gapPx = gapPx
        )
        val bubbleRect = Rect(
            left = bubbleOffset.x,
            top = bubbleOffset.y,
            right = bubbleOffset.x + bubbleSize.width.toFloat(),
            bottom = bubbleOffset.y + bubbleSize.height.toFloat()
        )
        val backgroundModifier = if (step == TutorialStep.WELCOME) {
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = backgroundModifier.drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x4D191226),
                            Color(0x591A1031),
                            Color(0x66130A24)
                        )
                    )
                )

                targets.forEach { rect ->
                    val inset = 10f
                    val animatedStroke = 5f * pulse.value
                    drawRoundRect(
                        color = Color(0x24FFF4FB),
                        topLeft = Offset(rect.left - inset, rect.top - inset),
                        size = Size(rect.width + inset * 2, rect.height + inset * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(34f, 34f)
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFFD9E8), Color(0xFFFFF2B8))
                        ),
                        topLeft = Offset(rect.left - inset, rect.top - inset),
                        size = Size(rect.width + inset * 2, rect.height + inset * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(34f, 34f),
                        style = Stroke(width = animatedStroke, cap = StrokeCap.Round)
                    )
                }

                if (step != TutorialStep.WELCOME && anchorRect != null && bubbleSize != IntSize.Zero) {
                    drawConnector(
                        bubbleRect = bubbleRect,
                        anchorRect = anchorRect,
                        pulse = pulse.value
                    )
                }
            }
        )

        TutorialBubble(
            step = step,
            content = content,
            onAction = action,
            maxBubbleWidth = bubbleMaxWidth,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = bubbleOffset.x.roundToInt(),
                        y = bubbleOffset.y.roundToInt()
                    )
                }
                .onGloballyPositioned { coordinates ->
                    bubbleSize = coordinates.size
                }
        )
    }
}

@Composable
private fun TutorialBubble(
    step: TutorialStep,
    content: TutorialBubbleContent,
    onAction: () -> Unit,
    maxBubbleWidth: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    val titleAlign = if (step == TutorialStep.WELCOME) TextAlign.Center else TextAlign.Start
    val messageAlign = if (step == TutorialStep.WELCOME) TextAlign.Center else TextAlign.Start

    Surface(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = maxBubbleWidth),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 24.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFF4F8),
                            Color(0xFFFFE8D7),
                            Color(0xFFFFD9E4)
                        )
                    ),
                    shape = shape
                )
                .border(
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
                    shape = shape
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFA9C4), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(content.badgeRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5D1E42),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(content.stepRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF7A395E),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = stringResource(content.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF35122A),
                    fontWeight = FontWeight.Bold,
                    textAlign = titleAlign
                )
                Text(
                    text = stringResource(content.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF4C2443),
                    textAlign = messageAlign
                )
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7B305C),
                        contentColor = Color(0xFFFFF8FB)
                    ),
                    modifier = Modifier.align(
                        if (step == TutorialStep.WELCOME) Alignment.CenterHorizontally
                        else Alignment.End
                    )
                ) {
                    Text(stringResource(content.actionRes))
                }
            }
        }
    }
}

private data class TutorialBubbleContent(
    @StringRes val badgeRes: Int,
    @StringRes val stepRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @StringRes val actionRes: Int
)

@Composable
private fun tutorialContent(step: TutorialStep): TutorialBubbleContent {
    return when (step) {
        TutorialStep.WELCOME -> TutorialBubbleContent(
            badgeRes = R.string.tutorial_badge_welcome,
            stepRes = R.string.tutorial_step_one,
            titleRes = R.string.tutorial_welcome_title,
            messageRes = R.string.tutorial_welcome_body,
            actionRes = R.string.tutorial_welcome_button
        )

        TutorialStep.SETTINGS_TAB -> TutorialBubbleContent(
            badgeRes = R.string.tutorial_badge_setup,
            stepRes = R.string.tutorial_step_two,
            titleRes = R.string.tutorial_settings_title,
            messageRes = R.string.tutorial_settings_body,
            actionRes = R.string.tutorial_settings_button
        )

        TutorialStep.SOUND_MOTION -> TutorialBubbleContent(
            badgeRes = R.string.tutorial_badge_setup,
            stepRes = R.string.tutorial_step_three,
            titleRes = R.string.tutorial_sound_motion_title,
            messageRes = R.string.tutorial_sound_motion_body,
            actionRes = R.string.tutorial_button_ok
        )

        TutorialStep.REMOTE_WEB_SERVICE -> TutorialBubbleContent(
            badgeRes = R.string.tutorial_badge_tip,
            stepRes = R.string.tutorial_step_four,
            titleRes = R.string.tutorial_remote_title,
            messageRes = R.string.tutorial_remote_body,
            actionRes = R.string.tutorial_button_ok
        )
    }
}

private fun resolveTargets(
    step: TutorialStep,
    targetBounds: Map<TutorialTargetKey, Rect>
): List<Rect> {
    return when (step) {
        TutorialStep.WELCOME -> emptyList()
        TutorialStep.SETTINGS_TAB -> listOfNotNull(targetBounds[TutorialTargetKey.SETTINGS_TAB])
        TutorialStep.SOUND_MOTION -> listOfNotNull(
            targetBounds[TutorialTargetKey.SOUND_ROW],
            targetBounds[TutorialTargetKey.MOTION_ROW]
        )
        TutorialStep.REMOTE_WEB_SERVICE -> listOfNotNull(
            targetBounds[TutorialTargetKey.WEB_SERVICE_ROW]
        )
    }
}

private fun unionRect(rects: List<Rect>): Rect? {
    if (rects.isEmpty()) return null
    return rects.reduce { acc, rect ->
        Rect(
            left = min(acc.left, rect.left),
            top = min(acc.top, rect.top),
            right = max(acc.right, rect.right),
            bottom = max(acc.bottom, rect.bottom)
        )
    }
}

private fun calculateBubbleOffset(
    step: TutorialStep,
    anchorRect: Rect?,
    bubbleSize: IntSize,
    screenWidthPx: Float,
    screenHeightPx: Float,
    marginPx: Float,
    gapPx: Float
): Offset {
    if (bubbleSize == IntSize.Zero) {
        return Offset(
            x = screenWidthPx / 2f,
            y = screenHeightPx / 2f
        )
    }

    val bubbleWidth = bubbleSize.width.toFloat()
    val bubbleHeight = bubbleSize.height.toFloat()
    val maxX = (screenWidthPx - bubbleWidth).coerceAtLeast(0f)
    val maxY = (screenHeightPx - bubbleHeight).coerceAtLeast(0f)

    if (step == TutorialStep.WELCOME || anchorRect == null) {
        return Offset(
            x = ((screenWidthPx - bubbleWidth) / 2f).coerceIn(0f, maxX),
            y = ((screenHeightPx - bubbleHeight) / 2f).coerceIn(0f, maxY)
        )
    }

    val xRangeStart = if (screenWidthPx - bubbleWidth >= marginPx * 2f) marginPx else 0f
    val xRangeEnd = if (screenWidthPx - bubbleWidth >= marginPx * 2f) {
        screenWidthPx - bubbleWidth - marginPx
    } else {
        maxX
    }
    val x = (anchorRect.center.x - bubbleWidth / 2f)
        .coerceIn(xRangeStart, xRangeEnd)
    val preferBelow = anchorRect.center.y < screenHeightPx * 0.46f
    val aboveY = anchorRect.top - bubbleHeight - gapPx
    val belowY = anchorRect.bottom + gapPx
    val y = when {
        preferBelow && belowY + bubbleHeight + marginPx <= screenHeightPx -> belowY
        aboveY >= marginPx -> aboveY
        belowY + bubbleHeight + marginPx <= screenHeightPx -> belowY
        else -> (screenHeightPx - bubbleHeight) / 2f
    }
    return Offset(x = x.coerceIn(0f, maxX), y = y.coerceIn(0f, maxY))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConnector(
    bubbleRect: Rect,
    anchorRect: Rect,
    pulse: Float
) {
    val start = if (bubbleRect.center.y < anchorRect.center.y) {
        Offset(bubbleRect.center.x, bubbleRect.bottom - 8f)
    } else {
        Offset(bubbleRect.center.x, bubbleRect.top + 8f)
    }
    val end = if (bubbleRect.center.y < anchorRect.center.y) {
        Offset(anchorRect.center.x, anchorRect.top - 6f)
    } else {
        Offset(anchorRect.center.x, anchorRect.bottom + 6f)
    }
    val control = Offset(
        x = (start.x + end.x) / 2f,
        y = if (bubbleRect.center.y < anchorRect.center.y) {
            start.y + (end.y - start.y) * 0.28f
        } else {
            start.y - (start.y - end.y) * 0.28f
        }
    )
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFD6E7), Color(0xFFFFF1BE))
        ),
        style = Stroke(
            width = 8f * pulse,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    val angle = atan2(end.y - control.y, end.x - control.x)
    val headLength = 26f
    val wingAngle = 0.6f
    val leftWing = Offset(
        x = end.x - headLength * cos(angle - wingAngle),
        y = end.y - headLength * sin(angle - wingAngle)
    )
    val rightWing = Offset(
        x = end.x - headLength * cos(angle + wingAngle),
        y = end.y - headLength * sin(angle + wingAngle)
    )
    drawLine(
        color = Color(0xFFFFF3C5),
        start = end,
        end = leftWing,
        strokeWidth = 8f * pulse,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFFFFF3C5),
        start = end,
        end = rightWing,
        strokeWidth = 8f * pulse,
        cap = StrokeCap.Round
    )
}
