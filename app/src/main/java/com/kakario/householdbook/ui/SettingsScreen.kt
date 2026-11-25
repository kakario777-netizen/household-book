package com.kakario.householdbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import com.kakario.householdbook.PALETTES
import com.kakario.householdbook.ThemeState
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
    var page by rememberSaveable { mutableStateOf("main") }
    var confirmResync by remember { mutableStateOf(false) }
    val updater = remember { Updater.get(ctx) }

    if (page == "cards") {
        BackHandler { page = "main" }
        CardsPage(data, hasSms, onBack = { page = "main" }, onChanged = onChanged)
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (!hasSms) item { PermissionCard(onAskSms, onOpenSettings) }

        // 카드 설정 (한 단계 들어가서 켜고 끄기)
        item {
            Row(
                Modifier.fillMaxWidth().clickable { page = "cards" }.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("카드 설정", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "켜진 카드: " + data.cards.filter { it.enabled }.joinToString(", ") { it.name }.ifEmpty { "없음" },
                        fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("›", fontSize = 22.sp, color = Gray)
            }
            HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // 화면 색감
        item { SectionTitle("화면 색감") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PALETTES.forEach { p ->
                    val on = ThemeState.palette.id == p.id
                    Column(
                        Modifier.weight(1f)
                            .background(Color(p.card), RoundedCornerShape(12.dp))
                            .border(if (on) 2.dp else 0.8.dp, if (on) Color(p.primary) else LightGray, RoundedCornerShape(12.dp))
                            .clickable { ThemeState.set(ctx, p); onChanged() }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(26.dp).background(Color(p.primary), CircleShape))
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        item { SectionTitle("구글 드라이브 백업") }
        item { BackupSection(onChanged) }

        item { SectionTitle("문자") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("문자 권한: " + if (hasSms) "허용됨" else "없음", fontSize = 14.sp)
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
                Text("결제 내역은 이 폰 안에만 저장되고, 서버나 구글 백업으로 보내지 않아요.", fontSize = 12.sp, color = Gray)
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmResync) {
        AlertDialog(
            onDismissRequest = { confirmResync = false },
            title = { Text("최근 1년 문자를 다시 읽을까요?") },
            text = { Text("결제 문자를 처음부터 다시 읽어요. 이미 있는 내역은 겹치지 않게 합치고, 고친 분류는 그대로 둬요.") },
            confirmButton = {
                TextButton(onClick = { Store.resetSync(ctx); confirmResync = false; onResync() }) { Text("다시 읽기") }
            },
            dismissButton = { TextButton(onClick = { confirmResync = false }) { Text("취소") } },
        )
    }
}

/** 카드 설정 화면 (설정 안의 두 번째 단계). 켜고 끌 때 한 번 더 물어봄 */
@Composable
private fun CardsPage(data: BookData, hasSms: Boolean, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val cards = data.cards
    var editing by remember { mutableStateOf<CardConf?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var toggling by remember { mutableStateOf<CardConf?>(null) }

    fun save(list: List<CardConf>) { Store.saveCards(ctx, list); onChanged() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", fontSize = 26.sp, color = Gray, modifier = Modifier.padding(horizontal = 8.dp))
                Text("카드 설정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "켜진 카드의 결제 문자만 가계부에 들어가요. 카드를 누르면 이름·문자 번호를 고칠 수 있어요.",
                fontSize = 12.sp, color = Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(cards, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable { isNew = false; editing = c }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 15.sp, color = if (c.enabled) Color.Unspecified else Gray)
                    Text(
                        "문자 번호 " + c.numbers.joinToString(", ").ifEmpty { "없음 (문자 내용의 카드 이름으로 찾음)" },
                        fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = c.enabled, onCheckedChange = { toggling = c })
            }
            HorizontalDivider(color = LightGray, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            TextButton(onClick = {
                isNew = true
                editing = CardConf("c${System.currentTimeMillis()}", "", "", emptyList(), true)
            }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("+ 카드 추가") }
        }
    }

    toggling?.let { c ->
        val turnOn = !c.enabled
        AlertDialog(
            onDismissRequest = { toggling = null },
            title = { Text("${c.name}를 ${if (turnOn) "켤" else "끌"}까요?") },
            text = {
                Text(
                    if (turnOn) "이 카드의 결제 문자를 다시 가계부에 넣어요."
                    else "끄면 앞으로 이 카드 결제 문자는 가계부에 안 들어가요. 이미 들어간 내역은 그대로 남아요."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    save(cards.map { if (it.id == c.id) it.copy(enabled = turnOn) else it })
                    toggling = null
                }) { Text(if (turnOn) "켜기" else "끄기") }
            },
            dismissButton = { TextButton(onClick = { toggling = null }) { Text("취소") } },
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
