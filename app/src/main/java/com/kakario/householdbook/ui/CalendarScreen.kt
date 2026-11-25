package com.kakario.householdbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.Green
import com.kakario.householdbook.data.Book
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.Entry
import com.kakario.householdbook.data.Recurring
import com.kakario.householdbook.data.dayOf
import com.kakario.householdbook.data.daysIn
import com.kakario.householdbook.data.firstWeekday
import com.kakario.householdbook.data.num
import com.kakario.householdbook.data.shortWon
import com.kakario.householdbook.data.won
import com.kakario.householdbook.data.ymOf
import com.kakario.householdbook.data.ymStart

private val WEEK = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun CalendarScreen(
    data: BookData, ym: Int, onYm: (Int) -> Unit, loading: Boolean, hasSms: Boolean,
    onAskSms: () -> Unit, onOpenSettings: () -> Unit, onChanged: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val curYm = ymOf(now)
    val todayDay = dayOf(now)
    var selDay by rememberSaveable(ym) { mutableStateOf(if (ym == curYm) todayDay else 1) }
    var edit by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }

    val month = data.inMonth(ym)
    val byDay = month.groupBy { dayOf(it.t.time) }
    val dayTotal = byDay.mapValues { (_, l) -> l.sumOf { it.signed } }
    val monthTotal = month.sumOf { it.signed }
    val recIds = data.recurring.mapTo(HashSet()) { it.id }
    val recDays = month.filter { it.included && it.merchantId in recIds }.mapTo(HashSet()) { dayOf(it.t.time) }
    val upcoming = Book.upcoming(data, ym, now)
    val upByDay = upcoming.groupBy({ it.first }, { it.second })

    LazyColumn(Modifier.fillMaxSize()) {
        item { MonthHeader(ym, onYm) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        if (!hasSms) item { PermissionCard(onAskSms, onOpenSettings) }

        item {
            SummaryCard {
                Row {
                    Column(Modifier.weight(1f)) { BigAmount("${ym % 100}월 쓴 돈", monthTotal) }
                    if (ym == curYm) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("오늘", fontSize = 13.sp, color = Gray)
                            Text("${num(dayTotal[todayDay] ?: 0L)}원", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Green)
                        }
                    }
                }
                if (upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "남은 정기결제 ${upcoming.size}건 · ${won(upcoming.sumOf { it.second.amount })} 예정",
                        fontSize = 13.sp, color = Gray,
                    )
                }
            }
        }

        // 요일
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                WEEK.forEachIndexed { i, w ->
                    Text(
                        w, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                        color = when (i) { 0 -> Red; 6 -> Blue; else -> Gray },
                    )
                }
            }
        }

        // 달력 칸
        item {
            val first = firstWeekday(ym)
            val n = daysIn(ym)
            val cells = first + n
            val rows = (cells + 6) / 7
            Column(Modifier.padding(horizontal = 8.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val d = r * 7 + c - first + 1
                            Box(Modifier.weight(1f).height(64.dp).padding(1.5.dp)) {
                                if (d in 1..n) {
                                    DayCell(
                                        day = d, weekday = c,
                                        total = dayTotal[d] ?: 0L,
                                        today = ym == curYm && d == todayDay,
                                        future = ym > curYm || (ym == curYm && d > todayDay),
                                        selected = d == selDay,
                                        recurring = d in recDays,
                                        planned = upByDay[d]?.sumOf { it.amount } ?: 0L,
                                        onClick = { selDay = d },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 고른 날 내역
        val dayList = byDay[selDay].orEmpty().sortedBy { it.t.time }
        val planned = upByDay[selDay].orEmpty()
        item {
            SectionTitle("${ym % 100}월 ${selDay}일 (${WEEK[(firstWeekday(ym) + selDay - 1) % 7]})", won(dayTotal[selDay] ?: 0L))
        }
        if (dayList.isEmpty() && planned.isEmpty()) {
            item { Text("결제 내역이 없어요", color = Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
        items(dayList, key = { it.t.key }) { e ->
            EntryRow(e, data, recurring = e.merchantId in recIds) { edit = e }
            HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(planned, key = { "plan|" + it.id }) { r -> PlannedRow(r, data) }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("현금·기타 직접 넣기")
            }
        }
    }

    edit?.let { EditDialog(it, data, onDismiss = { edit = null }, onChanged = onChanged) }
    if (adding) {
        AddDialog(ymStart(ym) + (selDay - 1) * 24L * 3600 * 1000, onDismiss = { adding = false }, onChanged = onChanged)
    }
}

@Composable
private fun DayCell(
    day: Int, weekday: Int, total: Long, today: Boolean, future: Boolean, selected: Boolean,
    recurring: Boolean, planned: Long, onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(if (selected) GreenSoft else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(top = 3.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).background(if (today) Green else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$day", fontSize = 12.sp,
                    color = when { today -> Color.White; weekday == 0 -> Red; weekday == 6 -> Blue; else -> Color(0xFF374151) },
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (recurring) {
                Spacer(Modifier.width(1.dp))
                Dot(Color(0xFF3BB3C3), 5)
            }
        }
        Spacer(Modifier.height(2.dp))
        if (total != 0L) {
            Text(
                shortWon(total), fontSize = 10.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                color = if (total < 0) Blue else Color(0xFF111827), fontWeight = FontWeight.Medium,
            )
        }
        if (planned > 0 && (future || today)) {
            Text(
                shortWon(planned), fontSize = 9.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                color = Color(0xFF9AA3AE),
                modifier = Modifier.border(0.8.dp, Color(0xFFC9CED4), RoundedCornerShape(4.dp)).padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun PlannedRow(r: Recurring, data: BookData) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).border(1.5.dp, catColor(r.cat), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.merchant, fontSize = 15.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("정기결제 예정 · 매달 ${r.day}일 · ${data.cardName(r.card)}", fontSize = 12.sp, color = Gray, maxLines = 1)
        }
        Text(won(r.amount), fontSize = 15.sp, color = Gray)
    }
}

@Composable
fun PermissionCard(onAskSms: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp)
            .background(Color(0xFFFFF4E5), RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Text("카드 결제 문자를 읽으려면 문자 권한이 필요해요", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "문자는 폰 안에서만 읽고 어디에도 보내지 않아요.\n" +
                "권한 창이 안 뜨거나 회색으로 막혀 있으면: 앱 정보 → 오른쪽 위 ⋮ → '제한된 설정 허용' → 권한 → SMS 허용",
            fontSize = 13.sp, color = Gray,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAskSms) { Text("권한 허용") }
            OutlinedButton(onClick = onOpenSettings) { Text("앱 정보 열기") }
        }
    }
}
