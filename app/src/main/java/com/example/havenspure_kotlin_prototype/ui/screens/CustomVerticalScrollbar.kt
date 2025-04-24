import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.lerp

@Composable
fun CustomVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    width: Float = 8f,
    color: Color = Color.White.copy(alpha = 0.5f)
) {
    // Scrollbar visibility and size calculations
    val isScrollable by remember {
        derivedStateOf { scrollState.maxValue > 0 }
    }

    // Only show scrollbar if content is scrollable
    if (isScrollable) {
        // Calculate thumb size as a percentage of the total scrollable area
        val thumbSizePercentage = remember(scrollState.maxValue) {
            val viewportRatio = scrollState.viewportSize.toFloat() / (scrollState.maxValue + scrollState.viewportSize)
            viewportRatio.coerceIn(0.1f, 1f) // Ensure the thumb has a minimum and maximum size
        }

        // Calculate thumb position based on current scroll
        val scrollPercentage by remember(scrollState.value, scrollState.maxValue) {
            derivedStateOf {
                if (scrollState.maxValue == 0) 0f
                else scrollState.value.toFloat() / scrollState.maxValue
            }
        }

        // Animate the thumb position
        val animatedScrollPercentage by animateFloatAsState(
            targetValue = scrollPercentage,
            animationSpec = tween(durationMillis = 100),
            label = "scrollPosition"
        )

        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(width.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Track
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(width / 2))
                    .width((width / 2).dp)
                    .background(color.copy(alpha = 0.2f))
            )

            // Thumb
            val availableSpace = remember(thumbSizePercentage) {
                // Calculate the remaining space where the thumb can move
                1f - thumbSizePercentage
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight(thumbSizePercentage)
                    .clip(RoundedCornerShape(width / 2))
                    .width(width.dp)
                    .background(color)
                    // Calculate the correct offset based on scroll percentage and available space
                    .offset(
                        y = lerp(
                            0.dp,
                            (availableSpace * scrollState.viewportSize).dp,
                            animatedScrollPercentage
                        )
                    )
            )
        }
    }
}