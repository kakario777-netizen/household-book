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
import androidx.compose.runtime.mutableIntStateOf
import com.kakario.householdbook.data.CASH
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.name.ifBlank { "(가게 이름 없음)" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (e.included) Color.Unspecified else Gray, modifier = Modifier.weight(1f, fill = false),
                )
                if (e.review) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "확인", fontSize = 10.sp, color = Color.White,
                        modifier = Modifier.background(Color(0xFFE08A2E), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    )
                }
            }
            val sub = buildList {
                if (showDate) add(dateText(t.time))
                if (!t.manual) add(timeText(t.time))
                add(data.cardName(t.card))
                add(category(e.cat).name)
                if (t.installment.isNotEmpty() && t.installment != "일시불") add(t.installment)
                if (t.kind.isNotEmpty()) add(t.kind)
                if (recurring) add("정기")
                if (e.amountFixed) add("금액 고침")
                if (!e.included) add("가계부 제외")
            }.joinToString(" · ")
            Text(sub, fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.memo.isNotEmpty()) Text("메모: " + e.memo, fontSize = 12.sp, color = Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun Choice(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
}

/**
 * 결제(문자)별 수정: 분류 / 가계부 합산 / 가게 이름 / 금액 / 정기결제 / 메모.
 * 분류·합산·이름·정기결제는 '이 결제만' 또는 '이 가게 앞으로 계속' 고를 수 있음.
 */
@Composable
fun EditDialog(e: Entry, data: BookData, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val t = e.t
    val hasName = t.merchant.isNotBlank()
    val isRec = data.recurring.any { it.id == e.merchantId }

    var cat by remember { mutableStateOf(e.cat) }
    var catForShop by remember { mutableStateOf(hasName) }
    // 합산: 0 넣기 / 1 이 결제만 빼기 / 2 이 가게 앞으로 계속 빼기
    var incMode by remember { mutableIntStateOf(if (e.shopExcluded && !e.included) 2 else if (e.included) 0 else 1) }
    var name by remember { mutableStateOf(e.name) }
    var amount by remember { mutableStateOf(t.amount.toString()) }
    var recurring by remember { mutableStateOf(isRec) }
    var memo by remember { mutableStateOf(e.memo) }
    var showRaw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name.ifBlank { "(가게 이름 없음)" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    (if (t.cancel) "취소 -" else "") + won(t.amount) + "  ·  " + data.cardName(t.card) +
                        "  ·  " + dateText(t.time) + (if (t.manual) "" else " " + timeText(t.time)),
                    fontSize = 14.sp, color = Gray,
                )
                if (e.review) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            t.merchant.isBlank() -> "가게 이름을 못 읽은 결제예요. 맞는지 확인해 주세요."
                            t.defaultExcluded -> "'${t.kind}' 문자라 기본으로 합계에서 뺐어요. 실제 결제면 넣어 주세요."
                            else -> "'${t.kind}' 문자예요. 금액이 맞는지 확인해 주세요."
                        },
                        fontSize = 13.sp, color = Color(0xFFB8641A),
                    )
                }

                Label("분류")
                CategoryPicker(cat) { cat = it }
                if (hasName) {
                    Choice(catForShop, "이 가게는 다음에도 이 분류로") { catForShop = true }
                    Choice(!catForShop, "이 결제만") { catForShop = false }
                }

                Label("가계부 합계")
                Choice(incMode == 0, "합계에 넣기") { incMode = 0 }
                Choice(incMode == 1, "이 결제만 빼기") { incMode = 1 }
                if (hasName) Choice(incMode == 2, "이 가게는 다음에도 계속 빼기") { incMode = 2 }

                if (hasName) {
                    Label("가게 이름 (다음에도 이 이름으로)")
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { recurring = !recurring }) {
                        Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                        Text("매달 나가는 정기결제로 보기", fontSize = 14.sp)
                    }
                }

                Label("금액 고치기 (이 결제만)")
                OutlinedTextField(
                    value = amount, onValueChange = { amount = digits(it).take(10) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("원") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Label("메모")
                OutlinedTextField(
                    value = memo, onValueChange = { memo = it.take(60) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (t.raw.isNotEmpty()) {
                    TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "문자 원문 닫기" else "문자 원문 보기") }
                    if (showRaw) {
                        Text(
                            t.raw, fontSize = 12.sp, color = Gray,
                            modifier = Modifier.background(LightGray, RoundedCornerShape(8.dp)).padding(10.dp),
                        )
                    }
                }
                if (t.manual) {
                    TextButton(onClick = { Book.deleteManual(ctx, t.key); onChanged(); onDismiss() }) {
                        Text("이 항목 지우기", color = Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 분류
                if (cat != e.cat || (hasName && catForShop && Store.loadRules(ctx)[e.merchantId] != cat)) {
                    if (catForShop && hasName) Store.setRule(ctx, t.merchant, cat) else Store.setTxnCat(ctx, t.key, cat)
                }
                // 합산
                when (incMode) {
                    0 -> { if (e.shopExcluded) Store.setShopExclude(ctx, t.merchant, false); Store.setInclude(ctx, t, true) }
                    1 -> Store.setInclude(ctx, t, false)
                    2 -> { Store.setShopExclude(ctx, t.merchant, true); Store.setInclude(ctx, t, false) }
                }
                // 이름·정기결제
                if (hasName) {
                    if (name != e.name) Store.setName(ctx, t.merchant, name)
                    if (recurring != isRec) {
                        if (recurring) { Store.setForcedRecurring(ctx, e.merchantId, true); Store.unhideRecurring(ctx, e.merchantId) }
                        else { Store.setForcedRecurring(ctx, e.merchantId, false); Store.hideRecurring(ctx, e.merchantId) }
                    }
                }
                // 금액
                val a = amount.toLongOrNull()
                if (a != null && a > 0 && a != t.amount) {
                    val orig = Store.loadTxns(ctx).firstOrNull { it.key == t.key }?.amount
                    Store.setAmount(ctx, t.key, if (a == orig) null else a)
                }
                if (memo != e.memo) Store.setMemo(ctx, t.key, memo)
                Store.markReviewed(ctx, t.key)
                onChanged(); onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 현금 등 직접 넣기 (읽지 못한 문자에서 넣을 때는 금액·카드·시각이 미리 채워짐) */
@Composable
fun AddDialog(
    dayTime: Long, onDismiss: () -> Unit, onChanged: () -> Unit,
    exactTime: Long? = null, initAmount: Long = 0, card: String = CASH, raw: String = "", onAdded: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var amount by remember { mutableStateOf(if (initAmount > 0) initAmount.toString() else "") }
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("etc") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${dateText(exactTime ?: dayTime)} 가계부에 넣기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (raw.isNotEmpty()) {
                    Text(
                        raw, fontSize = 12.sp, color = Gray, maxLines = 8, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().background(LightGray, RoundedCornerShape(8.dp)).padding(10.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
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
                    Book.addManual(ctx, exactTime ?: (dayTime + 12 * 3600 * 1000L), name, amount.toLong(), cat, card, raw)
                    onAdded(); onChanged(); onDismiss()
                },
            ) { Text("넣기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
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

