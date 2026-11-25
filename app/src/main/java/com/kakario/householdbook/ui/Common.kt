package com.kakario.householdbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.CardBg
import com.kakario.householdbook.Green
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.CATEGORIES
import com.kakario.householdbook.data.Entry
import com.kakario.householdbook.data.Book
import com.kakario.householdbook.data.Store
import com.kakario.householdbook.data.category
import com.kakario.householdbook.data.dateText
import com.kakario.householdbook.data.digits
import com.kakario.householdbook.data.num
import com.kakario.householdbook.data.timeText
import com.kakario.householdbook.data.won
import com.kakario.householdbook.data.ymAdd
import com.kakario.householdbook.data.ymText

val Gray = Color(0xFF6B7280)
val LightGray = Color(0xFFF3F4F6)
val Red = Color(0xFFD9534F)
val Blue = Color(0xFF3B73C4)

fun catColor(id: String) = Color(category(id).color)

@Composable
fun MonthHeader(ym: Int, onYm: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { onYm(ymAdd(ym, -1)) }) { Text("◀", color = Gray) }
        Text(ymText(ym), fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        TextButton(onClick = { onYm(ymAdd(ym, 1)) }) { Text("▶", color = Gray) }
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

/** 결제 한 줄 */
@Composable
fun EntryRow(e: Entry, data: BookData, showDate: Boolean = false, recurring: Boolean = false, onClick: () -> Unit) {
    val t = e.t
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(catColor(e.cat))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant.ifBlank { "(가게 이름 없음)" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (e.included) Color.Unspecified else Gray,
            )
            val sub = buildList {
                if (showDate) add(dateText(t.time))
                if (!t.manual) add(timeText(t.time))
                add(data.cardName(t.card))
                add(category(e.cat).name)
                if (t.installment.isNotEmpty() && t.installment != "일시불") add(t.installment)
                if (t.kind.isNotEmpty()) add(t.kind)
                if (recurring) add("정기")
                if (!e.included) add("가계부 제외")
            }.joinToString(" · ")
            Text(sub, fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (t.cancel) "-" else "") + won(t.amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = when { !e.included -> Gray; t.cancel -> Blue; else -> Color.Unspecified },
            textDecoration = if (e.included) null else TextDecoration.LineThrough,
            maxLines = 1,
        )
    }
}

/** 분류 고르기 (2열) */
@Composable
fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CATEGORIES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    val on = c.id == selected
                    Row(
                        Modifier.weight(1f)
                            .heightIn(min = 36.dp)
                            .background(if (on) Color(c.color).copy(alpha = 0.18f) else LightGray, RoundedCornerShape(10.dp))
                            .border(if (on) 1.5.dp else 0.dp, if (on) Color(c.color) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onSelect(c.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(Color(c.color), 8)
                        Spacer(Modifier.width(6.dp))
                        Text(c.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 결제 눌렀을 때: 분류 바꾸기 / 가계부에서 빼기 / 원문 보기 */
@Composable
fun EditDialog(e: Entry, data: BookData, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf(e.cat) }
    var include by remember { mutableStateOf(e.included) }
    val hasName = e.t.merchant.isNotBlank()
    var forShop by remember { mutableStateOf(hasName) }
    var showRaw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.t.merchant.ifBlank { "(가게 이름 없음)" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    (if (e.t.cancel) "취소 -" else "") + won(e.t.amount) + "  ·  " + data.cardName(e.t.card) +
                        "  ·  " + dateText(e.t.time) + (if (e.t.manual) "" else " " + timeText(e.t.time)),
                    fontSize = 14.sp, color = Gray,
                )
                Spacer(Modifier.height(12.dp))
                Text("분류", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                CategoryPicker(cat) { cat = it }
                if (hasName) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { forShop = true }) {
                        RadioButton(selected = forShop, onClick = { forShop = true })
                        Text("이 가게는 앞으로도 이 분류로", fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { forShop = false }) {
                        RadioButton(selected = !forShop, onClick = { forShop = false })
                        Text("이 결제만", fontSize = 14.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { include = !include }) {
                    Checkbox(checked = include, onCheckedChange = { include = it })
                    Text("가계부 합계에 넣기", fontSize = 14.sp)
                }
                if (e.t.kind.isNotEmpty()) {
                    Text("문자 종류: ${e.t.kind}", fontSize = 12.sp, color = Gray)
                }
                if (e.t.raw.isNotEmpty()) {
                    TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "문자 원문 닫기" else "문자 원문 보기") }
                    if (showRaw) {
                        Text(
                            e.t.raw, fontSize = 12.sp, color = Gray,
                            modifier = Modifier.background(LightGray, RoundedCornerShape(8.dp)).padding(10.dp),
                        )
                    }
                }
                if (e.t.manual) {
                    TextButton(onClick = { Book.deleteManual(ctx, e.t.key); onChanged(); onDismiss() }) {
                        Text("이 항목 지우기", color = Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (cat != e.cat) {
                    if (forShop && hasName) Store.setRule(ctx, e.t.merchant, cat) else Store.setTxnCat(ctx, e.t.key, cat)
                }
                if (include != e.included) Store.setInclude(ctx, e.t, include)
                onChanged(); onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 현금 등 직접 넣기 */
@Composable
fun AddDialog(dayTime: Long, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("etc") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${dateText(dayTime)} 직접 넣기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = digits(it).take(10) },
                    label = { Text("금액 (원)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (amount.isNotEmpty()) Text(won(amount.toLong()), fontSize = 12.sp, color = Gray)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text("어디에 (예: 시장, 용돈)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                CategoryPicker(cat) { cat = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (amount.toLongOrNull() ?: 0L) > 0,
                onClick = {
                    Book.addManual(ctx, dayTime + 12 * 3600 * 1000L, name, amount.toLong(), cat)
                    onChanged(); onDismiss()
                },
            ) { Text("넣기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun SectionTitle(text: String, right: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (right.isNotEmpty()) Text(right, fontSize = 14.sp, color = Gray)
    }
}

@Composable
fun SummaryCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .background(CardBg, RoundedCornerShape(16.dp)).padding(16.dp)
    ) { content() }
}

@Composable
fun BigAmount(label: String, v: Long, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(label, fontSize = 13.sp, color = Gray)
    Text("${num(v)}원", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
}

