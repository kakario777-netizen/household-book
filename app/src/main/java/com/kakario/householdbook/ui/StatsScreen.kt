package com.kakario.householdbook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.Green
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.Entry
import com.kakario.householdbook.data.category
import com.kakario.householdbook.data.num
import com.kakario.householdbook.data.shortWon
import com.kakario.householdbook.data.won
import com.kakario.householdbook.data.ymAdd
import java.util.Locale

private data class Part(val id: String, val name: String, val color: Color, val amount: Long)

private fun pct(v: Long, total: Long): String =
    if (total <= 0) "0%" else String.format(Locale.KOREA, "%.1f%%", v * 100.0 / total).replace(".0%", "%")

@Composable
fun StatsScreen(data: BookData, ym: Int, onYm: (Int) -> Unit, onChanged: () -> Unit) {
    var openCat by rememberSaveable(ym) { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<Entry?>(null) }

    val month = data.inMonth(ym).filter { it.included }
    val total = month.sumOf { it.signed }
    val prev = data.inMonth(ymAdd(ym, -1)).sumOf { it.signed }

    val cats = month.groupBy { it.cat }
        .map { (id, l) -> Part(id, category(id).name, catColor(id), l.sumOf { it.signed }.coerceAtLeast(0)) }
        .filter { it.amount > 0 }.sortedByDescending { it.amount }
    val catSum = cats.sumOf { it.amount }

    val palette = listOf(0xFF1F4E79, 0xFFE26AA5, 0xFF4CAF7A, 0xFFF2B233, 0xFF8E7CC3, 0xFF3BB3C3)
    val cards = month.groupBy { it.t.card }
        .map { (id, l) -> id to l.sumOf { it.signed }.coerceAtLeast(0) }
        .filter { it.second > 0 }.sortedByDescending { it.second }
        .mapIndexed { i, (id, v) -> Part(id, data.cardName(id), Color(palette[i % palette.size]), v) }
    val cardSum = cards.sumOf { it.amount }

    val trend = (5 downTo 0).map { i -> val m = ymAdd(ym, -i); m to data.inMonth(m).sumOf { it.signed } }

    LazyColumn(Modifier.fillMaxSize()) {
        item { MonthHeader(ym, onYm) }
        item {
            SummaryCard {
                BigAmount("${ym % 100}월 합계", total)
                if (prev > 0) {
                    val d = total - prev
                    Text(
                        if (d >= 0) "지난달보다 ${won(d)} 더 썼어요" else "지난달보다 ${won(-d)} 덜 썼어요",
                        fontSize = 13.sp, color = if (d > 0) Red else Green,
                    )
                }
            }
        }

        // 분류별
        item { SectionTitle("어디에 썼나", if (cats.isEmpty()) "" else "${cats.size}개 분류") }
        if (cats.isEmpty()) {
            item { Text("이 달 결제 내역이 없어요", color = Gray, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
        } else {
            item { Donut(cats, catSum) }
            items(cats, key = { "c|" + it.id }) { p ->
                ShareRow(p, catSum, opened = openCat == p.id) { openCat = if (openCat == p.id) null else p.id }
                if (openCat == p.id) {
                    val list = month.filter { it.cat == p.id }.sortedByDescending { it.t.time }
                    Column(Modifier.fillMaxWidth().background(Color(0xFFFAFAFA))) {
                        list.forEach { e -> EntryRow(e, data, showDate = true) { edit = e } }
                    }
                }
            }
        }

        // 카드별
        if (cards.isNotEmpty()) {
            item { SectionTitle("카드별", won(cardSum)) }
            items(cards, key = { "k|" + it.id }) { p -> ShareRow(p, cardSum, opened = false, onClick = null) }
        }

        // 최근 6개월
        item { SectionTitle("최근 6개월") }
        item { Trend(trend, ym) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    edit?.let { EditDialog(it, data, onDismiss = { edit = null }, onChanged = onChanged) }
}

@Composable
private fun Donut(parts: List<Part>, total: Long) {
    Box(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 34.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            var start = -90f
            parts.forEach { p ->
                val sweep = if (total > 0) p.amount * 360f / total else 0f
                drawArc(p.color, start, sweep, useCenter = false, topLeft = topLeft, size = Size(d, d), style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(parts.first().name, fontSize = 12.sp, color = Gray, maxLines = 1)
            Text(pct(parts.first().amount, total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShareRow(p: Part, total: Long, opened: Boolean, onClick: (() -> Unit)?) {
    val frac = if (total > 0) p.amount.toFloat() / total else 0f
    Column(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(p.color)
            Spacer(Modifier.width(10.dp))
            Text(p.name, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pct(p.amount, total), fontSize = 14.sp, color = Gray, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
            Text(won(p.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End, maxLines = 1)
            if (onClick != null) Text(if (opened) " ▲" else " ▼", fontSize = 11.sp, color = Gray)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(LightGray, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(p.color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Trend(trend: List<Pair<Int, Long>>, cur: Int) {
    val max = trend.maxOf { it.second }.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        trend.forEach { (m, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (v > 0) shortWon(v) else "", fontSize = 10.sp, color = Gray, maxLines = 1, softWrap = false)
                Spacer(Modifier.height(2.dp))
                val h = (v.coerceAtLeast(0).toFloat() / max * 110f)
                Box(
                    Modifier.fillMaxWidth(0.7f).height(h.coerceAtLeast(2f).dp)
                        .background(if (m == cur) Green else Green.copy(alpha = 0.35f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text("${m % 100}월", fontSize = 12.sp, color = if (m == cur) Green else Gray)
            }
        }
    }
}
