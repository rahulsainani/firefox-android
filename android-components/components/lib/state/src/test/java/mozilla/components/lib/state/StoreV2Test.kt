/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.lib.state

import app.cash.turbine.test
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import mozilla.components.support.test.ext.joinBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StoreV2Test {
    @Test
    fun `Dispatching Action executes reducer2s and creates new State`() {
        val store = StoreV2(
            TestState2(counter = 23),
            ::reducer2,
        )

        store.dispatch(TestAction2.IncrementAction).joinBlocking()

        assertEquals(24, store.state.counter)

        store.dispatch(TestAction2.DecrementAction).joinBlocking()
        store.dispatch(TestAction2.DecrementAction).joinBlocking()

        assertEquals(22, store.state.counter)
    }

    @Test
    fun `observe emits state changes`() = runTest {
        val store = StoreV2(
            TestState2(counter = 23),
            ::reducer2,
        )

        store.observe().map { it.counter }.test {
            store.dispatch(TestAction2.IncrementAction).joinBlocking()
            assertEquals(23, awaitItem())
            assertEquals(24, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `State flow does not emit when state is the same as previous`() = runTest {
        val store = StoreV2(
            TestState2(counter = 23),
            ::reducer2,
        )

        store.observe().test {
            assertEquals(23, awaitItem().counter)
            store.dispatch(TestAction2.DoNothingAction).joinBlocking()
            expectNoEvents()
        }
    }

    @Test
    fun `Middleware chain gets executed in order`() {
        val incrementMiddleware: MiddlewareV2<TestState2, TestAction2> = { store, next, action ->
            if (action == TestAction2.DoNothingAction) {
                store.dispatch(TestAction2.IncrementAction)
            }

            next(action)
        }

        val doubleMiddleware: MiddlewareV2<TestState2, TestAction2> = { store, next, action ->
            if (action == TestAction2.DoNothingAction) {
                store.dispatch(TestAction2.DoubleAction)
            }

            next(action)
        }

        val store = StoreV2(
            TestState2(counter = 0),
            ::reducer2,
            listOf(
                incrementMiddleware,
                doubleMiddleware,
            ),
        )

        store.dispatch(TestAction2.DoNothingAction).joinBlocking()

        assertEquals(2, store.state.counter)

        store.dispatch(TestAction2.DoNothingAction).joinBlocking()

        assertEquals(6, store.state.counter)

        store.dispatch(TestAction2.DoNothingAction).joinBlocking()

        assertEquals(14, store.state.counter)

        store.dispatch(TestAction2.DecrementAction).joinBlocking()

        assertEquals(13, store.state.counter)
    }

    @Test
    fun `Middleware can intercept actions`() {
        val interceptingMiddleware: MiddlewareV2<TestState2, TestAction2> = { _, _, _ ->
            // Do nothing!
        }

        val store = StoreV2(
            TestState2(counter = 0),
            ::reducer2,
            listOf(interceptingMiddleware),
        )

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(0, store.state.counter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(0, store.state.counter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(0, store.state.counter)
    }

    @Test
    fun `Middleware can rewrite actions`() {
        val rewritingMiddleware: MiddlewareV2<TestState2, TestAction2> = { _, next, _ ->
            next(TestAction2.DecrementAction)
        }

        val store = StoreV2(
            TestState2(counter = 0),
            ::reducer2,
            listOf(rewritingMiddleware),
        )

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(-1, store.state.counter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(-2, store.state.counter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(-3, store.state.counter)
    }

    @Test
    fun `Middleware can intercept and dispatch other action instead`() {
        val rewritingMiddleware: MiddlewareV2<TestState2, TestAction2> = { store, next, action ->
            if (action == TestAction2.IncrementAction) {
                store.dispatch(TestAction2.DecrementAction)
            } else {
                next(action)
            }
        }

        val store = StoreV2(
            TestState2(counter = 0),
            ::reducer2,
            listOf(rewritingMiddleware),
        )

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(-1, store.state.counter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(-2, store.state.counter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(-3, store.state.counter)
    }

    @Test
    fun `Middleware sees state before and after reducing`() {
        var countBefore = -1
        var countAfter = -1

        val observingMiddleware: MiddlewareV2<TestState2, TestAction2> = { store, next, action ->
            countBefore = store.state.counter
            next(action)
            countAfter = store.state.counter
        }

        val store = StoreV2(
            TestState2(counter = 0),
            ::reducer2,
            listOf(observingMiddleware),
        )

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(0, countBefore)
        assertEquals(1, countAfter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(1, countBefore)
        assertEquals(2, countAfter)

        store.dispatch(TestAction2.IncrementAction).joinBlocking()
        assertEquals(2, countBefore)
        assertEquals(3, countAfter)

        store.dispatch(TestAction2.DecrementAction).joinBlocking()
        assertEquals(3, countBefore)
        assertEquals(2, countAfter)
    }

    @Test
    fun `Middleware can catch exceptions in reducer2`() {
        var caughtException: Exception? = null

        val catchingMiddleware: MiddlewareV2<TestState2, TestAction2> = { _, next, action ->
            try {
                next(action)
            } catch (e: Exception) {
                caughtException = e
            }
        }

        val store = StoreV2(
            TestState2(counter = 0),
            { _: State, _: Action -> throw IOException() },
            listOf(catchingMiddleware),
        )

        store.dispatch(TestAction2.IncrementAction).joinBlocking()

        assertNotNull(caughtException)
        assertTrue(caughtException is IOException)
    }
}

fun reducer2(state: TestState2, action: TestAction2): TestState2 = when (action) {
    is TestAction2.IncrementAction -> state.copy(counter = state.counter + 1)
    is TestAction2.DecrementAction -> state.copy(counter = state.counter - 1)
    is TestAction2.SetValueAction -> state.copy(counter = action.value)
    is TestAction2.DoubleAction -> state.copy(counter = state.counter * 2)
    is TestAction2.DoNothingAction -> state
}

data class TestState2(
    val counter: Int,
) : State

sealed class TestAction2 : Action {
    object IncrementAction : TestAction2()
    object DecrementAction : TestAction2()
    object DoNothingAction : TestAction2()
    object DoubleAction : TestAction2()
    data class SetValueAction(val value: Int) : TestAction2()
}
