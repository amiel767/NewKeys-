package com.soundstage.mixer.model

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance DJ-style gapless looping audio engine.
 *
 * Architecture inspired by professional DJ software (Traktor, Pioneer DJ, djay Pro):
 * 1. Audio is decoded into 16-bit uncompressed PCM in memory.
 * 2. An AudioTrack in MODE_STREAM writes continuous PCM frames in a high-priority audio thread.
 * 3. When the playback head reaches endFrame, it immediately resets to startFrame in the same
 *    stream buffer without any pause, seek, or re-buffering.
 * 4. Yields 0.0ms delay and sample-accurate gapless repetition.
 */
class DjLoopEngine(private val context: Context) {

    companion object {
        private const val TAG = "DjLoopEngine"
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val CHANNELS = 2 // Stereo
    }

    private data class DecodedAudio(
        val pcm: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Int
    )

    // Cache decoded PCM loops in memory to allow instant starts
    private val loopPcmCache = ConcurrentHashMap<String, DecodedAudio>()

    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null

    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false

    @Volatile private var currentFilePath: String = ""
    @Volatile private var startFrame: Int = 0
    @Volatile private var endFrame: Int = 0
    @Volatile private var totalFrames: Int = 0
    @Volatile private var loopVolume: Float = 0.75f

    @Volatile private var currentPcm: ShortArray? = null
    @Volatile private var rawDecodedPcm: ShortArray? = null
    @Volatile private var currentSampleRate: Int = DEFAULT_SAMPLE_RATE
    @Volatile private var currentChannels: Int = CHANNELS

    @Volatile private var activeBeatCount: Int = 0
    @Volatile private var activeBpm: Int = 120
    @Volatile private var baseFileBpm: Int = 120
    @Volatile private var semitonePitchShift: Int = 0

    fun playLoop(
        filePath: String,
        volume: Float = 0.75f,
        beatCount: Int = 0,
        bpm: Int = 120,
        startMs: Int = 0,
        endMs: Int = 0,
        pitchShiftSemitones: Int = 0
    ) {
        loopVolume = volume.coerceIn(0f, 1f)
        activeBeatCount = beatCount
        activeBpm = bpm.coerceAtLeast(30)
        baseFileBpm = if (bpm > 0) bpm else 120
        semitonePitchShift = pitchShiftSemitones

        // If currently playing the exact same file, update trims / pitch dynamically
        if (isPlaying && currentFilePath == filePath && rawDecodedPcm != null) {
            applyTimeStretchAndPitch(bpm, pitchShiftSemitones)
            updateTrimPoints(startMs, endMs, beatCount, bpm)
            audioTrack?.setVolume(loopVolume)
            return
        }

        stop()

        Thread {
            try {
                val decoded = getOrDecodeAudio(filePath) ?: return@Thread
                currentFilePath = filePath
                rawDecodedPcm = decoded.pcm
                currentSampleRate = decoded.sampleRate
                currentChannels = decoded.channels

                applyTimeStretchAndPitch(bpm, pitchShiftSemitones)

                startAudioStream()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting DJ loop: ${e.message}")
            }
        }.start()
    }

    private fun applyTimeStretchAndPitch(targetBpm: Int, semitones: Int) {
        val raw = rawDecodedPcm ?: return
        val ratio = if (baseFileBpm > 0 && targetBpm > 0) {
            targetBpm.toFloat() / baseFileBpm.toFloat()
        } else 1.0f

        val processed = com.soundstage.mixer.audio.TimeStretchPitchShifter.process(
            inputPCM = raw,
            channels = currentChannels,
            sampleRate = currentSampleRate,
            tempoRatio = ratio,
            semitonePitchOffset = semitones
        )

        currentPcm = processed
        totalFrames = processed.size / currentChannels
        computeFrames(0, 0, activeBeatCount, targetBpm)
    }

    fun setPitchShift(semitones: Int) {
        semitonePitchShift = semitones.coerceIn(-12, 12)
        if (isPlaying && rawDecodedPcm != null) {
            applyTimeStretchAndPitch(activeBpm, semitonePitchShift)
        }
    }

