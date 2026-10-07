package jm.yardmoney.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.mapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun StatementImportSheet(model: AppModel, data: jm.yardmoney.data.FinanceSnapshot,
    initialUri: String?, busy: Boolean, close: () -> Unit) {
    val state: StatementImportViewModel = viewModel(key = "statement-import")
    val scope = rememberCoroutineScope()
    fun read(uri: Uri) {
        state.clear(); state.sourceUri = uri.toString(); state.loading = true
        scope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    model.app.contentResolver.openInputStream(uri)?.use { StatementCsv.read(it) }
                        ?: error("Choose a readable CSV file.")
                }
                state.document = document
                state.mapping = StatementCsv.mapping(document)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.notice = jm.yardmoney.userMessage(e) }
            catch (e: OutOfMemoryError) { state.notice = jm.yardmoney.userMessage(e) }
            finally { state.loading = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) read(uri) }
    LaunchedEffect(initialUri) {
        if (initialUri != null && state.sourceUri != initialUri) read(Uri.parse(initialUri))
    }
    fun finish() { state.clear(); close() }
    val doc = state.document
    StagedEditSheet("Import transactions", state.stage, busy || state.loading,
        doc != null && state.stage != "done", ::finish, scrollContent = state.stage != "preview",
        actions = {
            if (state.stage == "preview") Button(
                enabled = !busy && state.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
                onClick = {
                    val rows = state.rows.filter { it.index in state.selected }
                    val mapping = state.mapping
                    val account = state.account
                    var batch = ""
                    var imported = 0
                    model.act(done = { state.imported = imported; state.batch = batch; state.stage = "done" }, successMessage = "Statement imported") {
                        batch = StatementCsv.batchId(account, mapping, requireNotNull(doc))
                        imported = model.repo.importStatement(batch, account, mapping, rows, state.allowDuplicates) { current, total ->
                            state.progress = current to total
                        }
                    }
                }) { Text(if (busy) "Importing ${state.progress.first} / ${state.progress.second}" else "Import ${state.selected.size} selected rows") }
        }) {
        if (state.loading) { LoadingBudget(); Text("Reading statement…") }
        else if (state.stage == "preview") StatementPreviewContent(state.rows, state.selected, state.allowDuplicates, busy,
            state.notice, onToggle = { index -> state.selected = if (index in state.selected) state.selected - index else state.selected + index },
            onDuplicates = { allow ->
                state.allowDuplicates = allow
                if (!allow) state.selected = state.selected - state.rows.filter { it.duplicate }.map { it.index }.toSet()
            }, onBack = { state.stage = "mapping" })
        else if (state.stage == "done") {
            Text("${state.imported} records imported", style = MaterialTheme.typography.headlineSmall)
            Text("A repeated import of the same file and mapping adds no records. Your original statement was not saved.")
            state.batch?.let { batch -> OutlinedButton(enabled = !busy, onClick = {
                model.act(done = { state.batch = null; state.notice = "Import undone." }, successMessage = "Import undone") { model.repo.undoStatementImport(batch) }
            }) { Text("Undo import") } }
            state.notice?.let { Text(it) }
            Button(onClick = ::finish) { Text("Done") }
        } else {
            Text("Review a statement before adding money records. Amounts below zero are spending; amounts above zero are income.")
            OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("text/csv", "text/plain", "application/csv", "application/vnd.ms-excel")) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose CSV file") }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (doc != null) {
                val accounts = data.ledger.accounts.map { IdentityOption(it.account.id, it.account.name, identity = accountIdentity(it.account)) }
                IdentityPicker("Target account", state.account, accounts) { account ->
                    state.account = account
                    data.imports.mappings.find { it.accountId == account }?.let { state.mapping = it.mapping() }
                }
                StatementMappingContent(doc, state.mapping, !busy, { state.mapping = it })
                Button(enabled = !busy && state.account.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                    scope.launch {
                        state.loading = true
                        try {
                            val account = state.account
                            val mapping = state.mapping
                            val transactions = data.ledger.transactions.associateBy { it.id }
                            val existing = data.accountEntries.mapNotNull { entry ->
                                transactions[entry.transactionId]?.takeIf { it.kind in listOf("EXPENSE", "INCOME") }?.let {
                                    StatementExisting(entry.accountId, java.time.LocalDate.parse(it.date), entry.signedMinor, it.description)
                                }
                            }
                            val rows = withContext(Dispatchers.Default) { StatementCsv.preview(doc, mapping, account, existing, model.repo.today) }
                            state.rows = rows
                            state.selected = rows.filter { it.issues.isEmpty() && !it.duplicate }.map { it.index }.toSet()
                            state.notice = null; state.stage = "preview"
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { state.notice = jm.yardmoney.userMessage(e) }
                        finally { state.loading = false }
                    }
                }) { Text("Preview rows") }
            }
            if (data.imports.batches.isNotEmpty()) MoneyEntrySection("Previous imports") {
                data.imports.batches.take(10).forEach { batch ->
                    MoneyCard {
                        Text("${data.ledger.accounts.find { it.account.id == batch.accountId }?.account?.name ?: "Not specified"} · ${batch.rowCount} reviewed rows")
                        Text(java.time.Instant.ofEpochMilli(batch.createdAt).atZone(java.time.ZoneId.of("America/Jamaica")).toLocalDate().toString())
                        TextButton(enabled = !busy, onClick = {
                            model.act(successMessage = "Import undone") { model.repo.undoStatementImport(batch.id) }
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Undo import") }
                    }
                }
            }
        }
    }
}
