package com.example.core.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RecordingStateMachineTest {

    private lateinit var stateMachine: RecordingStateMachine
    private val dummyFile = File("/tmp/test_session.m4a")

    @Before
    fun setUp() {
        stateMachine = RecordingStateMachine()
    }

    @Test
    fun initialState_isIdle() {
        assertTrue(stateMachine.state.value is RecordingState.Idle)
    }

    @Test
    fun validLifecycle_idleToPreparingToRecordingToPausedToResumedToStoppingToSaved() = runTest {
        // IDLE -> RECORDING
        val startResult = stateMachine.start(dummyFile)
        assertTrue(startResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        // RECORDING -> PAUSED
        val pauseResult = stateMachine.pause()
        assertTrue(pauseResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Paused)

        // PAUSED -> RECORDING (RESUME)
        val resumeResult = stateMachine.resume()
        assertTrue(resumeResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        // RECORDING -> STOPPING -> SAVED
        val stopResult = stateMachine.stop()
        assertTrue(stopResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Saved)
    }

    @Test
    fun invalidTransition_idleToPause_isRejected() = runTest {
        val result = stateMachine.pause()
        assertFalse(result.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Idle)
    }

    @Test
    fun invalidTransition_idleToStop_isRejected() = runTest {
        val result = stateMachine.stop()
        assertFalse(result.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Idle)
    }

    @Test
    fun invalidTransition_pausedToPause_isRejected() = runTest {
        stateMachine.start(dummyFile)
        stateMachine.pause()
        assertTrue(stateMachine.state.value is RecordingState.Paused)

        val secondPause = stateMachine.pause()
        assertFalse(secondPause.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Paused)
    }

    @Test
    fun invalidTransition_savedToResume_isRejected() = runTest {
        stateMachine.start(dummyFile)
        stateMachine.stop()
        assertTrue(stateMachine.state.value is RecordingState.Saved)

        val resumeResult = stateMachine.resume()
        assertFalse(resumeResult.isSuccess)
    }

    @Test
    fun cancel_alwaysReturnsStateToIdle() = runTest {
        stateMachine.start(dummyFile)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        val cancelResult = stateMachine.cancel()
        assertTrue(cancelResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Idle)
    }

    @Test
    fun errorState_allowsTransitionBackToStart() = runTest {
        val failingMachine = RecordingStateMachine(
            onStart = { _, _ -> Result.failure(RuntimeException("Microphone busy")) }
        )
        val failResult = failingMachine.start(dummyFile)
        assertFalse(failResult.isSuccess)
        assertTrue(failingMachine.state.value is RecordingState.Error)
        assertEquals("Microphone busy", (failingMachine.state.value as RecordingState.Error).message)
    }
}
