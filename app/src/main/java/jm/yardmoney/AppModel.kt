package jm.yardmoney

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jm.yardmoney.data.*
import jm.yardmoney.receipts.ReceiptImages
import jm.yardmoney.receipts.ReceiptReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel(application: Application) : AndroidViewModel(application) {
    val app = application as YardMoneyApplication
    // Resolved on every access so a database reopened after recovery is never shadowed.
    val repo: FinanceRepository
        get() = app.repository
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    // One pending confirmation survives a brief collector gap; the visible scaffold consumes it.
    val success = MutableStateFlow<String?>(null)
    val scanProgress = MutableStateFlow<String?>(null)
    private val openRetries = Channel<Unit>(Channel.CONFLATED)
    val snapshot = flow {
        emitAll(repo.snapshot)
    }
        .retryWhen { cause, _ ->
            if (cause is CancellationException) throw cause
            error.value = "Unable to open your data: ${userMessage(cause)}"
            openRetries.receive()
            error.value = null
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        act(successMessage = null) {
            app.storage.removeExpiredTempReceipts()
            repo.materializeBills()
        }
    }

    fun retryOpenData() {
        openRetries.trySend(Unit)
    }

    /**
     * Recovery from the "cannot open your data" screen: forget the unreadable database, erase it
     * and its keys, then open a fresh one so a portable backup can be restored.
     */
    fun startOver() {
        act(done = ::retryOpenData, successMessage = null) {
            app.closeDatabase()
            app.storage.resetAll()
            clearPersonalPreferences()
        }
    }

    /** Personal details kept outside the database: saved shop items and the chosen account view. */
    fun clearPersonalPreferences() {
        app.savedItemsPrefs().edit().clear().commit()
        app.navigationPrefs().edit().clear().commit()
    }

    fun act(done: () -> Unit = {}, successMessage: String? = "Saved", block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            var succeeded = false
            try {
                withContext(Dispatchers.IO) { block() }
                succeeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = userMessage(e)
            } catch (e: OutOfMemoryError) {
                // Large receipt photos can exhaust the heap; report it instead of crashing.
                error.value = userMessage(e)
            } finally {
                scanProgress.value = null
                busy.value = false
            }
            if (succeeded) {
                success.value = successMessage
                done()
            }
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
                ReceiptImages.deleteTemporaryCapture(app, uri)
            }
        }
    }
}
