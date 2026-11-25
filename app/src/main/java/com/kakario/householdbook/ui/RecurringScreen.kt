package com.kakario.householdbook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.data.Book
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.Recurring
import com.kakario.householdbook.data.Store
import com.kakario.householdbook.data.category
import com.kakario.householdbook.data.dateText
import com.kakario.householdbook.data.won
import com.kakario.householdbook.data.ymOf

@Composable
fun RecurringScreen(data: BookData, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val active = data.recurring.filter { it.active }
    val stopped = data.recurring.filter { !it.active }
    val upcoming = Book.upcoming(data, ymOf(now), now)
    var showHidden by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Spacer(Modifier.height(12.dp))
            SummaryCard {
                BigAmount("매달 나가는 정기결제 (${active.size}건)", active.sumOf { it.amount })
                Spacer(Modifier.height(4.dp))
                Text(
                    if (upcoming.isEmpty()) "이번 달 남은 정기결제는 없어요"
                    else "이번 달 남은 것 ${upcoming.size}건 · ${won(upcoming.sumOf { it.second.amount })}",
                    fontSize = 13.sp, color = Gray,
                )
            }
            Text(
                "같은 가게에서 두 달 넘게 비슷한 날·비슷한 금액으로 결제되면 자동으로 찾아요. 자동납부 문자도 포함돼요.",
                fontSize = 12.sp, color = Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (data.recurring.isEmpty() && data.hiddenRecurring.isEmpty()) {
            item {
                Text(
                    "아직 찾은 정기결제가 없어요.\n몇 달 치 결제 문자가 쌓이면 여기에 나와요.",
                    color = Gray, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (active.isNotEmpty()) {
            item { SectionTitle("매달 결제 중", "결제일 순") }
            items(active, key = { "a|" + it.id }) { r ->
                RecRow(r, data, action = "정기결제 아님") { Store.hideRecurring(ctx, r.id); onChanged() }
            }
        }
        if (stopped.isNotEmpty()) {
            item { SectionTitle("최근에 멈춘 것", "두 달 넘게 결제 없음") }
            items(stopped, key = { "s|" + it.id }) { r ->
                RecRow(r, data, action = "목록에서 빼기", dim = true) { Store.hideRecurring(ctx, r.id); onChanged() }
            }
        }
        if (data.hiddenRecurring.isNotEmpty()) {
            item {
                TextButton(onClick = { showHidden = !showHidden }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text((if (showHidden) "▲ " else "▼ ") + "숨긴 항목 ${data.hiddenRecurring.size}개", color = Gray)
                }
            }
            if (showHidden) {
                items(data.hiddenRecurring, key = { "h|" + it.id }) { r ->
                    RecRow(r, data, action = "다시 보이기", dim = true) { Store.unhideRecurring(ctx, r.id); onChanged() }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecRow(r: Recurring, data: BookData, action: String, dim: Boolean = false, onAction: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${r.day}일", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (dim) Gray else catColor(r.cat), modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(r.merchant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (dim) Gray else androidx.compose.ui.graphics.Color.Unspecified)
                Text(
                    listOf(
                        data.cardName(r.card), category(r.cat).name,
                        if (r.auto) "자동납부" else "최근 1년 ${r.months}번",
                        "마지막 ${dateText(r.lastTime)}",
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(won(r.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (dim) Gray else androidx.compose.ui.graphics.Color.Unspecified)
        }
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            TextButton(onClick = onAction) { Text(action, fontSize = 12.sp, color = Gray) }
        }
        HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
