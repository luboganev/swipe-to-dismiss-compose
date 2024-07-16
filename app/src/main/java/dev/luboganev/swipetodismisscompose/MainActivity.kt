@file:OptIn(ExperimentalFoundationApi::class)

package dev.luboganev.swipetodismisscompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.luboganev.swipetodismisscompose.ui.theme.SwipeToDismissComposeTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwipeToDismissComposeTheme {
                DemoContents()
            }
        }
    }
}

@Composable
fun DemoContents() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
    ) {
        var showContainer by remember { mutableStateOf(true) }
        Button(
            modifier = Modifier.align(alignment = Alignment.TopCenter).padding(top = 180.dp),
            onClick = { showContainer = !showContainer }
        ) {
            Text(
                text = if (showContainer) "Hide" else "Show"
            )
        }

        SwipeToDismissContainer(
            show = showContainer,
            modifier = Modifier
                .padding(top = 64.dp)
                .width(width = 160.dp)
                .align(alignment = Alignment.TopCenter),
            onDismissRequest = { showContainer = false },
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                text = "Card contents",
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SwipeToDismissContainer(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    swipeDistanceDp: Dp = 160.dp,
    swipeToDismissThreshold: Float = 0.3f,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A basic container with some elevation
    val backgroundShape = RoundedCornerShape(16.dp)
    val backgroundModifier = Modifier
        .background(
            shape = backgroundShape,
            color = MaterialTheme.colorScheme.surface,
        )
        .border(
            width = 2.dp,
            shape = backgroundShape,
            color = MaterialTheme.colorScheme.primary,
        )

    val (anchorState, alpha) = rememberSwipeStates(
        swipeDistanceDp = swipeDistanceDp,
        swipeToDismissThreshold = swipeToDismissThreshold
    )
    TriggerDismissAfterHidden(
        anchorState = anchorState,
        show = show,
        onDismissRequest = onDismissRequest,
    )
    AnimateVisibility(
        anchorState = anchorState,
        show = show,
    )

    if (anchorState.currentValue == HorizontalSwipeAnchor.CENTER) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    this.translationX = anchorState.offset
                    this.alpha = alpha.value
                }
                .then(modifier)
                .anchoredDraggable(
                    state = anchorState,
                    orientation = Orientation.Horizontal,
                )
                .then(backgroundModifier),
            content = content
        )
    }

}

@Composable
private fun rememberSwipeStates(
    swipeDistanceDp: Dp,
    swipeToDismissThreshold: Float,
): Pair<AnchoredDraggableState<HorizontalSwipeAnchor>, State<Float>> {
    // We need exact pixels for the APIs related to the offset
    val swipeDistanceAndVelocityPx = with(LocalDensity.current) { swipeDistanceDp.toPx() }

    // Define the different anchors we want to support. We want to be able to swipe left
    // and right, while the initial state we call CENTER has an offset value of 0f.
    val anchors = remember {
        DraggableAnchors {
            HorizontalSwipeAnchor.CENTER at 0f
            HorizontalSwipeAnchor.LEFT at swipeDistanceAndVelocityPx.unaryMinus()
            HorizontalSwipeAnchor.RIGHT at swipeDistanceAndVelocityPx
        }
    }
    // Use the defined anchors and created the state
    val anchorState = remember {
        AnchoredDraggableState(
            initialValue = HorizontalSwipeAnchor.CENTER,
            anchors = anchors,
            positionalThreshold = { totalDistance -> totalDistance * swipeToDismissThreshold },
            velocityThreshold = { swipeDistanceAndVelocityPx },
            animationSpec = tween()
        )
    }

    // Making the component fade out while swiping away toward maximum distance.
    // Calculate alpha based on the absolute offset. The value is 1.0f when offset is 0.
    // The closer the offset gets to the max swipe distance, the closer this value gets to 0f.
    val alpha = remember {

        derivedStateOf {
            abs(abs(anchorState.offset) - swipeDistanceAndVelocityPx)
                .div(swipeDistanceAndVelocityPx)
        }
    }

    return Pair(anchorState, alpha)
}

@Composable
private fun AnimateVisibility(
    anchorState: AnchoredDraggableState<HorizontalSwipeAnchor>,
    show: Boolean,
) = LaunchedEffect(key1 = show) {
    when {
        show.not() && anchorState.currentValue == HorizontalSwipeAnchor.CENTER -> {
            anchorState.animateTo(
                targetValue = HorizontalSwipeAnchor.RIGHT,
            )
        }

        show && anchorState.currentValue != HorizontalSwipeAnchor.CENTER -> {
            anchorState.snapTo(HorizontalSwipeAnchor.CENTER)
        }
    }
}

@Composable
private fun TriggerDismissAfterHidden(
    anchorState: AnchoredDraggableState<HorizontalSwipeAnchor>,
    show: Boolean,
    onDismissRequest: () -> Unit,
) = LaunchedEffect(key1 = anchorState.currentValue) {
    when (anchorState.currentValue) {
        HorizontalSwipeAnchor.LEFT, HorizontalSwipeAnchor.RIGHT -> {
            if (show) {
                onDismissRequest()
            }
        }

        HorizontalSwipeAnchor.CENTER -> {
            // Do nothing
        }
    }
}

private enum class HorizontalSwipeAnchor {
    LEFT,
    RIGHT,
    CENTER
}

@Preview(
    showBackground = true,
)
@Composable
fun GreetingPreview() {
    SwipeToDismissComposeTheme {
        DemoContents()
    }
}
