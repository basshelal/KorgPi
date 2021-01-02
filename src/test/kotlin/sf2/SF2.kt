@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package sf2

import mustEqual
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("SoundFont Tests")
class SF2 {

    val JSoundbank = com.sun.media.sound.SF2Soundbank(File("res/Example.sf2"))
    val KSoundbank = com.github.basshelal.korgpi.sf2.SF2Soundbank("res/Example.sf2")

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
        }
    }

    @Test
    fun `Variables are equal`() {
        JSoundbank.name mustEqual KSoundbank.name
        JSoundbank.romName mustEqual KSoundbank.romName
        JSoundbank.romVersionMajor mustEqual KSoundbank.romVersionMajor
        JSoundbank.romVersionMinor mustEqual KSoundbank.romVersionMinor
        JSoundbank.targetEngine mustEqual KSoundbank.targetEngine
        JSoundbank.creationDate mustEqual KSoundbank.creationDate
        JSoundbank.product mustEqual KSoundbank.product
        JSoundbank.tools mustEqual KSoundbank.tools

        JSoundbank.samples.size mustEqual KSoundbank.samples.size

        JSoundbank.instruments.size mustEqual KSoundbank.instruments.size
        JSoundbank.layers.size mustEqual KSoundbank.layers.size

        JSoundbank.samples.first().dataBuffer.capacity() mustEqual
                KSoundbank.samples.first().data?.capacity()

        JSoundbank.samples.last().dataBuffer.capacity() mustEqual
                KSoundbank.samples.last().data?.capacity()
    }


}