// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import com.bfbai.superhub.service.ShelfItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * مصدر واحد للحقيقة: الرف وشاشة السجل بيقروا ويكتبوا من هنا بس.
 * الحفظ في الملف بيتم من مكان واحد، بعد ما التغييرات تهدى 300ms.
 */
object HistoryRepository {

    private const val MAX_TEXT_LENGTH = 100_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private var started = false

    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    // لتجاهل صدى النسخ اللي بنعمله إحنا (مثلاً نسخ من السجل)
    @Volatile
    var ignoreClipUntil: Long = 0L

    /**
     * النص التالف (صورة اتقرت كنص) أو الضخم جداً بيعلّق الشاشة،
     * فبنرفضه وبننضّف أي حاجة منه كانت اتحفظت قبل كده.
     */
    fun isGarbage(text: String): Boolean {
        if (text.length > MAX_TEXT_LENGTH) return true
        val n = minOf(text.length, 2000)
        if (n == 0) return false
        var bad = 0
        for (i in 0 until n) {
            val c = text[i]
            if (c == '\uFFFD' || (c < ' ' && c != '\n' && c != '\r' && c != '\t')) bad++
        }
        return bad * 100 > n * 2
    }

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        val manager = HistoryManager(context.applicationContext)
        scope.launch {
            val saved = manager.loadHistory().filterNot { isGarbage(it.text) }
            // أي حاجة اتضافت قبل ما التحميل يخلص بتتقدم، والمحفوظ القديم بيتحط بعدها
            _items.update { current ->
                val known = current.map { it.text }.toSet()
                current + saved.filter { it.text !in known }
            }
            _loaded.value = true
            _items.collectLatest { list ->
                delay(300)
                manager.saveHistory(list)
            }
        }
    }

    private fun same(a: ShelfItem, b: ShelfItem): Boolean =
        a.text == b.text && a.timestamp == b.timestamp

    fun add(text: String, source: String) {
        val t = text.trim()
        if (t.isEmpty() || isGarbage(t)) return
        _items.update { list ->
            val old = list.firstOrNull { it.text == t }
            listOf(ShelfItem(text = t, source = source, isPinned = old?.isPinned ?: false)) +
                list.filterNot { it.text == t }
        }
    }

    fun togglePin(item: ShelfItem) {
        _items.update { list ->
            list.map { if (same(it, item)) it.copy(isPinned = !it.isPinned) else it }
        }
    }

    fun remove(item: ShelfItem) {
        _items.update { list -> list.filterNot { same(it, item) } }
    }

    fun clearUnpinned() {
        _items.update { list -> list.filter { it.isPinned } }
    }
}
