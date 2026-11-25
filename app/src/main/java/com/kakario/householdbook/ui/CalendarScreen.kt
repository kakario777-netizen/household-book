package com.kakario.householdbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.kakario.householdbook.data.ymAdd
import com.kakario.householdbook.data.ymOf
import com.kakario.householdbook.data.ymStart
import com.kakario.householdbook.data.ymText

private val WEEK = listOf("일", "월", "화", "수", "목", "금", "토")

/** 정기결제 표시 색 */
val RecColor = Color(0xFF7B5CC4)
private val GridLine = Color(0xFFE3E6EA)

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
    val dayRec = byDay.mapValues { (_, l) -> l.filter { it.merchantId in recIds }.sumOf { it.signed } }
    val upByDay = Book.upcoming(data, ym, now).groupBy({ it.first }, { it.second })

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ── 한 화면: 합계 + 달력 (화면 끝까지) ──
            Column(Modifier.fillMaxWidth().height(screenH)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!hasSms) PermissionCard(onAskSms, onOpenSettings)

                // 월 이동 + 합계
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("◀", color = Gray, fontSize = 16.sp, modifier = Modifier.clickable { onYm(ymAdd(ym, -1)) }.padding(12.dp))
                    Text(ymText(ym), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("▶", color = Gray, fontSize = 16.sp, modifier = Modifier.clickable { onYm(ymAdd(ym, 1)) }.padding(12.dp))
                    Spacer(Modifier.weight(1f))
                    if (ym == curYm) {
                        Text("오늘 ${num(dayTotal[todayDay] ?: 0L)}원", fontSize = 13.sp, color = Green, fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    "${num(monthTotal)}원",
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )

                // 요일
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    WEEK.forEachIndexed { i, w ->
                        Text(
                            w, Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center, fontSize = 12.sp,
                            color = when (i) { 0 -> Red; 6 -> Blue; else -> Gray },
                        )
                    }
                }

                // 달력 칸 (남은 높이를 줄 수만큼 나눠 씀)
                val first = firstWeekday(ym)
                val n = daysIn(ym)
                val rows = (first + n + 6) / 7
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                        .border(0.8.dp, GridLine)
                ) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            for (c in 0 until 7) {
                                val d = r * 7 + c - first + 1
                                Box(Modifier.weight(1f).fillMaxHeight().border(0.4.dp, GridLine)) {
                                    if (d in 1..n) {
                                        DayCell(
                                            day = d, weekday = c,
                                            total = dayTotal[d] ?: 0L,
                                            rec = dayRec[d] ?: 0L,
                                            planned = upByDay[d]?.sumOf { it.amount } ?: 0L,
                                            today = ym == curYm && d == todayDay,
                                            selected = d == selDay,
                                            onClick = { selDay = d },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "▲ 위로 밀면 ${selDay}일 내역",
                    fontSize = 11.sp, color = Color(0xFFA0A7B0), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }

            // ── 아래: 고른 날 내역 ──
            val dayList = byDay[selDay].orEmpty().sortedBy { it.t.time }
            val planned = upByDay[selDay].orEmpty()
            SectionTitle("${ym % 100}월 ${selDay}일 (${WEEK[(firstWeekday(ym) + selDay - 1) % 7]})", won(dayTotal[selDay] ?: 0L))
            if (dayList.isEmpty() && planned.isEmpty()) {
                Text("결제 내역이 없어요", color = Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            dayList.forEach { e ->
                EntryRow(e, data, recurring = e.merchantId in recIds) { edit = e }
                HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
            }
            planned.forEach { r -> PlannedRow(r, data) }
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("현금·기타 직접 넣기")
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    edit?.let { EditDialog(it, data, onDismiss = { edit = null }, onChanged = onChanged) }
    if (adding) {
        AddDialog(ymStart(ym) + (selDay - 1) * 24L * 3600 * 1000, onDismiss = { adding = false }, onChanged = onChanged)
    }
}

/** 날짜 한 칸: 날짜 → 정기결제(보라) → 그 밖에 쓴 돈 */
@Composable
private fun DayCell(
    day: Int, weekday: Int, total: Long, rec: Long, planned: Long,
    today: Boolean, selected: Boolean, onClick: () -> Unit,
) {
    val other = total - rec
    Column(
        Modifier.fillMaxSize()
            .background(if (selected) GreenSoft else Color.Transparent)
            .border(if (selected) 1.6.dp else 0.dp, if (selected) Green else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(20.dp).background(if (today) Green else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day", fontSize = 11.sp,
                color = when { today -> Color.White; weekday == 0 -> Red; weekday == 6 -> Blue; else -> Color(0xFF374151) },
                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(2.dp))
        if (rec != 0L) AmountChip(shortWon(rec), RecColor, filled = true)
        if (planned > 0) AmountChip(shortWon(planned), RecColor, filled = false)
        if (other != 0L) {
            Text(
                shortWon(other), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                color = if (other < 0) Blue else Color(0xFF111827), fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** 정기결제 금액 (결제됨 = 색 배경, 예정 = 테두리만) */
@Composable
private fun AmountChip(text: String, color: Color, filled: Boolean) {
    Text(
        text, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        color = if (filled) Color.White else color, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 1.dp)
            .background(if (filled) color else Color.Transparent, RoundedCornerShape(4.dp))
            .border(if (filled) 0.dp else 0.8.dp, if (filled) Color.Transparent else color, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
    )
}

@Composable
private fun PlannedRow(r: Recurring, data: BookData) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).border(1.5.dp, RecColor, CircleShape))
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
