package com.github.basshelal.korgpi.app

import com.github.basshelal.korgpi.JackMixer
import com.github.basshelal.korgpi.RealTimeCritical
import com.github.basshelal.korgpi.TWOPI
import com.github.basshelal.korgpi.audio.AudioOutPort
import com.github.basshelal.korgpi.extensions.D
import com.github.basshelal.korgpi.extensions.F
import com.github.basshelal.korgpi.extensions.updateEach
import com.github.basshelal.korgpi.extensions.zero
import com.github.basshelal.korgpi.midi.MidiInPort
import com.github.basshelal.korgpi.midi.MidiMessage
import com.github.basshelal.korgpi.midiKeyToFrequency
import java.nio.FloatBuffer
import kotlin.math.sin

class Synth(midiInPort: MidiInPort, audioOutPort: AudioOutPort) {

    var frequency: Double = 0.0
    var sampleRate = 0.0
    var angle: Double = 0.0
    var angleDelta: Double = 0.0

    var isPressed = false

    init {
        sampleRate = JackMixer.sampleRate.D
        midiInPort.callbacks.add(this::onMidiMessage)
        audioOutPort.callbacks.add(this::processAudio)
    }

    @RealTimeCritical
    fun onMidiMessage(message: MidiMessage) {
        frequency = midiKeyToFrequency(message.data1.toInt())
        updateAngleDelta()
        when (message.command) {
            MidiMessage.NOTE_ON -> {
                isPressed = true
            }
            MidiMessage.NOTE_OFF -> {
                isPressed = false
            }
        }
    }

    @RealTimeCritical
    fun processAudio(floatBuffer: FloatBuffer) {
        if (isPressed) {
            val level = 0.02F
            floatBuffer.updateEach { value, index ->
                val currentSample = sin(angle).F
                angle += angleDelta
                return@updateEach level * currentSample
            }
        } else {
            floatBuffer.zero()
        }
    }

    fun updateAngleDelta() {
        val cyclesPerSample: Double = frequency / sampleRate
        angleDelta = cyclesPerSample * TWOPI
    }

}