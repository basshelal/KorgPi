@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.github.basshelal.korgpi.sf2

import com.github.basshelal.korgpi.extensions.I
import com.github.basshelal.korgpi.extensions.subBuffer
import com.github.basshelal.korgpi.log.logD
import com.sun.media.sound.ModelByteBuffer
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer

// TODO: 31/12/2020 Reimplement this, probably into a java.nio.ByteBuffer
typealias MByteBuffer = ModelByteBuffer

class SF2Soundbank(inputStream: InputStream) {

    // version of the Sound Font RIFF file
    var major: Int = -1
    var minor: Int = -1

    // target Sound Engine
    var targetEngine: String = "EMU8000"

    // Sound Font Bank Name
    var name: String = "untitled"

    // Sound ROM Name
    var romName: String = ""

    // Sound ROM Version
    var romVersionMajor: Int = -1
    var romVersionMinor: Int = -1

    // Date of Creation of the Bank
    var creationDate: String = ""

    // Sound Designers and Engineers for the Bank
    var engineers: String = ""

    // Product for which the Bank was intended
    var product: String = ""

    // Copyright message
    var copyright: String = ""

    // Comments
    var comments: String = ""

    // The SoundFont tools used to create and alter the bank
    var tools: String = ""

    // The Sample Data loaded from the SoundFont
    var sampleData: ByteBuffer? = null
    var sampleData24: ByteBuffer? = null

    val instruments: MutableList<SF2Instrument> = mutableListOf()
    val layers: MutableList<SF2Layer> = mutableListOf()
    val samples: MutableList<SF2Sample> = mutableListOf()

    constructor(file: File) : this(FileInputStream(file))

    constructor(url: URL) : this(url.openStream())

    constructor(filePath: String) : this(FileInputStream(filePath))

    init {
        try {
            val riffReader = RIFFReader(inputStream)
            if (riffReader.format != "RIFF") throw RIFFInvalidFormatException("Input stream is not a valid RIFF stream!")
            if (riffReader.type != "sfbk") throw RIFFInvalidFormatException("Input stream is not a valid SoundFont!")

            riffReader.forEach { chunk ->
                if (chunk.format == "LIST") {
                    if (chunk.type == "INFO") readInfoChunk(chunk)
                    if (chunk.type == "sdta") readSdtaChunk(chunk)
                    if (chunk.type == "pdta") readPdtaChunk(chunk)
                }
            }
        } finally {
            inputStream.close()
        }
    }

    private fun readInfoChunk(riffReader: RIFFReader) {
        riffReader.forEach { chunk ->
            when (chunk.format) {
                "ifil" -> {
                    this.major = chunk.uShort
                    this.minor = chunk.uShort
                }
                "isng" -> {
                    this.targetEngine = chunk.string
                }
                "INAM" -> {
                    this.name = chunk.string
                }
                "irom" -> {
                    this.romName = chunk.string
                }
                "iver" -> {
                    this.romVersionMajor = chunk.uShort
                    this.romVersionMinor = chunk.uShort
                }
                "ICRD" -> {
                    this.creationDate = chunk.string
                }
                "IENG" -> {
                    this.engineers = chunk.string
                }
                "IPRD" -> {
                    this.product = chunk.string
                }
                "ICOP" -> {
                    this.copyright = chunk.string
                }
                "ICMT" -> {
                    this.comments = chunk.string
                }
                "ISFT" -> {
                    this.tools = chunk.string
                }
            }
        }
        // TODO: 05/01/2021 Verify all necessary was loaded, else throw an Exception
    }

