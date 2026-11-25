package com.kakario.householdbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.CardConf
import com.kakario.householdbook.data.Store
import com.kakario.householdbook.data.digits
import com.kakario.householdbook.sms.SmsReader
import com.kakario.householdbook.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    data: BookData, hasSms: Boolean, loading: Boolean,
    onAskSms: () -> Unit, onOpenSettings: () -> Unit, onResync: () -> Unit, onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<CardConf?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var confirmResync by remember { mutableStateOf(false) }
    val updater = remember { Updater.get(ctx) }
    val cards = data.cards

    fun save(list: List<CardConf>) { Store.saveCards(ctx, list); onChanged() }

    LazyColumn(Modifier.fillMaxSize()) {
        if (!hasSms) item { PermissionCard(onAskSms, onOpenSettings) }

        item { SectionTitle("카드", "켜진 카드의 결제 문자만 읽어요") }
        items(cards, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable { isNew = false; editing = c }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 15.sp)
                    Text(
                        "문자 번호 " + c.numbers.joinToString(", ").ifEmpty { "없음 (문자 내용의 카드 이름으로 찾음)" },
                        fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = c.enabled, onCheckedChange = { on -> save(cards.map { if (it.id == c.id) it.copy(enabled = on) else it }) })
            }
            HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            TextButton(onClick = {
                isNew = true
                editing = CardConf("c${System.currentTimeMillis()}", "", "", emptyList(), true)
            }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("+ 카드 추가") }
        }

        item { SectionTitle("문자") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "문자 권한: " + if (hasSms) "허용됨" else "없음", fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "카드 번호를 바꾸거나 추가했으면 최근 1년 문자를 다시 읽어 주세요. 고친 분류·제외 표시는 그대로 남아요.",
                    fontSize = 12.sp, color = Gray,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmResync = true }, enabled = hasSms && !loading) {
                    Text(if (loading) "읽는 중…" else "최근 1년 문자 다시 읽기")
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }

        item { SectionTitle("앱") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("지금 빌드 #${updater.currentBuild}", fontSize = 14.sp)
                Text("앱을 켤 때마다 새 버전을 자동으로 확인해요", fontSize = 12.sp, color = Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { updater.check(quiet = false) }) { Text("업데이트 확인") }
                Spacer(Modifier.height(14.dp))
                Text(
                    "결제 내역은 이 폰 안에만 저장되고, 서버나 구글 백업으로 보내지 않아요.",
                    fontSize = 12.sp, color = Gray,
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmResync) {
        AlertDialog(
            onDismissRequest = { confirmResync = false },
            title = { Text("문자 다시 읽기") },
            text = { Text("최근 1년 결제 문자를 처음부터 다시 읽어요. 이미 있는 내역은 겹치지 않게 합쳐요.") },
            confirmButton = {
                TextButton(onClick = { Store.resetSync(ctx); confirmResync = false; onResync() }) { Text("읽기") }
            },
            dismissButton = { TextButton(onClick = { confirmResync = false }) { Text("취소") } },
        )
    }

    editing?.let { c ->
        CardDialog(
            card = c, isNew = isNew, hasSms = hasSms,
            onDismiss = { editing = null },
            onSave = { nc ->
                save(if (isNew) cards + nc else cards.map { if (it.id == nc.id) nc else it })
                editing = null
            },
            onDelete = if (isNew) null else ({ save(cards.filterNot { it.id == c.id }); editing = null }),
        )
    }
}

@Composable
private fun CardDialog(
    card: CardConf, isNew: Boolean, hasSms: Boolean,
    onDismiss: () -> Unit, onSave: (CardConf) -> Unit, onDelete: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(card.name) }
    var keyword by remember { mutableStateOf(card.keyword) }
    var numbers by remember { mutableStateOf(card.numbers.joinToString(", ")) }
    var found by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    var searching by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "카드 추가" else card.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("카드 이름 (예: 신한카드)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keyword, { keyword = it.take(10) }, label = { Text("문자 속 카드사 단어 (예: 신한)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(numbers, { numbers = it }, label = { Text("문자 보내는 번호 (쉼표로 여러 개)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                TextButton(
                    enabled = hasSms && keyword.isNotBlank() && !searching,
                    onClick = {
                        searching = true
                        scope.launch {
                            found = withContext(Dispatchers.IO) { SmsReader.findSenders(ctx, keyword.trim()) }
                            searching = false
                        }
                    },
                ) { Text(if (searching) "찾는 중…" else "최근 3달 문자에서 번호 찾기") }
                found?.let { list ->
                    if (list.isEmpty()) Text("'$keyword' 결제 문자를 찾지 못했어요", fontSize = 12.sp, color = Gray)
                    list.take(5).forEach { (n, cnt) ->
                        Text(
                            "+ $n (${cnt}통)", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val cur = numbers.split(",").map { digits(it) }.filter { it.isNotEmpty() }
                                if (n !in cur) numbers = (cur + n).joinToString(", ")
                            }.padding(vertical = 6.dp),
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("이 카드 지우기", color = Red) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        card.copy(
                            name = name.trim(), keyword = keyword.trim(),
                            numbers = numbers.split(",").map { digits(it) }.filter { it.length >= 4 }.distinct(),
                        )
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
