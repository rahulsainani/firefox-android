package mozilla.components.lib.state.ext

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.launch
import mozilla.components.lib.state.Action
import mozilla.components.lib.state.State
import mozilla.components.lib.state.StoreV2

fun <S : State, A : Action> StoreV2<S, A>.collectWithLifecycle(
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: (S) -> Unit,
) {
    lifecycle.coroutineScope.launch {
        observe()
            .flowWithLifecycle(
                lifecycle = lifecycle,
                minActiveState = minActiveState,
            )
            .collect {
                block(it)
            }
    }
}

@Composable
fun <S : State, A : Action> StoreV2<S, A>.collectAsState(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): androidx.compose.runtime.State<S> {
    return observe()
        .collectAsStateWithLifecycle(minActiveState = minActiveState)
}