    private fun computeFrames(startMs: Int, endMs: Int, beatCount: Int, bpm: Int) {
        val sr = currentSampleRate
        val tf = totalFrames
        if (tf <= 0) return

        val sF = if (startMs > 0) {
            ((startMs.toLong() * sr) / 1000L).toInt().coerceIn(0, tf - 100)
        } else {
            val pcm = currentPcm
            if (pcm != null && beatCount <= 0 && endMs <= 0) {
                com.soundstage.mixer.audio.SeamlessLoopTrimmer.autoTrimSilenceShorts(pcm, currentChannels).startIndex
            } else 0
        }

        val beatDurationFrames = if (beatCount > 0 && bpm > 0) {
            ((beatCount.toLong() * 60L * sr) / bpm.toLong()).toInt()
        } else 0

        val eF = when {
            endMs > startMs -> ((endMs.toLong() * sr) / 1000L).toInt().coerceIn(sF + 100, tf)
            beatDurationFrames > 0 -> (sF + beatDurationFrames).coerceIn(sF + 100, tf)
            else -> {
                val pcm = currentPcm
                if (pcm != null && startMs <= 0) {
                    com.soundstage.mixer.audio.SeamlessLoopTrimmer.autoTrimSilenceShorts(pcm, currentChannels).endIndex.coerceIn(sF + 100, tf)
                } else tf
            }
        }

        startFrame = sF
        endFrame = eF
    }

    fun updateTrims(startMs: Int, endMs: Int) {
        if (currentPcm == null || totalFrames <= 0) return
        computeFrames(startMs, endMs, activeBeatCount, activeBpm)
    }

    fun setBeats(beatCount: Int, bpm: Int) {
        activeBeatCount = beatCount
        activeBpm = bpm.coerceAtLeast(30)
        if (currentPcm == null || totalFrames <= 0) return
        computeFrames(0, 0, beatCount, bpm)
    }

    fun setVolume(volume: Float) {
        loopVolume = volume.coerceIn(0f, 1f)
        try {
            audioTrack?.setVolume(loopVolume)
        } catch (_: Exception) {}
    }

    private fun updateTrimPoints(startMs: Int, endMs: Int, beatCount: Int, bpm: Int) {
        computeFrames(startMs, endMs, beatCount, bpm)
    }

