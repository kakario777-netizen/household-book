package com.kakario.householdbook.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 폰 안에만 저장 (서버 전송 없음, 구글 클라우드 백업 제외).
 *  - 결제 목록: files/txns.json
 *  - 설정·사용자가 고친 것: SharedPreferences "book"
 */
object Store {
    private const val P = "book"
    private val lock = Any()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
    private fun txnFile(ctx: Context) = File(ctx.filesDir, "txns.json")

    /* ---------- 결제 목록 ---------- */

    fun loadTxns(ctx: Context): List<Txn> = synchronized(lock) {
        val f = txnFile(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Txn(
                    key = o.getString("k"), card = o.getString("c"), time = o.getLong("t"),
                    merchant = o.optString("m"), amount = o.getLong("a"), cancel = o.optBoolean("x"),
                    installment = o.optString("i"), kind = o.optString("kd"), raw = o.optString("r"),
                    manual = o.optBoolean("mn"),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveTxns(ctx: Context, list: List<Txn>) = synchronized(lock) {
        val arr = JSONArray()
        list.sortedBy { it.time }.forEach { t ->
            arr.put(JSONObject().apply {
                put("k", t.key); put("c", t.card); put("t", t.time); put("m", t.merchant); put("a", t.amount)
                if (t.cancel) put("x", true)
                if (t.installment.isNotEmpty()) put("i", t.installment)
                if (t.kind.isNotEmpty()) put("kd", t.kind)
                if (t.raw.isNotEmpty()) put("r", t.raw)
                if (t.manual) put("mn", true)
            })
        }
        val f = txnFile(ctx)
        val tmp = File(f.parentFile, "txns.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    /* ---------- 카드 ---------- */

    fun loadCards(ctx: Context): List<CardConf> {
        val s = prefs(ctx).getString("cards", null) ?: return DEFAULT_CARDS
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val nums = o.optString("numbers").split(",").map { digits(it) }.filter { it.isNotEmpty() }
                CardConf(o.getString("id"), o.getString("name"), o.optString("keyword"), nums, o.optBoolean("enabled", true))
            }
        } catch (_: Exception) { DEFAULT_CARDS }
    }

    fun saveCards(ctx: Context, cards: List<CardConf>) {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name.trim()); put("keyword", c.keyword.trim())
                put("numbers", c.numbers.joinToString(",")); put("enabled", c.enabled)
            })
        }
        prefs(ctx).edit().putString("cards", arr.toString()).apply()
    }

    /* ---------- 사용자가 고친 것 ---------- */

    private fun loadMap(ctx: Context, name: String): Map<String, String> {
        val s = prefs(ctx).getString(name, null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveMap(ctx: Context, name: String, map: Map<String, String>) {
        prefs(ctx).edit().putString(name, JSONObject(map).toString()).apply()
    }

    /** 가게별 분류 (정규화된 가게 이름 → 분류 id) */
    fun loadRules(ctx: Context) = loadMap(ctx, "rules")
    fun setRule(ctx: Context, merchant: String, cat: String) {
        val n = normMerchant(merchant)
        if (n.isEmpty()) return
        saveMap(ctx, "rules", loadRules(ctx) + (n to cat))
    }

    /** 건별 분류 (이 결제만) — 가게 이름이 없는 결제용 */
    fun loadTxnCats(ctx: Context) = loadMap(ctx, "txnCats")
    fun setTxnCat(ctx: Context, key: String, cat: String) = saveMap(ctx, "txnCats", loadTxnCats(ctx) + (key to cat))

    /** 건별 포함/제외 ("1" 포함, "0" 제외) — 기본값과 다를 때만 저장 */
    fun loadIncludes(ctx: Context) = loadMap(ctx, "includes")
    fun setInclude(ctx: Context, t: Txn, include: Boolean) {
        val m = loadIncludes(ctx).toMutableMap()
        if (include == !t.defaultExcluded) m.remove(t.key) else m[t.key] = if (include) "1" else "0"
        saveMap(ctx, "includes", m)
    }

    /** 정기결제 아님으로 표시한 가게 */
    fun loadHiddenRecurring(ctx: Context): Set<String> = prefs(ctx).getStringSet("hiddenRec", emptySet())!!.toSet()
    fun hideRecurring(ctx: Context, id: String) =
        prefs(ctx).edit().putStringSet("hiddenRec", loadHiddenRecurring(ctx) + id).apply()
    fun unhideRecurring(ctx: Context, id: String) =
        prefs(ctx).edit().putStringSet("hiddenRec", loadHiddenRecurring(ctx) - id).apply()

    /* ---------- 결제별 수정 ---------- */

    /** 가게 이름 고치기 (정규화된 원래 이름 → 보여줄 이름) — 앞으로 같은 가게 전부 */
    fun loadNames(ctx: Context) = loadMap(ctx, "names")
    fun setName(ctx: Context, merchant: String, name: String) {
        val n = normMerchant(merchant); if (n.isEmpty()) return
        val m = loadNames(ctx).toMutableMap()
        if (name.isBlank() || name.trim() == merchant.trim()) m.remove(n) else m[n] = name.trim()
        saveMap(ctx, "names", m)
    }

    /** 금액 고치기 (이 결제만) */
    fun loadAmounts(ctx: Context) = loadMap(ctx, "amounts")
    fun setAmount(ctx: Context, key: String, amount: Long?) {
        val m = loadAmounts(ctx).toMutableMap()
        if (amount == null) m.remove(key) else m[key] = amount.toString()
        saveMap(ctx, "amounts", m)
    }

    /** 메모 (이 결제만) */
    fun loadMemos(ctx: Context) = loadMap(ctx, "memos")
    fun setMemo(ctx: Context, key: String, memo: String) {
        val m = loadMemos(ctx).toMutableMap()
        if (memo.isBlank()) m.remove(key) else m[key] = memo.trim()
        saveMap(ctx, "memos", m)
    }

    /** 이 가게는 앞으로 계속 가계부에서 빼기 */
    fun loadShopExcludes(ctx: Context): Set<String> = prefs(ctx).getStringSet("shopEx", emptySet())!!.toSet()
    fun setShopExclude(ctx: Context, merchant: String, on: Boolean) {
        val n = normMerchant(merchant); if (n.isEmpty()) return
        val cur = loadShopExcludes(ctx)
        prefs(ctx).edit().putStringSet("shopEx", if (on) cur + n else cur - n).apply()
    }

    /** 직접 정기결제로 지정한 가게 */
    fun loadForcedRecurring(ctx: Context): Set<String> = prefs(ctx).getStringSet("recForce", emptySet())!!.toSet()
    fun setForcedRecurring(ctx: Context, id: String, on: Boolean) {
        val cur = loadForcedRecurring(ctx)
        prefs(ctx).edit().putStringSet("recForce", if (on) cur + id else cur - id).apply()
    }

    /** 확인 필요 → 확인함 */
    fun loadReviewed(ctx: Context): Set<String> = prefs(ctx).getStringSet("reviewed", emptySet())!!.toSet()
    fun markReviewed(ctx: Context, key: String) =
        prefs(ctx).edit().putStringSet("reviewed", loadReviewed(ctx) + key).apply()

    /* ---------- 읽지 못한 문자 ---------- */

    private fun pendingFile(ctx: Context) = File(ctx.filesDir, "pending.json")

    fun loadPending(ctx: Context): List<Pending> = synchronized(lock) {
        val f = pendingFile(ctx)
        if (!f.exists()) return emptyList()
        val done = loadPendingDone(ctx)
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Pending(o.getString("k"), o.getString("c"), o.getLong("t"), o.getString("b"), o.optLong("g"))
            }.filter { it.key !in done }
        } catch (_: Exception) { emptyList() }
    }

    fun addPending(ctx: Context, list: List<Pending>): Unit = synchronized(lock) {
        if (list.isEmpty()) return
        val f = pendingFile(ctx)
        val arr = try { if (f.exists()) JSONArray(f.readText()) else JSONArray() } catch (_: Exception) { JSONArray() }
        val keys = HashSet<String>()
        for (i in 0 until arr.length()) keys.add(arr.getJSONObject(i).getString("k"))
        list.filter { keys.add(it.key) }.forEach { p ->
            arr.put(JSONObject().apply { put("k", p.key); put("c", p.card); put("t", p.time); put("b", p.body); put("g", p.guess) })
        }
        f.writeText(arr.toString())
    }

    /** 처리한(넣었거나 무시한) 문자 */
    fun loadPendingDone(ctx: Context): Set<String> = prefs(ctx).getStringSet("pendingDone", emptySet())!!.toSet()
    fun markPendingDone(ctx: Context, keys: Collection<String>) =
        prefs(ctx).edit().putStringSet("pendingDone", loadPendingDone(ctx) + keys).apply()

    /* ---------- 백업용: 전체 내보내기 / 되돌리기 ---------- */

    /** 백업 설정(bk_)은 빼고 전부 */
    fun exportAll(ctx: Context): JSONObject {
        val out = JSONObject()
        out.put("app", "household-book"); out.put("version", 1); out.put("created", System.currentTimeMillis())
        synchronized(lock) {
            val f = txnFile(ctx)
            out.put("txns", if (f.exists()) JSONArray(f.readText()) else JSONArray())
            val pf = pendingFile(ctx)
            out.put("pending", if (pf.exists()) JSONArray(pf.readText()) else JSONArray())
        }
        val p = JSONObject()
        for ((k, v) in prefs(ctx).all) {
            if (k.startsWith("bk_")) continue
            when (v) {
                is String -> p.put(k, JSONObject().put("s", v))
                is Long -> p.put(k, JSONObject().put("l", v))
                is Int -> p.put(k, JSONObject().put("i", v))
                is Boolean -> p.put(k, JSONObject().put("b", v))
                is Set<*> -> p.put(k, JSONObject().put("set", JSONArray(v.map { it.toString() })))
            }
        }
        out.put("prefs", p)
        return out
    }

    fun importAll(ctx: Context, data: JSONObject) {
        require(data.optString("app") == "household-book") { "가계부 백업 파일이 아니에요" }
        synchronized(lock) {
            txnFile(ctx).writeText(data.getJSONArray("txns").toString())
            pendingFile(ctx).writeText(data.optJSONArray("pending")?.toString() ?: "[]")
        }
        val src = data.getJSONObject("prefs")
        val ed = prefs(ctx).edit()
        prefs(ctx).all.keys.filterNot { it.startsWith("bk_") }.forEach { ed.remove(it) }
        for (k in src.keys()) {
            if (k.startsWith("bk_")) continue
            val o = src.getJSONObject(k)
            when {
                o.has("s") -> ed.putString(k, o.getString("s"))
                o.has("l") -> ed.putLong(k, o.getLong("l"))
                o.has("i") -> ed.putInt(k, o.getInt("i"))
                o.has("b") -> ed.putBoolean(k, o.getBoolean("b"))
                o.has("set") -> {
                    val a = o.getJSONArray("set"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        ed.apply()
    }

    fun lastSync(ctx: Context): Long = prefs(ctx).getLong("lastSync", 0L)
    fun setLastSync(ctx: Context, v: Long) = prefs(ctx).edit().putLong("lastSync", v).apply()

    fun resetSync(ctx: Context) = prefs(ctx).edit().putLong("lastSync", 0L).apply()
}
