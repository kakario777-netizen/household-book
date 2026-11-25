package com.kakario.householdbook.data

import android.content.Context
import com.kakario.householdbook.sms.SmsReader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** 화면에 쓰는 한 건 = 결제 + 분류 + 가계부 포함 여부 */
data class Entry(val t: Txn, val cat: String, val included: Boolean) {
    val signed: Long get() = if (included) t.signed else 0L
    val merchantId: String get() = normMerchant(t.merchant)
}

/** 매달 나가는 정기결제 */
data class Recurring(
    val id: String,          // 정규화된 가게 이름
    val merchant: String,
    val card: String,
    val day: Int,            // 보통 결제되는 날
    val amount: Long,        // 최근 금액
    val cat: String,
    val months: Int,         // 최근 1년 중 결제된 달 수
    val lastTime: Long,
    val active: Boolean,     // 최근 두 달 안에 결제됨
    val auto: Boolean,       // 자동납부 문자
)

class BookData(
    val entries: List<Entry>,
    val recurring: List<Recurring>,      // 숨긴 것 제외
    val hiddenRecurring: List<Recurring>,
    val cards: List<CardConf>,
) {
    fun cardName(id: String): String =
        if (id == CASH) "현금·기타" else cards.firstOrNull { it.id == id }?.name ?: id

    fun inMonth(ym: Int): List<Entry> {
        val a = ymStart(ym); val b = ymEnd(ym)
        return entries.filter { it.t.time in a..b }
    }

    fun spentBetween(from: Long, to: Long): Long = entries.filter { it.t.time in from..to }.sumOf { it.signed }

    companion object {
        val EMPTY = BookData(emptyList(), emptyList(), emptyList(), DEFAULT_CARDS)
    }
}

object Book {
    private val syncLock = Any()

    /**
     * 새 문자 읽어서 저장. 처음엔 최근 1년치, 그다음부터는 마지막으로 읽은 때부터.
     * 돌려주는 값 = 새로 들어온 건수
     */
    fun sync(ctx: Context): Int = synchronized(syncLock) {
        if (!SmsReader.hasPermission(ctx)) return 0
        val now = System.currentTimeMillis()
        val last = Store.lastSync(ctx)
        val from = if (last == 0L) ymStart(ymAdd(ymOf(now), -12)) else last - 3 * SmsReader.DAY
        val found = SmsReader.payments(ctx, Store.loadCards(ctx), from, now + SmsReader.DAY)
        val old = Store.loadTxns(ctx)
        val keys = old.mapTo(HashSet()) { it.key }
        val add = found.filter { keys.add(it.key) }.map {
            Txn(
                key = it.key, card = it.card, time = it.time, merchant = it.merchant, amount = it.amount,
                cancel = it.cancel, installment = it.installment, kind = it.kind, raw = it.raw,
            )
        }
        if (add.isNotEmpty()) Store.saveTxns(ctx, old + add)
        Store.setLastSync(ctx, now)
        return add.size
    }

    fun addManual(ctx: Context, time: Long, merchant: String, amount: Long, cat: String) {
        val t = Txn(key = "m|$time|${System.nanoTime()}", card = CASH, time = time, merchant = merchant.trim(),
            amount = amount, cancel = false, manual = true)
        synchronized(syncLock) { Store.saveTxns(ctx, Store.loadTxns(ctx) + t) }
        Store.setTxnCat(ctx, t.key, cat)
    }

    fun deleteManual(ctx: Context, key: String) {
        synchronized(syncLock) { Store.saveTxns(ctx, Store.loadTxns(ctx).filterNot { it.key == key && it.manual }) }
    }

