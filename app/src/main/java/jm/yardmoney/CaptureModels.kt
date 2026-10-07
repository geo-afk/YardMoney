package jm.yardmoney

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

internal data class SharedCapture(val type: String, val text: String? = null, val uri: Uri? = null)

internal fun sharedCapture(intent: Intent): SharedCapture? {
    if (intent.action != Intent.ACTION_SEND) return null
    return when {
        intent.type == "text/plain" -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let {
            require(it.length <= 10000) { "Share alert text of up to 10,000 characters." }
            SharedCapture("text", text = it)
        }
        intent.type == "text/csv" || intent.type?.startsWith("image/") == true -> {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            require(uri?.scheme == "content") { "Share a readable photo or CSV file from another app." }
            SharedCapture(if (intent.type == "text/csv") "csv" else "image", uri = uri)
        }
        else -> null
    }
}
