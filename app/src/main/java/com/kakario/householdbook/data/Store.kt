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

    fun lastSync(ctx: Context): Long = prefs(ctx).getLong("lastSync", 0L)
    fun setLastSync(ctx: Context, v: Long) = prefs(ctx).edit().putLong("lastSync", v).apply()

    fun resetSync(ctx: Context) = prefs(ctx).edit().putLong("lastSync", 0L).apply()
}
