package app.d6d.android

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class LimitedInputCopyTest {

    @Test
    fun `an input exactly at the limit is copied`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val destination = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(bytes).copyToLimited(destination, bytes.size.toLong(), 2)

        assertEquals(bytes.size.toLong(), copied)
        assertArrayEquals(bytes, destination.toByteArray())
    }

    @Test
    fun `an input larger than the limit is rejected before writing past it`() {
        val destination = ByteArrayOutputStream()

        assertThrows(InputSizeLimitExceededException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
                .copyToLimited(destination, 4, 2)
        }

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), destination.toByteArray())
    }

    @Test
    fun `a provider returning zero once cannot stall the copy`() {
        val bytes = byteArrayOf(7, 8, 9)
        val source = object : InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            private var first = true

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (first) {
                    first = false
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }

            override fun read(): Int = delegate.read()
        }
        val destination = ByteArrayOutputStream()

        source.copyToLimited(destination, 3, 2)

        assertArrayEquals(bytes, destination.toByteArray())
    }
}
