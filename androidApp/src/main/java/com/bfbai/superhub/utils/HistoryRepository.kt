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
import java.io.File

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
    private var lastTs = 0L

    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    // لتجاهل صدى النسخ اللي بنعمله إحنا (مثلاً نسخ من السجل)
    @Volatile
    var ignoreClipUntil: Long = 0L

    // آخر نص/صورة المستخدم مسحهم: الالتقاط السلبي (زي لمسة المقبض) بيتجاهلهم
    // لحد ما تنسخ حاجة جديدة أو تنسخ نفس العنصر بنفسك
    @Volatile
    var dismissedText: String? = null

    @Volatile
    var dismissedImageHash: String? = null

    // النص التالف (صورة اتقرت كنص) أو الضخم جداً بيعلّق الشاشة، فبنرفضه
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
    private fun nextTs(): Long {
        val t = System.currentTimeMillis()
        lastTs = if (t > lastTs) t else lastTs + 1
        return lastTs
    }

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        PrivacyPolicy.init(context)
        val manager = HistoryManager(context.applicationContext)
        val days = PrivacyPolicy.autoWipeDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L
        scope.launch {
            val all = manager.loadHistory()
                .filterNot { isGarbage(it.text) }
                .filter { it.imagePath == null || File(it.imagePath).exists() }

            // المسح التلقائي للأقدم (لو مفعّل في الخصوصية): المثبّت بيفضل
            val saved = if (cutoff > 0L) {
                val old = all.filter { !it.isPinned && it.timestamp < cutoff }
                for (o in old) {
                    val p = o.imagePath
                    if (p != null) ImageStore.delete(p)
                }
                all.filter { it.isPinned || it.timestamp >= cutoff }
            } else {
                all
            }

            // أي حاجة اتضافت قبل ما التحميل يخلص بتتقدم، والمحفوظ القديم بيتحط بعدها
            _items.update { current ->
                val known = current.map { it.text + "|" + it.timestamp }.toSet()
                current + saved.filter { (it.text + "|" + it.timestamp) !in known }
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

    // لو نفس الصورة (بنفس المحتوى) موجودة، بنطلّعها لفوق ونمسح النسخة الجديدة بدل ما نكررها
    fun addImage(path: String, source: String) {
        scope.launch {
            val h = ImageStore.hashOf(path)
            val existing = if (h != null) {
                _items.value.firstOrNull { it.imagePath != null && it.imageHash == h }
            } else {
                null
            }
            if (existing != null) {
                if (existing.imagePath != path) ImageStore.delete(path)
                val bumped = existing.copy(timestamp = nextTs())
                _items.update { list -> listOf(bumped) + list.filterNot { same(it, existing) } }
            } else {
                val item = ShelfItem(
                    text = "",
                    source = source,
                    timestamp = nextTs(),
                    imagePath = path,
                    imageHash = h
                )
                _items.update { list -> listOf(item) + list }
            }
        }
    }

    fun togglePin(item: ShelfItem) {
        _items.update { list ->
            list.map { if (same(it, item)) it.copy(isPinned = !it.isPinned) else it }
        }
    }

    fun remove(item: ShelfItem) {
        if (item.imagePath == null) dismissedText = item.text else dismissedImageHash = item.imageHash
        _items.update { list -> list.filterNot { same(it, item) } }
        val p = item.imagePath
        if (p != null) scope.launch { ImageStore.delete(p) }
    }

    fun clearUnpinned() {
        val gone = _items.value.filter { !it.isPinned }
        val firstText = gone.firstOrNull { it.imagePath == null }
        val firstImage = gone.firstOrNull { it.imagePath != null }
        if (firstText != null) dismissedText = firstText.text
        if (firstImage != null) dismissedImageHash = firstImage.imageHash
        _items.update { list -> list.filter { it.isPinned } }
        scope.launch {
            for (g in gone) {
                val p = g.imagePath
                if (p != null) ImageStore.delete(p)
            }
        }
    }

    // مسح الأقدم من عدد أيام معيّن (المثبّت بيفضل)
    fun cleanOlderThan(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val gone = _items.value.filter { !it.isPinned && it.timestamp < cutoff }
        if (gone.isEmpty()) return
        _items.update { list -> list.filter { it.isPinned || it.timestamp >= cutoff } }
        scope.launch {
            for (g in gone) {
                val p = g.imagePath
                if (p != null) ImageStore.delete(p)
            }
        }
    }
}
