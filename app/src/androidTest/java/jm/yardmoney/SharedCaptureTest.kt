package jm.yardmoney

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class SharedCaptureTest {
    @Test fun pendingIntentCannotBeConsumedWhileLockedAndSurvivesRetry() {
        val session = LockSession()
        session.locked = true
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Payment J$600 at Taxi today")
        session.stageCapture(intent)
        val held = session.pendingCapture!!
        session.consumeCapture(held)
        assertSame(held, session.pendingCapture)
        session.autoPrompt = false
        assertSame(held, session.pendingCapture)
        session.locked = false
        assertEquals("text", sharedCapture(held)?.type)
        session.consumeCapture(held)
        assertNull(session.pendingCapture)
    }
    @Test fun supportedContentRoutesNeverTrustAFilePathOrNetworkUrl() {
        listOf("image/png" to "image", "text/csv" to "csv").forEach { (mime, route) ->
            val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, Uri.parse("content://synthetic.example/1"))
            assertEquals(route, sharedCapture(intent)?.type)
            listOf("file:///private/file", "https://example.invalid/photo").forEach { value ->
                assertThrows(IllegalArgumentException::class.java) {
                    sharedCapture(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, Uri.parse(value)))
                }
            }
        }
        assertNull(sharedCapture(Intent(Intent.ACTION_MAIN)))
        assertNull(sharedCapture(Intent(Intent.ACTION_SEND).setType("application/pdf")))
    }
}
