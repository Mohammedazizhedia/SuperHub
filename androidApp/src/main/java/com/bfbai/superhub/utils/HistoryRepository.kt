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

    // مجموعة يدوية شغالة: أي نسخ جديد بيدخلها لحد ما تتقفل
    data class ManualGroup(val id: String, val name: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _manual = MutableStateFlow<ManualGroup?>(null)
    private var started = false
    private var lastTs = 0L
    private var groupSeq = 0L

    @Volatile
    private var manualLastAt = 0L

    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    val manualGroup: StateFlow<ManualGroup?> = _manual.asStateFlow()

    // لتجاهل صدى النسخ اللي بنعمله إحنا (مثلاً نسخ من السجل)
    @Volatile
    var ignoreClipUntil: Long = 0L

    // آخر نص/صورة المستخدم مسحهم: الالتقاط السلبي (زي لمسة المقبض) بيتجاهلهم
    // لحد ما تنسخ حاجة جديدة أو تنسخ نفس العنصر بنفسك
    @Volatile
    var dismissedText: String? = null

    @Volatile
    var dismissedImageHash: String? = null

    // اسم ملف كود: كلمة بامتداد معروف (kt, xml, json...)
    private val fileRe = Regex(
        """^[\w@\-./\\]*[\w\-]+\.(kt|kts|java|xml|json|gradle|md|txt|py|js|ts|tsx|jsx|html|css|yml|yaml|properties|sh|cpp|c|h|swift|dart|php|sql|toml|cfg)$""",
        RegexOption.IGNORE_CASE
    )

    fun fileNameOf(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 200 || t.contains('\n')) return null
        if (!fileRe.matches(t)) return null
        return t.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun isFileLike(text: String): Boolean = fileNameOf(text) != null

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
    private fun newGroupId(): String {
        groupSeq++
        return "g" + System.currentTimeMillis() + "_" + groupSeq
    }

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        ShelfBehavior.init(context)
        PrivacyPolicy.init(context)
        val manager = HistoryManager(context.applicationContext)
        val days = PrivacyPolicy.autoWipeDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L
        scope.launch {
            val all = manager.loadHistory()
                .filterNot { isGarbage(it.text) }
                .filter { it.imagePath == null || File(it.imagePath).exists() }

            // المسح التلقائي للأقدم (لو مفعّل في الخصوصية): المثبّت بيفضل
            val kept = if (cutoff > 0L) {
                val old = all.filter { !it.isPinned && it.timestamp < cutoff }
                for (o in old) {
                    val p = o.imagePath
                    if (p != null) ImageStore.delete(p)
                }
                all.filter { it.isPinned || it.timestamp >= cutoff }
            } else {
                all
            }
            val saved = normalize(kept)

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

    // أي مجموعة فيها أقل من عنصرين بتتفك (إلا المجموعة اليدوية الشغالة)
    private fun normalize(list: List<ShelfItem>): List<ShelfItem> {
        val keep = _manual.value?.id
        val counts = HashMap<String, Int>()
        for (i in list) {
            val g = i.groupId
            if (g != null) counts[g] = (counts[g] ?: 0) + 1
        }
        var changed = false
        val out = list.map { i ->
            val g = i.groupId
            if (g != null && g != keep && (counts[g] ?: 0) < 2) {
                changed = true
                i.copy(groupId = null, groupName = null)
            } else {
                i
            }
        }
        return if (changed) out else list
    }

    // بيحط العنصر الجديد فوق، وبيقرر هل يدخل مجموعة (يدوية أو تلقائية)
    private fun placeNew(list: List<ShelfItem>, item: ShelfItem): List<ShelfItem> {
        val manual = _manual.value
        if (manual != null) {
            val idle = ShelfBehavior.manualIdleMin * 60_000L
            if (idle > 0L && item.timestamp - manualLastAt > idle) {
                _manual.value = null
            } else {
                manualLastAt = item.timestamp
                return listOf(item.copy(groupId = manual.id, groupName = manual.name)) + list
            }
        }

        if (!ShelfBehavior.autoGroup) return listOf(item) + list
        val last = list.firstOrNull() ?: return listOf(item)

        if (ShelfBehavior.groupSameApp && last.source != item.source) return listOf(item) + list

        var window = ShelfBehavior.groupWindowSec * 1000L
        if (ShelfBehavior.groupCodeFile) {
            val g0 = last.groupId
            val hasFile = isFileLike(last.text) || isFileLike(item.text) ||
                (g0 != null && list.any { it.groupId == g0 && isFileLike(it.text) })
            if (hasFile) window = maxOf(window, 300_000L)
        }
        if (item.timestamp - last.timestamp > window) return listOf(item) + list

        val gid = last.groupId ?: newGroupId()
        val joined = item.copy(groupId = gid, groupName = last.groupName)
        val rest = if (last.groupId == null) {
            list.map { if (it === last) it.copy(groupId = gid) else it }
        } else {
            list
        }
        return listOf(joined) + rest
    }

    fun add(text: String, source: String) {
        val t = text.trim()
        if (t.isEmpty() || isGarbage(t)) return
        _items.update { list ->
            val old = list.firstOrNull { it.imagePath == null && it.text == t }
            val rest = if (old != null) list.filterNot { it === old } else list
            val item = ShelfItem(
                text = t,
                source = source,
                timestamp = nextTs(),
                isPinned = old?.isPinned ?: false
            )
            normalize(placeNew(rest, item))
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
                _items.update { list ->
                    val rest = list.filterNot { same(it, existing) }
                    val bumped = existing.copy(
                        timestamp = nextTs(),
                        groupId = null,
                        groupName = null,
                        pastedAt = 0L
                    )
                    normalize(placeNew(rest, bumped))
                }
            } else {
                _items.update { list ->
                    val item = ShelfItem(
                        text = "",
                        source = source,
                        timestamp = nextTs(),
                        imagePath = path,
                        imageHash = h
                    )
                    normalize(placeNew(list, item))
                }
            }
        }
    }

    fun togglePin(item: ShelfItem) {
        _items.update { list ->
            list.map { if (same(it, item)) it.copy(isPinned = !it.isPinned) else it }
        }
    }

    fun setPinned(targets: List<ShelfItem>, pinned: Boolean) {
        _items.update { list ->
            list.map { i -> if (targets.any { same(i, it) }) i.copy(isPinned = pinned) else i }
        }
    }

    // علامة "اتلصق": ما بتغيّرش الترتيب
    fun markPasted(item: ShelfItem, pasted: Boolean) {
        val t = if (pasted) System.currentTimeMillis() else 0L
        _items.update { list ->
            list.map { if (same(it, item)) it.copy(pastedAt = t) else it }
        }
    }

    fun remove(item: ShelfItem) {
        if (item.imagePath == null) dismissedText = item.text else dismissedImageHash = item.imageHash
        _items.update { list -> normalize(list.filterNot { same(it, item) }) }
        val p = item.imagePath
        if (p != null) scope.launch { ImageStore.delete(p) }
    }

    fun clearUnpinned() {
        val gone = _items.value.filter { !it.isPinned }
        val firstText = gone.firstOrNull { it.imagePath == null }
        val firstImage = gone.firstOrNull { it.imagePath != null }
        if (firstText != null) dismissedText = firstText.text
        if (firstImage != null) dismissedImageHash = firstImage.imageHash
        _items.update { list -> normalize(list.filter { it.isPinned }) }
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
        _items.update { list -> normalize(list.filter { it.isPinned || it.timestamp >= cutoff }) }
        scope.launch {
            for (g in gone) {
                val p = g.imagePath
                if (p != null) ImageStore.delete(p)
            }
        }
    }

    // ---------- المجموعات ----------

    // بيجمّع العناصر المختارة في مجموعة واحدة جديدة، وبيرجّع رقمها
    fun createGroup(targets: List<ShelfItem>, name: String?): String {
        val id = newGroupId()
        val nm = name?.trim()?.takeIf { it.isNotEmpty() }
        _items.update { list ->
            normalize(
                list.map { i ->
                    if (targets.any { same(i, it) }) i.copy(groupId = id, groupName = nm) else i
                }
            )
        }
        return id
    }

    fun removeFromGroup(targets: List<ShelfItem>) {
        _items.update { list ->
            normalize(
                list.map { i ->
                    if (targets.any { same(i, it) }) i.copy(groupId = null, groupName = null) else i
                }
            )
        }
    }

    fun ungroup(groupId: String) {
        if (_manual.value?.id == groupId) _manual.value = null
        _items.update { list ->
            list.map { if (it.groupId == groupId) it.copy(groupId = null, groupName = null) else it }
        }
    }

    // بيمسح عناصر المجموعة (المثبّت بيفضل لوحده)
    fun removeGroup(groupId: String) {
        if (_manual.value?.id == groupId) _manual.value = null
        val gone = _items.value.filter { it.groupId == groupId && !it.isPinned }
        val firstText = gone.firstOrNull { it.imagePath == null }
        if (firstText != null) dismissedText = firstText.text
        _items.update { list ->
            normalize(
                list.filterNot { it.groupId == groupId && !it.isPinned }
                    .map { if (it.groupId == groupId) it.copy(groupId = null, groupName = null) else it }
            )
        }
        scope.launch {
            for (g in gone) {
                val p = g.imagePath
                if (p != null) ImageStore.delete(p)
            }
        }
    }

    fun setGroupName(groupId: String, name: String?) {
        val nm = name?.trim()?.takeIf { it.isNotEmpty() }
        _items.update { list ->
            list.map { if (it.groupId == groupId) it.copy(groupName = nm) else it }
        }
        val m = _manual.value
        if (m != null && m.id == groupId) _manual.value = ManualGroup(m.id, nm)
    }

    // ---------- المجموعة اليدوية ----------

    fun startManualGroup(name: String?): String {
        val id = newGroupId()
        val nm = name?.trim()?.takeIf { it.isNotEmpty() }
        _manual.value = ManualGroup(id, nm)
        manualLastAt = System.currentTimeMillis()
        return id
    }

    fun endManualGroup() {
        _manual.value = null
        _items.update { list -> normalize(list) }
    }
}
