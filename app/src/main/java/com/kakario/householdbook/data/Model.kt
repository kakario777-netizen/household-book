package com.kakario.householdbook.data

import java.util.Calendar
import java.util.Locale

/** 가계부 한 건 */
data class Txn(
    val key: String,            // 중복 방지 열쇠 (문자) / "m|시각" (직접 입력)
    val card: String,           // 카드 id, 직접 입력 현금은 "cash"
    val time: Long,
    val merchant: String,
    val amount: Long,           // 양수
    val cancel: Boolean,
    val installment: String = "",
    val kind: String = "",      // "" 일반 / 자동납부 / 결제 예정 안내 / 이용금액 안내 / 해외 결제
    val raw: String = "",
    val manual: Boolean = false,
) {
    val signed: Long get() = if (cancel) -amount else amount

    /** 안내 문자(결제 예정·이용금액)는 실제 결제가 아니라 기본으로 뺌 */
    val defaultExcluded: Boolean get() = kind == "결제 예정 안내" || kind == "이용금액 안내"
}

/** 카드사 번호에서 왔는데 결제로 못 읽은(또는 애매한) 문자 */
data class Pending(val key: String, val card: String, val time: Long, val body: String, val guess: Long)

data class CardConf(
    val id: String,
    val name: String,
    val keyword: String,
    val numbers: List<String>,
    val enabled: Boolean,
)

/** 카드사 대표번호(공개 번호) — 설정에서 바꾸거나 추가 가능 */
val DEFAULT_CARDS = listOf(
    CardConf("hyundai", "현대카드", "현대", listOf("15776200"), true),
    CardConf("lotte", "롯데카드", "롯데", listOf("15888100"), true),
    CardConf("woori", "우리카드", "우리", listOf("15889955"), true),
)

const val CASH = "cash"

/** 분류 */
data class Category(val id: String, val name: String, val color: Long)

val CATEGORIES = listOf(
    Category("grocery", "식비·장보기", 0xFF4CAF7A),
    Category("dining", "외식·배달", 0xFFEF7D4F),
    Category("cafe", "카페·간식·편의점", 0xFFB9875A),
    Category("transport", "교통·주유", 0xFF4A90D9),
    Category("shopping", "쇼핑", 0xFFE26AA5),
    Category("living", "생활·공과금", 0xFF8E7CC3),
    Category("subscribe", "통신·구독", 0xFF3BB3C3),
    Category("medical", "의료·약국", 0xFFE0565B),
    Category("edu", "교육·육아", 0xFFF2B233),
    Category("leisure", "문화·여가", 0xFF7CB342),
    Category("etc", "기타", 0xFF9E9E9E),
)

fun category(id: String): Category = CATEGORIES.firstOrNull { it.id == id } ?: CATEGORIES.last()

/* ---------- 공통 도구 ---------- */

fun digits(s: String) = s.filter { it.isDigit() }

fun won(v: Long): String = String.format(Locale.KOREA, "%,d원", v)

fun num(v: Long): String = String.format(Locale.KOREA, "%,d", v)

/** 달력 칸용 짧은 금액 (칸을 넘지 않게 최대 6~7글자) */
fun shortWon(v: Long): String {
    val a = kotlin.math.abs(v)
    val s = when {
        a < 100_000 -> String.format(Locale.KOREA, "%,d", a)
        a < 10_000_000 -> String.format(Locale.KOREA, "%.1f만", a / 10_000.0).replace(".0만", "만")
        else -> String.format(Locale.KOREA, "%,d만", a / 10_000)
    }
    return if (v < 0) "-$s" else s
}

/** 연월 202611 */
fun ymOf(t: Long): Int {
    val c = Calendar.getInstance(); c.timeInMillis = t
    return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
}

fun ymAdd(ym: Int, n: Int): Int {
    var y = ym / 100; var m = ym % 100 - 1 + n
    y += Math.floorDiv(m, 12); m = Math.floorMod(m, 12)
    return y * 100 + m + 1
}

fun ymText(ym: Int) = "${ym / 100}년 ${ym % 100}월"

fun ymStart(ym: Int): Long {
    val c = Calendar.getInstance(); c.clear(); c.set(ym / 100, ym % 100 - 1, 1); return c.timeInMillis
}

fun ymEnd(ym: Int): Long = ymStart(ymAdd(ym, 1)) - 1

fun daysIn(ym: Int): Int {
    val c = Calendar.getInstance(); c.clear(); c.set(ym / 100, ym % 100 - 1, 1)
    return c.getActualMaximum(Calendar.DAY_OF_MONTH)
}

/** 1일의 요일 (0=일 … 6=토) */
fun firstWeekday(ym: Int): Int {
    val c = Calendar.getInstance(); c.clear(); c.set(ym / 100, ym % 100 - 1, 1)
    return c.get(Calendar.DAY_OF_WEEK) - 1
}

fun dayOf(t: Long): Int {
    val c = Calendar.getInstance(); c.timeInMillis = t; return c.get(Calendar.DAY_OF_MONTH)
}

fun dayStart(t: Long): Long {
    val c = Calendar.getInstance(); c.timeInMillis = t
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun timeText(t: Long): String = String.format(Locale.KOREA, "%tH:%tM", t, t)

fun dateText(t: Long): String = String.format(Locale.KOREA, "%tm/%td", t, t)

/** 가게 이름 비교용 (띄어쓰기·괄호·대소문자 무시) */
fun normMerchant(s: String): String =
    s.lowercase(Locale.ROOT).replace("(주)", "").replace("주식회사", "").replace(Regex("[\\s()\\[\\]_.·\\-]"), "")
