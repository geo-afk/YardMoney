package jm.yardmoney.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import jm.yardmoney.core.*

/** File contents stay in memory only, across rotation and lock/unlock. No raw file is saved. */
internal class StatementImportViewModel : ViewModel() {
    var document by mutableStateOf<StatementDocument?>(null)
    var sourceUri by mutableStateOf<String?>(null)
    var account by mutableStateOf("")
    var mapping by mutableStateOf(StatementMapping())
    var stage by mutableStateOf("mapping")
    var rows by mutableStateOf<List<StatementRow>>(emptyList())
    var selected by mutableStateOf<Set<Int>>(emptySet())
    var loading by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var allowDuplicates by mutableStateOf(false)
    var batch by mutableStateOf<String?>(null)
    var imported by mutableIntStateOf(0)
    var progress by mutableStateOf(0 to 0)
    fun clear() {
        document = null; sourceUri = null; account = ""; mapping = StatementMapping()
        stage = "mapping"; rows = emptyList(); selected = emptySet(); loading = false
        notice = null; allowDuplicates = false; batch = null; imported = 0; progress = 0 to 0
    }
}
