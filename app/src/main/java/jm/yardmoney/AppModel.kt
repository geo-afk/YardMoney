package jm.yardmoney

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jm.yardmoney.data.*
import jm.yardmoney.receipts.ReceiptReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel(application: Application) : AndroidViewModel(application) {
    val app = application as YardMoneyApplication
    val repo by lazy { app.repository }
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val scanProgress = MutableStateFlow<String?>(null)
    private val openRetries =
        kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    val snapshot = flow {
        emitAll(repo.snapshot)
    }
        .retryWhen { cause, _ ->
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            error.value = "Unable to open your data: ${cause.message}"
            openRetries.receive()
            error.value = null
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        act { repo.materializeBills() }
    }

    fun retryOpenData() {
        openRetries.trySend(Unit)
    }

    fun act(done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            var succeeded = false
            try {
                withContext(Dispatchers.IO) { block() }
                succeeded = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value =
                    e.message ?: "This change could not be saved. Please check your entries."
            } finally {
                scanProgress.value = null
                busy.value = false
            }
            if (succeeded) done()
        }
    }

    fun rescanReceipt(draft: ReceiptDraft, done: (String) -> Unit) {
        var id = ""
        act({ done(id) }) {
            try {
                val image = requireNotNull(draft.imageRef) { "The draft has no photo." }
                id =
                    ReceiptReader(app).readBytes(app.storage.readReceipt(image)) {
                        scanProgress.value = it
                    }
                repo.dao.deleteDraft(draft.id)
                app.storage.deleteReceipt(image)
            } finally {
                scanProgress.value = null
            }
        }
    }

    fun importReceipt(uri: Uri, done: (String) -> Unit) {
        var id = ""
        act({ done(id) }) {
            try {
                id = ReceiptReader(app).read(uri) { scanProgress.value = it }
            } finally {
                scanProgress.value = null
                if (uri.authority == "${app.packageName}.files")
                    uri.lastPathSegment
                        ?.takeIf { Regex("receipt-[a-zA-Z0-9-]+\\.jpg").matches(it) }
                        ?.let { java.io.File(app.cacheDir, "exports/$it").delete() }
            }
        }
    }
}
