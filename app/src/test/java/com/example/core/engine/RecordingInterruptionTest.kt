package com.example.core.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RecordingInterruptionTest {

    private lateinit var stateMachine: RecordingStateMachine
    private val dummyFile = File("/tmp/test_interruption.m4a")

    @Before
    fun setUp() {
        stateMachine = RecordingStateMachine()
    }

    @Test
    fun standardPause_hasIsInterruptedFalse() = runTest {
        stateMachine.start(dummyFile)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        stateMachine.pause(isInterrupted = false)
        val pausedState = stateMachine.state.value
        assertTrue(pausedState is RecordingState.Paused)
        assertFalse((pausedState as RecordingState.Paused).isInterrupted)
    }

    @Test
    fun interruptionPause_setsIsInterruptedTrue() = runTest {
        stateMachine.start(dummyFile)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        // Audio Focus loss / incoming phone call interruption
        stateMachine.pause(isInterrupted = true)
        val pausedState = stateMachine.state.value
        assertTrue(pausedState is RecordingState.Paused)
        assertTrue((pausedState as RecordingState.Paused).isInterrupted)
    }

    @Test
    fun resumeFromInterruption_restoresRecordingState() = runTest {
        stateMachine.start(dummyFile)
        stateMachine.pause(isInterrupted = true)
        assertTrue((stateMachine.state.value as RecordingState.Paused).isInterrupted)

        val resumeResult = stateMachine.resume()
        assertTrue(resumeResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Recording)
    }

    @Test
    fun cancelFromInterruption_resetsToIdle() = runTest {
        stateMachine.start(dummyFile)
        stateMachine.pause(isInterrupted = true)

        val cancelResult = stateMachine.cancel()
        assertTrue(cancelResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Idle)
    }
}
