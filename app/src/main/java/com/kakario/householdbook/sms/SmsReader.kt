package com.kakario.householdbook.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat
import com.kakario.householdbook.data.CardConf
import com.kakario.householdbook.data.digits

/** 받은 문자 한 통. src = SMS / MMS / RCS */
data class Msg(val address: String, val body: String, val time: Long, val src: String)

/** 문자에서 읽은 결제 한 건 (가공 전) */
data class ParsedPay(
    val key: String,
    val card: String,
    val time: Long,
    val merchant: String,
    val amount: Long,
    val cancel: Boolean,
    val installment: String,
    val kind: String,
    val raw: String,
)

/** 카드 정리 앱에서 가져온 문자 읽기 (SMS + 장문 MMS + 삼성 채팅+ RCS) */
object SmsReader {

    const val DAY = 24L * 3600 * 1000

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** 초/밀리초가 섞여 있어도 밀리초로 */
    private fun ms(v: Long) = if (v in 1..99_999_999_999L) v * 1000 else v

    private fun Cursor.str(name: String): String? {
        val i = getColumnIndex(name); return if (i >= 0) getString(i) else null
    }

    private fun Cursor.lng(name: String): Long {
        val i = getColumnIndex(name); return if (i >= 0) getLong(i) else 0L
    }

    fun inbox(ctx: Context, from: Long, to: Long): List<Msg> {
        val out = ArrayList<Msg>()
        for (fn in listOf<() -> Unit>(
            { readSms(ctx, from, to, out) },
            { readMms(ctx, from, to, out) },
            { readRcs(ctx, from, to, out) },
        )) {
            try { fn() } catch (_: Exception) { }
        }
        return out.map { it.copy(body = cleanBody(it.body)) }
    }

    /** 채팅+ 카드형 메시지(JSON)면 글만 뽑고, 줄바꿈 정리 */
    fun cleanBody(body: String): String {
        val rich = richText(body)
        return SmsParser.normalize(rich ?: body).trim()
    }

