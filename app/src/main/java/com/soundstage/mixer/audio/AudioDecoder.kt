package com.soundstage.mixer.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, off-audio-thread audio decoder.
 * Decodes WAV, MP3, AAC, OGG, FLAC into 48kHz Interleaved Stereo Float32 PCM.
 * Strictly executed on Dispatchers.IO (never inside Oboe onAudioReady).
 */
object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 48000

    data class DecodedAudio(
        val pcmData: FloatArray,
        val totalFrames: Int,
        val sampleRate: Int = TARGET_SAMPLE_RATE,
        val channels: Int = 2
    )

    suspend fun decodeAudioFile(file: File): DecodedAudio? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "File not readable: ${file.absolutePath}")
            return@withContext null
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in: ${file.name}")
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val inputChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val rawPcmList = ArrayList<ShortArray>()
            var totalShorts = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            val timeoutUs = 5000L

            while (!isEOS) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val shorts = ShortArray(shortBuf.remaining())
                        shortBuf.get(shorts)
                        rawPcmList.add(shorts)
                        totalShorts += shorts.size
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            // Consolidate into single ShortArray
            val allShorts = ShortArray(totalShorts)
            var destPos = 0
            for (chunk in rawPcmList) {
                System.arraycopy(chunk, 0, allShorts, destPos, chunk.size)
                destPos += chunk.size
            }

            val inputFrames = totalShorts / inputChannels
            if (inputFrames <= 0) return@withContext null

            // Resample & convert to Interleaved Stereo Float32 at 48000 Hz
            val resampleRatio = TARGET_SAMPLE_RATE.toDouble() / inputSampleRate.toDouble()
            val targetFrames = (inputFrames * resampleRatio).toInt().coerceAtLeast(1)
            val outputFloatPcm = FloatArray(targetFrames * 2)

            for (f in 0 until targetFrames) {
                val srcFrameD = f / resampleRatio
                val srcFrameIdx = srcFrameD.toInt().coerceIn(0, inputFrames - 1)
                val frac = (srcFrameD - srcFrameIdx).toFloat()
                val nextFrameIdx = (srcFrameIdx + 1).coerceIn(0, inputFrames - 1)

                val left1 = allShorts[srcFrameIdx * inputChannels] / 32768.0f
                val right1 = if (inputChannels > 1) allShorts[srcFrameIdx * inputChannels + 1] / 32768.0f else left1

                val left2 = allShorts[nextFrameIdx * inputChannels] / 32768.0f
                val right2 = if (inputChannels > 1) allShorts[nextFrameIdx * inputChannels + 1] / 32768.0f else left2

                outputFloatPcm[f * 2] = left1 + frac * (left2 - left1)
                outputFloatPcm[f * 2 + 1] = right1 + frac * (right2 - right1)
            }

            Log.i(TAG, "Successfully decoded ${file.name}: $inputSampleRate Hz ($inputChannels ch) -> $TARGET_SAMPLE_RATE Hz Stereo ($targetFrames frames)")
            DecodedAudio(
                pcmData = outputFloatPcm,
                totalFrames = targetFrames,
                sampleRate = TARGET_SAMPLE_RATE,
                channels = 2
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed decoding audio file ${file.name}: ${e.message}", e)
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (_: Exception) {}
        }
    }
}
