package jm.yardmoney.receipts

import android.net.Uri
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.data.FinanceRepository
import kotlin.math.*

/**
 * Bounded overlapping tiles preserve small text on long receipts. Never posts a financial record.
 */
class ReceiptReader(private val app: YardMoneyApplication) {
    suspend fun read(uri: Uri, progress: (String) -> Unit = {}): String {
        progress("Reading photo…")
        val bytes = ReceiptImages.read(app, uri)
        return readBytes(bytes, progress)
    }

    suspend fun readBytes(bytes: ByteArray, progress: (String) -> Unit = {}): String {
        val bitmap = ReceiptImages.decode(bytes)
        val annotated =
            try {
                ReceiptRecognizer().recognize(bitmap, progress)
            } finally {
                bitmap.recycle()
            }
        progress("Saving private review draft…")
        val image = app.storage.saveReceipt(FinanceRepository.id(), bytes)
        return try {
            app.repository.saveDraft(annotated, image, FinanceRepository.fingerprint(bytes))
        } catch (e: Exception) {
            app.storage.deleteReceipt(image)
            throw e
        }
    }
}