    private fun startAudioStream() {
        val pcm = currentPcm ?: return
        val sr = currentSampleRate
        val ch = currentChannels

        val channelConfig = if (ch == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(sr, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = max(minBuf * 2, 4096)

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sr)
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        track.setVolume(loopVolume)
        track.play()
        audioTrack = track
        isPlaying = true
        isPaused = false

        playThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunkFrames = 512
            val chunkSamples = chunkFrames * ch
            val outShorts = ShortArray(chunkSamples)

            var playhead = startFrame

            while (isPlaying) {
                if (isPaused) {
                    try {
                        Thread.sleep(15)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                val curStart = startFrame
                val curEnd = endFrame.coerceAtLeast(curStart + 64).coerceAtMost(pcm.size / ch)

                if (playhead < curStart || playhead >= curEnd) {
                    playhead = curStart
                }

                val framesAvailable = curEnd - playhead
                val framesToWrite = min(chunkFrames, framesAvailable)

                if (framesToWrite <= 0) {
                    playhead = curStart
                    continue
                }

                // Use SeamlessLoopTrimmer for zero-click 10ms micro-crossfade playback
                val playheadRef = intArrayOf(playhead)
                com.soundstage.mixer.audio.SeamlessLoopTrimmer.renderSeamlessStereoShorts(
                    output = outShorts,
                    pcm = pcm,
                    startFrame = curStart,
                    endFrame = curEnd,
                    channels = ch,
                    sampleRate = sr,
                    playheadRef = playheadRef,
                    framesToRender = framesToWrite
                )
                playhead = playheadRef[0]

                val samplesToWrite = framesToWrite * ch
                val written = track.write(outShorts, 0, samplesToWrite)
                if (written < 0) {
                    break
                }
            }
        }, "DjLoopAudioStreamThread").apply { start() }
    }

    fun pause() {
        isPaused = true
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        isPaused = false
        try {
            audioTrack?.play()
        } catch (_: Exception) {}
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        try {
            playThread?.interrupt()
            playThread = null
        } catch (_: Exception) {}

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        currentFilePath = ""
    }

    fun isPlaying(): Boolean = isPlaying && !isPaused

    // ------------------------------------------------------------------------
    // AUDIO DECODING PIPELINE (WAV direct PCM + MediaCodec for MP3/M4A/AAC)
    // ------------------------------------------------------------------------
    private fun getOrDecodeAudio(filePath: String): DecodedAudio? {
        val cached = loopPcmCache[filePath]
        if (cached != null) return cached

        val file = File(filePath)
        if (!file.exists()) return null

        // 1. Check for standard WAV
        if (filePath.endsWith(".wav", ignoreCase = true)) {
            val wav = decodeWav(file)
            if (wav != null) {
                loopPcmCache[filePath] = wav
                return wav
            }
        }

        // 2. Decode MP3/M4A/etc via MediaExtractor + MediaCodec
        val decoded = decodeWithMediaCodec(file)
        if (decoded != null) {
            loopPcmCache[filePath] = decoded
        }
        return decoded
    }

    private fun decodeWav(file: File): DecodedAudio? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) < 44) return null

                // Check "RIFF" and "WAVE"
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                if (riff != "RIFF" || wave != "WAVE") return null

                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt()
                val sampleRate = bb.getInt(24)
                val bitsPerSample = bb.getShort(34).toInt()

                if (bitsPerSample != 16 || channels !in 1..2) {
                    // Fall back to MediaCodec for non-16-bit or unusual channels
                    return null
                }

                val dataLen = (file.length() - 44).toInt()
                val pcmShorts = ShortArray(dataLen / 2)
                val byteBuf = ByteArray(4096)
                var shortIdx = 0

                val sbb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)

                var read: Int
                while (fis.read(byteBuf).also { read = it } > 0) {
                    for (i in 0 until read - 1 step 2) {
                        if (shortIdx >= pcmShorts.size) break
                        val s = ((byteBuf[i + 1].toInt() shl 8) or (byteBuf[i].toInt() and 0xFF)).toShort()
                        pcmShorts[shortIdx++] = s
                    }
                }

                // If mono, convert to stereo for consistent mixer output
                val stereoPcm = if (channels == 1) {
                    val st = ShortArray(shortIdx * 2)
                    for (i in 0 until shortIdx) {
                        val sample = pcmShorts[i]
                        st[i * 2] = sample
                        st[i * 2 + 1] = sample
                    }
                    st
                } else {
                    pcmShorts.copyOf(shortIdx)
                }

                val durationMs = ((stereoPcm.size / 2) * 1000L / sampleRate).toInt()
                return DecodedAudio(stereoPcm, sampleRate, 2, durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct WAV parse failed, fallback to MediaCodec: ${e.message}")
            return null
        }
    }

    private fun decodeWithMediaCodec(file: File): DecodedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else DEFAULT_SAMPLE_RATE
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmChunks = ArrayList<ShortArray>()
            var totalShorts = 0

            val info = MediaCodec.BufferInfo()
            var isEos = false

            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(info, 5000)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && info.size > 0) {
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        val shortCount = info.size / 2
                        val shorts = ShortArray(shortCount)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        pcmChunks.add(shorts)
                        totalShorts += shortCount
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(info, 1000)
                }
            }

            if (totalShorts == 0) return null

            // Merge chunks into single contiguous ShortArray
            val fullPcm = ShortArray(totalShorts)
            var destPos = 0
            for (chunk in pcmChunks) {
                System.arraycopy(chunk, 0, fullPcm, destPos, chunk.size)
                destPos += chunk.size
            }

            // Convert to stereo if mono
            val finalStereo = if (channels == 1) {
                val st = ShortArray(totalShorts * 2)
                for (i in 0 until totalShorts) {
                    val s = fullPcm[i]
                    st[i * 2] = s
                    st[i * 2 + 1] = s
                }
                st
            } else {
                fullPcm
            }

            val durationMs = ((finalStereo.size / 2) * 1000L / sampleRate).toInt()
            return DecodedAudio(finalStereo, sampleRate, 2, durationMs)

        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decoding failed: ${e.message}")
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }
}
