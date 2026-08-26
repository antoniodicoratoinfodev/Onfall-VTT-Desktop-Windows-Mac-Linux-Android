package app.d6d.android

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Raised as soon as an input stream exceeds its allowed byte budget. */
internal class InputSizeLimitExceededException(maxBytes: Long) :
    IOException("Input exceeds the supported limit of $maxBytes bytes")

/**
 * Copies at most [maxBytes], probing one byte past the boundary so a stream
 * whose size is unavailable (as with many Android content providers) cannot
 * fill the cache before the normal image validation runs.
 */
internal fun InputStream.copyToLimited(
    destination: OutputStream,
    maxBytes: Long,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
): Long {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    require(bufferSize > 0) { "bufferSize must be positive" }

    val buffer = ByteArray(bufferSize)
    var copied = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return copied
        if (count == 0) {
            // InputStream discourages zero-length reads with a non-empty buffer,
            // but a remote ContentProvider is outside our control. Probe one byte
            // so a broken implementation cannot leave this worker spinning.
            val byte = read()
            if (byte < 0) return copied
            if (copied == maxBytes) throw InputSizeLimitExceededException(maxBytes)
            destination.write(byte)
            copied++
            continue
        }
        if (count.toLong() > maxBytes - copied) {
            throw InputSizeLimitExceededException(maxBytes)
        }
        destination.write(buffer, 0, count)
        copied += count
    }
}
