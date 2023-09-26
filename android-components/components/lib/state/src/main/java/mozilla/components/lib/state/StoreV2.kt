/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.lib.state

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mozilla.components.lib.state.internal.ReducerChainBuilderV2
import mozilla.components.lib.state.internal.StoreThreadFactory
import java.util.concurrent.Executors

/**
 * A generic store holding an immutable [State].
 *
 * The [State] can only be modified by dispatching [Action]s which will create a new state and notify all registered
 * [Observer]s.
 *
 * @param initialState The initial state until a dispatched [Action] creates a new state.
 * @param reducer A function that gets the current [State] and [Action] passed in and will return a new [State].
 * @param middleware Optional list of [Middleware] sitting between the [Store] and the [Reducer].
 * @param threadNamePrefix Optional prefix with which to name threads for the [Store]. If not provided,
 * the naming scheme will be deferred to [Executors.defaultThreadFactory]
 */
open class StoreV2<S : State, A : Action>(
    initialState: S,
    reducer: Reducer<S, A>,
    middleware: List<MiddlewareV2<S, A>> = emptyList(),
    threadNamePrefix: String? = null,
) {
    private val threadFactory = StoreThreadFactory(threadNamePrefix)
    private val dispatcher =
        Executors.newSingleThreadExecutor(threadFactory).asCoroutineDispatcher()
    private val reducerChainBuilder = ReducerChainBuilderV2(threadFactory, reducer, middleware)
    private val scope = CoroutineScope(dispatcher)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // We want exceptions in the reducer to crash the app and not get silently ignored. Therefore we rethrow the
        // exception on the main thread.
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
            throw StoreException("Exception while reducing state", throwable)
        }

        // Once an exception happened we do not want to accept any further actions. So let's cancel the scope which
        // will cancel all jobs and not accept any new ones.
        scope.cancel()
    }
    private val dispatcherWithExceptionHandler = dispatcher + exceptionHandler

    private val currentState = MutableStateFlow(initialState)

    /**
     * The current [State].
     */
    val state: S
        get() = currentState.value

    /**
     * Dispatch an [Action] to the store in order to trigger a [State] change.
     */
    @Synchronized
    fun dispatch(action: A) = scope.launch(dispatcherWithExceptionHandler) {
        synchronized(this@StoreV2) {
            reducerChainBuilder.get(this@StoreV2).invoke(action)
        }
    }

    internal fun observe(): StateFlow<S> =
        currentState.asStateFlow()

    /**
     * Transitions from the current [State] to the passed in [state] and notifies all observers.
     */
    internal fun transitionTo(state: S) {
        currentState.update { state }
    }
}