    private fun readSdtaChunk(riffReader: RIFFReader) {
        riffReader.forEach { chunk ->
            when (chunk.format) {
                "smpl" -> {
                    val sampleData = ByteArray(chunk.available)
                    var read = 0
                    val avail = chunk.available
                    while (read != avail) {
                        if (avail - read > 65536) {
                            chunk.readFully(sampleData, read, 65536)
                            read += 65536
                        } else {
                            chunk.readFully(sampleData, read, avail - read)
                            read = avail
                        }
                    }
                    this.sampleData = ByteBuffer.wrap(sampleData)
                    logD("Sample Data size: ${this.sampleData?.capacity()}")
                }
                "sm24" -> {
                    val sampleData24 = ByteArray(chunk.available)
                    var read = 0
                    val avail = chunk.available
                    while (read != avail) {
                        if (avail - read > 65536) {
                            chunk.readFully(sampleData24, read, 65536)
                            read += 65536
                        } else {
                            chunk.readFully(sampleData24, read, avail - read)
                            read = avail
                        }
                    }
                    this.sampleData24 = ByteBuffer.wrap(sampleData24)
                }
            }
        }
    }

    private fun readPdtaChunk(riffReader: RIFFReader) {
        val presets = mutableListOf<SF2Instrument>()
        val presets_bagNdx = mutableListOf<Int>()
        val presets_splits_gen = mutableListOf<SF2InstrumentRegion?>()
        val presets_splits_mod = mutableListOf<SF2InstrumentRegion?>()

        val instruments = mutableListOf<SF2Layer>()
        val instruments_bagNdx = mutableListOf<Int>()
        val instruments_splits_gen = mutableListOf<SF2LayerRegion?>()
        val instruments_splits_mod = mutableListOf<SF2LayerRegion?>()

        riffReader.forEach { chunk: RIFFReader ->
            when (chunk.format) {
                // Preset Headers
                // Initialize presets
                "phdr" -> {
                    if (chunk.available % 38 != 0)
                        throw RIFFInvalidDataException("PHDR sub chunk is not a multiple of 38 bytes in length")
                    val count: Int = chunk.available / 38
                    if (count < 2)
                        throw RIFFInvalidDataException("PHDR sub chunk contains fewer than 2 records")
                    for (i in 0 until count) {
                        SF2Instrument().also { preset ->
                            preset.name = chunk.readString(20)
                            preset.preset = chunk.readUShort()
                            preset.bank = chunk.readUShort()
                            val index = chunk.readUShort()
                            presets_bagNdx.add(index)
                            preset.index = index
                            preset.library = chunk.readUInt()
                            preset.genre = chunk.readUInt()
                            preset.morphology = chunk.readUInt()
                            // Add all except last, we must still read it though to move the read cursors
                            if (i != count - 1) {
                                presets.add(preset)
                                this.instruments.add(preset)
                            }
                        }
                    }
                }
                // Preset Zones
                "pbag" -> {
                    if (chunk.available % 4 != 0)
                        throw RIFFInvalidDataException("PBAG sub chunk is not a multiple of 4 bytes in length")
                    var count: Int = chunk.available / 4

                    // Skip first record
                    val _gencount = chunk.readUShort()
                    val _modcount = chunk.readUShort()
                    while (presets_splits_gen.size < _gencount) presets_splits_gen.add(null)
                    while (presets_splits_mod.size < _modcount) presets_splits_mod.add(null)
                    count--

                    if (presets_bagNdx.isEmpty()) throw RIFFInvalidDataException("RIFF Invalid Data")

                    val offset = presets_bagNdx.first()
                    // Offset should be 0 (but just in case)
                    for (i in 0 until offset) {
                        if (count == 0) throw RIFFInvalidDataException("RIFF Invalid Data")

                        val gencount = chunk.readUShort()
                        val modcount = chunk.readUShort()
                        while (presets_splits_gen.size < gencount) presets_splits_gen.add(null)
                        while (presets_splits_mod.size < modcount) presets_splits_mod.add(null)
                        count--
                    }

                    for (i in 0 until presets_bagNdx.size - 1) {
                        val zone_count = presets_bagNdx[i + 1] - presets_bagNdx[i]
                        val preset = presets[i]
                        for (ii in 0 until zone_count) {
                            if (count == 0) throw RIFFInvalidDataException("RIFF Invalid Data")
                            val gencount = chunk.readUShort()
                            val modcount = chunk.readUShort()
                            val split = SF2InstrumentRegion()
                            preset.regions.add(split)
                            while (presets_splits_gen.size < gencount) presets_splits_gen.add(split)
                            while (presets_splits_mod.size < modcount) presets_splits_mod.add(split)
                            count--
                        }
                    }
                }
                "pmod" -> {
                    // Preset Modulators / Split Modulators
                    for (i in 0 until presets_splits_mod.size) {
                        val modulator = SF2Modulator()
                        modulator.sourceOperator = chunk.readUShort()
                        modulator.destinationOperator = chunk.readUShort()
                        modulator.amount = chunk.readShort()
                        modulator.amountSourceOperator = chunk.readUShort()
                        modulator.transportOperator = chunk.readUShort()
                        val split = presets_splits_mod[i]
                        if (split != null) split.modulators.add(modulator)
                    }
                }
                "pgen" -> {
                    // Preset Generators / Split Generators
                    for (i in 0 until presets_splits_gen.size) {
                        val operator = chunk.readUShort()
                        val amount = chunk.readShort()
                        val split = presets_splits_gen[i]
                        if (split != null) split.generators[operator] = amount
                    }
                }
                "inst" -> {
                    // Instrument Header / Layers
                    if (chunk.available() % 22 != 0) throw RIFFInvalidDataException("RIFF Invalid Data")
                    val count = chunk.available() / 22
                    for (i in 0 until count) {
                        val layer = SF2Layer(/*this*/)
                        layer.name = chunk.readString(20)
                        instruments_bagNdx.add(chunk.readUShort())
                        instruments.add(layer)
                        if (i != count - 1) this.layers.add(layer)
                    }
                }
                "ibag" -> {
                    // Instrument Zones / Layer splits
                    if (chunk.available() % 4 != 0) throw RIFFInvalidDataException("RIFF Invalid Data")
                    var count = chunk.available() / 4

                    // Skip first record
                    kotlin.run {
                        val gencount = chunk.readUShort()
                        val modcount = chunk.readUShort()
                        while (instruments_splits_gen.size < gencount) instruments_splits_gen.add(null)
                        while (instruments_splits_mod.size < modcount) instruments_splits_mod.add(null)
                        count--
                    }

                    if (instruments_bagNdx.isEmpty()) throw RIFFInvalidDataException("RIFF Invalid Data")

                    val offset = instruments_bagNdx.first()
                    // Offset should be 0 but (just in case)
                    for (i in 0 until offset) {
                        if (count == 0) throw RIFFInvalidDataException("RIFF Invalid Data")
                        val gencount = chunk.readUShort()
                        val modcount = chunk.readUShort()
                        while (instruments_splits_gen.size < gencount) instruments_splits_gen.add(null)
                        while (instruments_splits_mod.size < modcount) instruments_splits_mod.add(null)
                        count--
                    }

                    for (i in 0 until instruments_bagNdx.size - 1) {
                        val zone_count = instruments_bagNdx[i + 1] - instruments_bagNdx[i]
                        val layer = layers[i]
                        for (ii in 0 until zone_count) {
                            if (count == 0) throw RIFFInvalidDataException("RIFF Invalid Data")
                            val gencount = chunk.readUShort()
                            val modcount = chunk.readUShort()
                            val split = SF2LayerRegion()
                            layer.regions.add(split)
                            while (instruments_splits_gen.size < gencount) instruments_splits_gen.add(split)
                            while (instruments_splits_mod.size < modcount) instruments_splits_mod.add(split)
                            count--
                        }
                    }
                }
                "imod" -> {
                    // Instrument Modulators / Split Modulators
                    for (i in 0 until instruments_splits_mod.size) {
                        val modulator = SF2Modulator()
                        modulator.sourceOperator = chunk.readUShort()
                        modulator.destinationOperator = chunk.readUShort()
                        modulator.amount = chunk.readShort()
                        modulator.amountSourceOperator = chunk.readUShort()
                        modulator.transportOperator = chunk.readUShort()
                        if (i < 0 || i >= instruments_splits_gen.size) throw RIFFInvalidDataException("RIFF Invalid Data")
                        val split = instruments_splits_gen[i]
                        if (split != null) split.modulators.add(modulator)
                    }
                }
                "igen" -> {
                    // Instrument Generators / Split Generators
                    for (i in 0 until instruments_splits_gen.size) {
                        val operator = chunk.readUShort()
                        val amount = chunk.readShort()
                        val split = instruments_splits_gen[i]
                        if (split != null) split.generators[operator] = amount
                    }
                }
                "shdr" -> {
                    // Sample Headers
                    if (chunk.available % 46 != 0)
                        throw RIFFInvalidDataException("SHDR sub chunk is not a multiple of 46 bytes in length")
                    val count = chunk.available / 46
                    for (i in 0 until count) {
                        val isNotLast = i != count - 1
                        SF2Sample().also { sample ->
                            sample.name = chunk.readString(20)
                            val start = chunk.uInt
                            val end = chunk.uInt
                            logD("Name: ${sample.name} Start: $start, End: $end Size: ${end - start}")
                            this.sampleData?.also {
                                if (isNotLast)
                                    sample.data = it.subBuffer(start.I * 2, end.I * 2)
                                // sample.data = it.subBuffer(start.I * 2, (end.I * 2) + 46)
                                // + 46 is optional because spec requires samples have 46 0 bytes at the end, we
                                // don't need this but may find it useful later
                            }
                            this.sampleData24?.also {
                                // TODO: 09/01/2021
                                //  sample.data24 = it.subbuffer(start, end, true)
                            }
                            sample.startLoop = chunk.uInt - start
                            sample.endLoop = chunk.uInt - start
                            if (sample.startLoop < 0) sample.startLoop = -1
                            if (sample.endLoop < 0) sample.endLoop = -1
                            sample.sampleRate = chunk.uInt
                            sample.originalPitch = chunk.uByte
                            sample.pitchCorrection = chunk.byte
                            sample.sampleLink = chunk.uShort
                            sample.sampleType = chunk.uShort
                            if (isNotLast) this.samples.add(sample)
                        }
                    }
                }
            }
        }

        this.layers.forEach { layer ->
            var globalSplit: SF2Region? = null
            layer.regions.forEach { split ->
                val sampleid = split.generators[SF2Region.GENERATOR_SAMPLEID]?.I
                if (sampleid != null) {
                    split.generators.remove(SF2Region.GENERATOR_SAMPLEID)
                    if (sampleid < 0 || sampleid >= samples.size) throw RIFFInvalidDataException("RIFF Invalid Data")
                    split.sample = samples[sampleid]
                } else {
                    globalSplit = split
                }
            }
            globalSplit?.also {
                layer.regions.remove(it)
                val gsplit = SF2Region()
                gsplit.generators = it.generators
                gsplit.modulators = it.modulators
                layer.globalRegion = gsplit
            }
        }

        this.instruments.forEach { instrument ->
            var globalSplit: SF2Region? = null
            instrument.regions.forEach { split ->
                val instrumentId = split.generators[SF2Region.GENERATOR_INSTRUMENT]?.I
                if (instrumentId != null) {
                    split.generators.remove(SF2Region.GENERATOR_INSTRUMENT)
                    if (instrumentId < 0 || instrumentId >= layers.size) throw RIFFInvalidDataException("RIFF Invalid Data")
                    split.layer = layers[instrumentId]
                } else {
                    globalSplit = split
                }
            }

            globalSplit?.also {
                instrument.regions.remove(it)
                val gsplit = SF2Region()
                gsplit.generators = it.generators
                gsplit.modulators = it.modulators
                instrument.globalRegion = gsplit
            }
        }

        // TODO: 09/01/2021 Verify all necessary was loaded, else throw an Exception
    }

    // TODO: 02/01/2021 Missing SF2 Writing and editing functions

}