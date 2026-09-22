package com.fintrack.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pull-to-refresh propio (sin librerías): arrastrar hacia abajo desde el
 * tope estira el contenido y al soltar dispara [onRefresh].
 *
 * @param atTopProvider true cuando la lista está en el tope (el gesto solo
 * se arma ahí).
 * @param content recibe el Modifier ya con nestedScroll + desplazamiento;
 * aplícalo a tu LazyColumn.
 */
@Composable
fun PullRefreshLayout(
    onRefresh: () -> Unit,
    isLoading: Boolean,
    atTopProvider: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 110.dp.toPx() }
    val holdPx = with(density) { 64.dp.toPx() }
    val maxPx = with(density) { 200.dp.toPx() }
    val pullPx = remember { Animatable(0f) }
    var pullRefreshing by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    val loadingState = rememberUpdatedState(isLoading)
    val pullScope = rememberCoroutineScope()
    val refreshState = rememberUpdatedState(onRefresh)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (pullRefreshing || settling) return Offset.Zero
                val dy = available.y
                if (dy > 0 && atTopProvider() && pullPx.value < maxPx) {
                    val consume = minOf(dy, maxPx - pullPx.value)
                    if (consume > 0) {
                        pullScope.launch { pullPx.snapTo(pullPx.value + consume) }
                        return Offset(0f, consume)
                    }
                } else if (dy < 0 && pullPx.value > 0) {
                    val consume = maxOf(dy, -pullPx.value)
                    pullScope.launch { pullPx.snapTo(pullPx.value + consume) }
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }

            suspend fun release() {
                if (pullRefreshing || settling || pullPx.value <= 0f) return
                settling = true
                try {
                    if (pullPx.value >= thresholdPx) {
                        pullRefreshing = true
                        refreshState.value()
                        pullPx.animateTo(holdPx)
                        // Espera a que termine la recarga (con tope).
                        delay(400)
                        var waited = 0
                        while (loadingState.value && waited < 12000) {
                            delay(200)
                            waited += 200
                        }
                        delay(400)
                    } else {
                        pullPx.animateTo(0f)
                    }
                } finally {
                    pullPx.snapTo(0f)
                    pullRefreshing = false
                    settling = false
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                release()
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                release()
                return super.onPostFling(consumed, available)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        val pull = pullPx.value
        if (pull > 1f || pullRefreshing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { pull.toDp() })
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pullRefreshing || isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Actualizando…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(
                            alpha = (pull / thresholdPx).coerceIn(0f, 1f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (pull >= thresholdPx) "Suelta para actualizar"
                        else "Desliza para actualizar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        content(
            Modifier
                .fillMaxSize()
                .nestedScroll(connection)
                .graphicsLayer { translationY = pullPx.value }
        )
    }
}