    private fun richText(body: String): String? {
        val t = body.trim()
        if (!t.startsWith("{") || !t.contains("\"widget\"")) return null
        return try {
            val out = ArrayList<String>()
            fun walk(v: Any?) {
                when (v) {
                    is org.json.JSONObject -> {
                        if (v.optString("widget") == "Button") return
                        val txt = v.opt("text")
                        if (txt is String && txt.isNotBlank()) out.add(txt.trim())
                        val keys = v.keys()
                        while (keys.hasNext()) {
                            val c = v.opt(keys.next())
                            if (c is org.json.JSONObject || c is org.json.JSONArray) walk(c)
                        }
                    }
                    is org.json.JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
                }
            }
            walk(org.json.JSONObject(t))
            if (out.isEmpty()) null else out.joinToString("\n")
        } catch (_: Exception) {
            val m = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").findAll(t)
                .map { it.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/") }
                .filter { it.isNotBlank() }.toList()
            if (m.isEmpty()) null else m.joinToString("\n")
        }
    }

    private fun readSms(ctx: Context, from: Long, to: Long, out: MutableList<Msg>) {
        val c = ctx.contentResolver.query(
            Uri.parse("content://sms"), arrayOf("address", "body", "date"),
            "type = 1 AND date >= ? AND date <= ?",
            arrayOf(from.toString(), to.toString()), "date ASC"
        ) ?: return
        c.use {
            while (it.moveToNext()) {
                val body = it.str("body") ?: continue
                out.add(Msg(it.str("address") ?: "", body, ms(it.lng("date")), "SMS"))
            }
        }
    }

    /** [Web발신] 장문은 MMS 칸에 들어감 */
    private fun readMms(ctx: Context, from: Long, to: Long, out: MutableList<Msg>) {
        val cr = ctx.contentResolver
        val ids = ArrayList<Pair<Long, Long>>()
        val c = cr.query(
            Uri.parse("content://mms"), arrayOf("_id", "date", "msg_box"),
            "msg_box = 1 AND ((date >= ? AND date <= ?) OR (date >= ? AND date <= ?))",
            arrayOf((from / 1000).toString(), (to / 1000).toString(), from.toString(), to.toString()),
            "date ASC"
        ) ?: return
        c.use { while (it.moveToNext()) ids.add(it.getLong(0) to ms(it.getLong(1))) }
        for ((id, time) in ids) {
            if (time < from || time > to) continue
            val body = mmsText(ctx, id)
            if (body.isBlank()) continue
            // 결제 문자가 아니면 주소 조회까지 하지 않음 (1년치 읽을 때 빠르게)
            if (!SmsParser.looksLikePayment(cleanBody(body))) continue
            out.add(Msg(mmsAddress(ctx, id) ?: "", body, time, "MMS"))
        }
    }

    private fun mmsAddress(ctx: Context, id: Long): String? {
        val c = ctx.contentResolver.query(
            Uri.parse("content://mms/$id/addr"), arrayOf("address", "type"), null, null, null
        ) ?: return null
        var any: String? = null
        c.use {
            while (it.moveToNext()) {
                val a = it.getString(0) ?: continue
                if (it.getInt(1) == 137) return a
                if (any == null && a != "insert-address-token") any = a
            }
        }
        return any
    }

    private fun mmsText(ctx: Context, id: Long): String {
        val cr = ctx.contentResolver
        val sb = StringBuilder()
        val c = cr.query(Uri.parse("content://mms/part"), null, "mid = ?", arrayOf(id.toString()), null) ?: return ""
        c.use {
            while (it.moveToNext()) {
                val ct = it.str("ct") ?: continue
                if (!ct.startsWith("text/plain")) continue
                val text = it.str("text")
                if (!text.isNullOrEmpty()) { sb.append(text); continue }
                try {
                    cr.openInputStream(Uri.parse("content://mms/part/${it.lng("_id")}"))?.use { s ->
                        sb.append(s.readBytes().toString(Charsets.UTF_8))
                    }
                } catch (_: Exception) { }
            }
        }
        return sb.toString()
    }

    /** 삼성 메시지 채팅+(RCS) 저장소. 기기마다 없을 수 있음 */
    private fun readRcs(ctx: Context, from: Long, to: Long, out: MutableList<Msg>) {
        val c = ctx.contentResolver.query(Uri.parse("content://im/chat"), null, null, null, null) ?: return
        c.use {
            while (it.moveToNext()) {
                val body = it.str("body") ?: continue
                val time = ms(it.lng("date"))
                if (time < from || time > to) continue
                val addr = listOf("address", "sender", "from_address", "remote_uri", "recipients")
                    .mapNotNull { k -> it.str(k) }.firstOrNull { a -> digits(a).length >= 4 } ?: ""
                out.add(Msg(addr, body, time, "RCS"))
            }
        }
    }

    /* ---------- 카드 판별 ---------- */

    private fun numberMatch(addr: String, numbers: List<String>): Boolean {
        val a = digits(addr)
        if (a.length < 4) return false
        return numbers.any { n -> n.length >= 4 && (a == n || a.endsWith(n) || n.endsWith(a)) }
    }

    private fun bodyMatch(body: String, c: CardConf): Boolean {
        val head = body.take(80)
        if (c.name.isNotBlank() && head.contains(c.name)) return true
        val k = c.keyword.trim()
        if (k.isEmpty()) return false
        return head.contains("${k}카드") || Regex(Regex.escape(k) + "\\s*[(\\[]").containsMatchIn(head)
    }

    fun cardFor(m: Msg, cards: List<CardConf>): CardConf? {
        if (!SmsParser.looksLikePayment(m.body)) return null
        val on = cards.filter { it.enabled }
        return on.firstOrNull { numberMatch(m.address, it.numbers) }
            ?: on.firstOrNull {
                bodyMatch(m.body, it) && (m.body.contains("승인") || m.body.contains("취소") || SmsParser.kindOf(m.body).isNotEmpty())
            }
    }

    /** 기간 안의 카드 결제 문자 → 결제 목록 (같은 문자가 여러 칸에 저장된 경우 하나로) */
    fun payments(ctx: Context, cards: List<CardConf>, from: Long, to: Long): List<ParsedPay> {
        val result = ArrayList<ParsedPay>()
        val seen = HashSet<String>()
        for (m in inbox(ctx, from, to).sortedBy { it.time }) {
            val card = cardFor(m, cards) ?: continue
            val r = SmsParser.parse(m.body, m.time)
            if (!r.ok) continue
            val key = "${card.id}|${r.amount}|${r.cancel}|${r.kind}|${r.time / 60000}"
            if (!seen.add(key)) continue
            result.add(
                ParsedPay(
                    key = key, card = card.id, time = r.time, merchant = r.merchant.trim(),
                    amount = r.amount, cancel = r.cancel, installment = r.installment,
                    kind = r.kind, raw = m.body.trim(),
                )
            )
        }
        return result
    }

    /** 최근 문자에서 키워드가 들어간 결제 문자를 보낸 번호 찾기 (번호 → 건수) */
    fun findSenders(ctx: Context, keyword: String, days: Int = 90): List<Pair<String, Int>> {
        if (keyword.isBlank()) return emptyList()
        val to = System.currentTimeMillis()
        val count = HashMap<String, Int>()
        for (m in inbox(ctx, to - days * DAY, to)) {
            if (!m.body.contains(keyword) || !SmsParser.looksLikePayment(m.body)) continue
            val n = digits(m.address)
            if (n.isEmpty()) continue
            count[n] = (count[n] ?: 0) + 1
        }
        return count.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
