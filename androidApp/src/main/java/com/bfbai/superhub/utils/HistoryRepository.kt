// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import com.bfbai.superhub.service.ShelfItem
import com.bfbai.superhub.service.ShelfSettings
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

    data class ManualGroup(val id: String, val name: String?)
    data class UndoInfo(val id: Long, val count: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _manual = MutableStateFlow<ManualGroup?>(null)
    private val _undo = MutableStateFlow<UndoInfo?>(null)
    private var started = false
    private var lastTs = 0L
    private var groupSeq = 0L

    @Volatile
    private var manualLastAt = 0L

    @Volatile
    private var undoBuf: List<ShelfItem> = emptyList()

    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    val manualGroup: StateFlow<ManualGroup?> = _manual.asStateFlow()
    val undo: StateFlow<UndoInfo?> = _undo.asStateFlow()

    // لتجاهل صدى النسخ اللي بنعمله إحنا (مثلاً نسخ من السجل)
    @Volatile
    var ignoreClipUntil: Long = 0L

    // آخر 64 نص و64 صورة المستخدم مسحهم: الالتقاط السلبي بيتجاهلهم
    private val dismissedTexts = ArrayDeque<String>()
    private val dismissedImages = ArrayDeque<String>()

    private fun rememberDismissed(t: String) {
        synchronized(dismissedTexts) {
            dismissedTexts.remove(t)
            dismissedTexts.addLast(t)
            while (dismissedTexts.size > 64) dismissedTexts.removeFirst()
        }
    }

    private fun rememberDismissedImage(h: String) {
        synchronized(dismissedImages) {
            dismissedImages.remove(h)
            dismissedImages.addLast(h)
            while (dismissedImages.size > 64) dismissedImages.removeFirst()
        }
    }

    fun isDismissed(text: String): Boolean = synchronized(dismissedTexts) { dismissedTexts.contains(text) }

    fun isDismissedImage(hash: String?): Boolean =
        hash != null && synchronized(dismissedImages) { dismissedImages.contains(hash) }

    fun forgetDismissed(text: String) {
        synchronized(dismissedTexts) { dismissedTexts.remove(text) }
    }

    fun forgetDismissedImage(hash: String) {
        synchronized(dismissedImages) { dismissedImages.remove(hash) }
    }

    // للتوافق مع الكود القديم
    var dismissedText: String?
        get() = synchronized(dismissedTexts) { dismissedTexts.lastOrNull() }
        set(v) {
            if (v == null) synchronized(dismissedTexts) { dismissedTexts.clear() } else rememberDismissed(v)
        }

    var dismissedImageHash: String?
        get() = synchronized(dismissedImages) { dismissedImages.lastOrNull() }
        set(v) {
            if (v == null) synchronized(dismissedImages) { dismissedImages.clear() } else rememberDismissedImage(v)
        }

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
        ShelfSettings.init(context)
        PrivacyPolicy.init(context)
        val manager = HistoryManager(context.applicationContext)
        val days = PrivacyPolicy.autoWipeDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L
        scope.launch {
            val all = manager.loadHistory()
                .filterNot { isGarbage(it.text) }
                .filter { it.imagePath == null || File(it.imagePath).exists() }

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

    private fun keyOf(i: ShelfItem): String =
        i.timestamp.toString() + "|" + (i.imagePath ?: i.text.hashCode().toString())

    private fun sameApp(a: ShelfItem, b: ShelfItem): Boolean {
        val pa = a.sourcePkg
        val pb = b.sourcePkg
        return if (pa != null && pb != null) pa == pb else a.source == b.source
    }

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

        if (ShelfBehavior.groupSameApp && !sameApp(last, item)) return listOf(item) + list

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

    fun add(text: String, source: String, pkg: String? = null) {
        val t = text.trim()
        if (t.isEmpty() || isGarbage(t)) return
        _items.update { list ->
            val old = list.firstOrNull { it.imagePath == null && it.text == t }
            val rest = if (old != null) list.filterNot { it === old } else list
            val item = ShelfItem(
                text = t,
                source = source,
                timestamp = nextTs(),
                isPinned = old?.isPinned ?: false,
                sourcePkg = pkg
            )
            normalize(placeNew(rest, item))
        }
    }

    fun addImage(path: String, source: String, pkg: String? = null) {
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
                        imageHash = h,
                        sourcePkg = pkg
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

    // علامة "اتلصق/اتنسخت": ما بتغيّرش الترتيب
    fun markPasted(item: ShelfItem, pasted: Boolean) {
        val t = if (pasted) System.currentTimeMillis() else 0L
        _items.update { list ->
            list.map { if (same(it, item)) it.copy(pastedAt = t) else it }
        }
    }

    // ---------- الحذف مع التراجع ----------

    // بنحتفظ بالمحذوف 20 ثانية (وملفات الصور ما بتتمسحش قبلها)
    private fun stash(removed: List<ShelfItem>) {
        if (removed.isEmpty()) return
        undoBuf = removed
        _undo.value = UndoInfo(nextTs(), removed.size)
        for (r in removed) {
            if (r.imagePath == null) rememberDismissed(r.text) else r.imageHash?.let { rememberDismissedImage(it) }
        }
        val imgs = removed.mapNotNull { it.imagePath }
        if (imgs.isNotEmpty()) {
            scope.launch {
                delay(20_000)
                val keep = _items.value.mapNotNull { it.imagePath }.toSet()
                for (p in imgs) if (p !in keep) ImageStore.delete(p)
            }
        }
    }

    fun undo() {
        val back = undoBuf
        if (back.isEmpty()) return
        undoBuf = emptyList()
        _undo.value = null
        for (b in back) {
            if (b.imagePath == null) forgetDismissed(b.text) else b.imageHash?.let { forgetDismissedImage(it) }
        }
        _items.update { list ->
            val have = list.map { keyOf(it) }.toSet()
            val merged = list + back.filter { keyOf(it) !in have }
            normalize(merged.sortedByDescending { it.timestamp })
        }
    }

    fun remove(item: ShelfItem) {
        var gone = false
        _items.update { list ->
            val n = list.filterNot { same(it, item) }
            gone = n.size != list.size
            normalize(n)
        }
        if (gone) stash(listOf(item))
    }

    fun removeMany(targets: List<ShelfItem>) {
        if (targets.isEmpty()) return
        val gone = _items.value.filter { i -> targets.any { same(i, it) } }
        _items.update { list -> normalize(list.filterNot { i -> targets.any { same(i, it) } }) }
        stash(gone)
    }

    fun clearUnpinned() {
        removeMany(_items.value.filter { !it.isPinned })
    }

    fun clearPasted() {
        removeMany(_items.value.filter { !it.isPinned && it.pastedAt > 0L })
    }

    fun clearImages() {
        removeMany(_items.value.filter { !it.isPinned && it.imagePath != null })
    }

    fun cleanOlderThan(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        removeMany(_items.value.filter { !it.isPinned && it.timestamp < cutoff })
    }

    // ---------- المجموعات ----------

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
        DiagLog.d("repo", "createGroup id=$id targets=${targets.size}")
        return id
    }

    // السحب داخل الرف: ضم كارت لكارت (أو لمجموعته)
    fun dropOn(src: ShelfItem, target: ShelfItem) {
        if (same(src, target)) return
        _items.update { list ->
            val cur = list.firstOrNull { same(it, src) } ?: return@update list
            val tgt = list.firstOrNull { same(it, target) } ?: return@update list
            val gid = tgt.groupId ?: newGroupId()
            val name = tgt.groupName
            normalize(
                list.map { i ->
                    when {
                        same(i, cur) -> i.copy(groupId = gid, groupName = name)
                        same(i, tgt) && tgt.groupId == null -> i.copy(groupId = gid, groupName = null)
                        else -> i
                    }
                }
            )
        }
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
        _items.update { list ->
            normalize(
                list.filterNot { it.groupId == groupId && !it.isPinned }
                    .map { if (it.groupId == groupId) it.copy(groupId = null, groupName = null) else it }
            )
        }
        stash(gone)
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
