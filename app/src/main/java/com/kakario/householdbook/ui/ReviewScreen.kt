package com.kakario.householdbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.Entry
import com.kakario.householdbook.data.Pending
import com.kakario.householdbook.data.Store
import com.kakario.householdbook.data.dateText
import com.kakario.householdbook.data.timeText
import com.kakario.householdbook.data.won

/** 확인 필요: 결제로 못 읽은 카드사 문자 + 애매하게 읽힌 결제 */
@Composable
fun ReviewScreen(data: BookData, onBack: () -> Unit, onChanged: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf<Pending?>(null) }
    var edit by remember { mutableStateOf<Entry?>(null) }
    var confirmIgnoreAll by remember { mutableStateOf(false) }
    val reviewEntries = data.entries.filter { it.review }.sortedByDescending { it.t.time }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", fontSize = 26.sp, color = Gray, modifier = Modifier.padding(horizontal = 8.dp))
                Text("확인 필요", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (data.pending.isEmpty() && reviewEntries.isEmpty()) {
                Text("확인할 문자가 없어요", color = Gray, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
        }

        if (data.pending.isNotEmpty()) {
            item { SectionTitle("결제로 못 읽은 카드사 문자", "${data.pending.size}건") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "결제면 '가계부에 넣기', 안내·광고 문자면 '무시'를 눌러 주세요.",
                        fontSize = 12.sp, color = Gray, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmIgnoreAll = true }) { Text("모두 무시", fontSize = 13.sp, color = Gray) }
                }
            }
            items(data.pending, key = { it.key }) { p ->
                PendingCard(p, data, onAdd = { adding = p }, onIgnore = { Store.markPendingDone(ctx, listOf(p.key)); onChanged() })
            }
        }

        if (reviewEntries.isNotEmpty()) {
            item { SectionTitle("확인이 필요한 결제", "${reviewEntries.size}건") }
            item {
                Text(
                    "가게 이름이 없거나, 결제 예정·이용금액 안내·해외 결제 문자예요. 눌러서 고치거나 '맞아요'를 눌러 주세요.",
                    fontSize = 12.sp, color = Gray, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(reviewEntries, key = { "r|" + it.t.key }) { e ->
                EntryRow(e, data, showDate = true) { edit = e }
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { edit = e }) { Text("고치기", fontSize = 13.sp) }
                    TextButton(onClick = { Store.markReviewed(ctx, e.t.key); onChanged() }) { Text("맞아요", fontSize = 13.sp) }
                }
                HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    adding?.let { p ->
        AddDialog(
            dayTime = p.time, exactTime = p.time, initAmount = p.guess, card = p.card, raw = p.body,
            onDismiss = { adding = null }, onChanged = onChanged,
            onAdded = { Store.markPendingDone(ctx, listOf(p.key)) },
        )
    }
    edit?.let { EditDialog(it, data, onDismiss = { edit = null }, onChanged = onChanged) }
    if (confirmIgnoreAll) {
        AlertDialog(
            onDismissRequest = { confirmIgnoreAll = false },
            title = { Text("읽지 못한 문자 ${data.pending.size}건을 모두 무시할까요?") },
            text = { Text("가계부에 넣지 않고 이 목록에서 지워요.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.markPendingDone(ctx, data.pending.map { it.key }); confirmIgnoreAll = false; onChanged()
                }) { Text("모두 무시") }
            },
            dismissButton = { TextButton(onClick = { confirmIgnoreAll = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun PendingCard(p: Pending, data: BookData, onAdd: () -> Unit, onIgnore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(LightGray, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${data.cardName(p.card)} · ${dateText(p.time)} ${timeText(p.time)}",
                fontSize = 13.sp, color = Gray, modifier = Modifier.weight(1f),
            )
            if (p.guess > 0) Text(won(p.guess), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            p.body, fontSize = 13.sp, maxLines = if (open) 30 else 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { open = !open },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text("가계부에 넣기", fontSize = 13.sp) }
            OutlinedButton(onClick = onIgnore) { Text("무시", fontSize = 13.sp) }
        }
    }
}