    fun load(ctx: Context): BookData {
        val rules = Store.loadRules(ctx)
        val txnCats = Store.loadTxnCats(ctx)
        val includes = Store.loadIncludes(ctx)
        val entries = Store.loadTxns(ctx).map { t ->
            val cat = txnCats[t.key] ?: Categorizer.categorize(t.merchant, t.kind, rules)
            val inc = includes[t.key]?.let { it == "1" } ?: !t.defaultExcluded
            Entry(t, cat, inc)
        }.sortedBy { it.t.time }
        val hidden = Store.loadHiddenRecurring(ctx)
        val rec = detectRecurring(entries, System.currentTimeMillis())
        return BookData(
            entries = entries,
            recurring = rec.filter { it.id !in hidden },
            hiddenRecurring = rec.filter { it.id in hidden },
            cards = Store.loadCards(ctx),
        )
    }

    /* ---------- 정기결제 찾기 ---------- */

    private fun monthsBetween(a: Int, b: Int) = (b / 100 - a / 100) * 12 + (b % 100 - a % 100)

    /** 날짜 차이 (말일·1일처럼 달 경계를 넘는 경우 포함) */
    private fun dayDist(a: Int, b: Int): Int { val d = abs(a - b); return min(d, 31 - d) }

    fun detectRecurring(entries: List<Entry>, now: Long): List<Recurring> {
        val curYm = ymOf(now)
        val minYm = ymAdd(curYm, -12)
        val groups = entries.filter {
            it.included && !it.t.cancel && it.t.merchant.isNotBlank() && ymOf(it.t.time) >= minYm && it.merchantId.isNotEmpty()
        }.groupBy { it.merchantId }

        val out = ArrayList<Recurring>()
        for ((id, list) in groups) {
            val byMonth = list.groupBy { ymOf(it.t.time) }
            val months = byMonth.keys.sorted()
            val auto = list.any { it.t.kind == "자동납부" }
            if (months.size < 2 && !auto) continue
            // 한 달에 여러 번 가는 가게(마트·편의점 등)는 정기결제 아님
            if (list.size.toDouble() / months.size > 1.5) continue

            val amounts = list.map { it.t.amount }.sorted()
            val med = amounts[amounts.size / 2]
            val picks = months.map { ym -> byMonth.getValue(ym).minBy { abs(it.t.amount - med) } }
            val need = max(2, ceil(picks.size * 0.8).toInt())

            if (!auto) {
                // 금액이 비슷하고 (±25%)
                if (picks.count { abs(it.t.amount - med) <= med / 4 } < need) continue
                // 결제일이 비슷하고 (±4일)
                val days = picks.map { dayOf(it.t.time) }.sorted()
                val dmed = days[days.size / 2]
                if (days.count { dayDist(it, dmed) <= 4 } < need) continue
                // 거의 매달 (두 달 넘게 빈 경우는 한 번까지만)
                if (months.zipWithNext().count { (a, b) -> monthsBetween(a, b) > 2 } > 1) continue
            }

            val days = picks.map { dayOf(it.t.time) }.sorted()
            val last = picks.last()
            out.add(
                Recurring(
                    id = id, merchant = last.t.merchant, card = last.t.card, day = days[days.size / 2],
                    amount = last.t.amount, cat = last.cat, months = months.size, lastTime = last.t.time,
                    active = monthsBetween(ymOf(last.t.time), curYm) <= 1, auto = auto,
                )
            )
        }
        return out.sortedWith(compareBy({ !it.active }, { it.day }))
    }

    /** 이 달에 아직 안 나간 정기결제 (지금 달·다음 달 이후 화면용). 결제일은 그 달 말일을 넘지 않게 */
    fun upcoming(data: BookData, ym: Int, now: Long): List<Pair<Int, Recurring>> {
        val curYm = ymOf(now)
        if (ym < curYm) return emptyList()
        val paidIds = data.inMonth(ym).filter { it.included && !it.t.cancel }.mapTo(HashSet()) { it.merchantId }
        val today = dayOf(now)
        return data.recurring.filter { it.active && it.id !in paidIds }
            .map { min(it.day, daysIn(ym)) to it }
            // 이번 달인데 결제일이 이미 지났으면 '늦어지는 중' — 오늘 칸에 붙임
            .map { (d, r) -> if (ym == curYm && d < today) today to r else d to r }
            .sortedBy { it.first }
    }
}
