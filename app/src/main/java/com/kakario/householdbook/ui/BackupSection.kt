package com.kakario.householdbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.householdbook.Green
import com.kakario.householdbook.backup.BackupScheduler
import com.kakario.householdbook.backup.DriveBackup
import com.kakario.householdbook.backup.FtpBackup
import com.kakario.householdbook.backup.FtpConf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hourText(h: Int) = when (h) {
    0 -> "밤 12시"
    in 1..11 -> "오전 ${h}시"
    12 -> "낮 12시"
    else -> "오후 ${h - 12}시"
}

/** 설정 > 백업: 구글 드라이브(준비 중) + FTP 서버 동기화 */
@Composable
fun BackupSection(onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var notReady by remember { mutableStateOf(false) }

    var conf by remember { mutableStateOf(FtpBackup.load(ctx)) }
    var editServer by remember { mutableStateOf(false) }
    var pickFolder by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(DriveBackup.status(ctx)) }
    var hour by remember { mutableIntStateOf(DriveBackup.hour(ctx)) }
    var pickHour by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<FtpBackup.BackupFile>?>(null) }
    var restoreTarget by remember { mutableStateOf<FtpBackup.BackupFile?>(null) }

    fun run(label: String, work: () -> String, after: () -> Unit = {}) {
        busy = label
        scope.launch {
            status = try { withContext(Dispatchers.IO) { work() } } catch (e: Exception) { e.message ?: "오류가 났어요" }
            busy = null
            after()
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        // ── 구글 드라이브 (로그인 창만, 아직 준비 중) ──
        Text("구글 드라이브", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim().take(80) },
            label = { Text("구글 계정 (예: abc@gmail.com)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { notReady = true }) { Text("구글 계정 연결") }

        HorizontalDivider(color = LightGray, modifier = Modifier.padding(vertical = 14.dp))

        // ── FTP 서버 동기화 ──
        Text("FTP 서버 동기화", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        val c = conf
        if (c == null) {
            Text(
                "집 NAS 같은 FTP 서버를 등록하면 매일 ${hourText(hour)}에 고른 폴더로 가계부를 자동 백업해요. " +
                    "접속 정보는 이 폰에만 암호화해서 저장돼요.",
                fontSize = 12.sp, color = Gray,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { editServer = true }) { Text("FTP 서버 등록") }
        } else {
            InfoRow("서버", "${c.host}:${c.port}" + (if (c.secure) " (보안)" else "") + " · ${c.encoding}") { editServer = true }
            InfoRow("백업 폴더", c.folder.ifEmpty { "골라 주세요" }) { pickFolder = true }
            InfoRow("자동 백업", if (c.folder.isEmpty()) "폴더를 고르면 켜져요" else "매일 ${hourText(hour)}") { pickHour = true }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = busy == null && c.folder.isNotEmpty(), onClick = {
                    run("백업 중…", {
                        val name = FtpBackup.backupNow(ctx)
                        DriveBackup.saveStatus(ctx, true, "${BackupScheduler.timeText(System.currentTimeMillis())} 백업 완료 ($name)")
                        DriveBackup.status(ctx)
                    })
                }) { Text("지금 백업") }
                OutlinedButton(enabled = busy == null && c.folder.isNotEmpty(), onClick = {
                    busy = "백업 목록 불러오는 중…"
                    scope.launch {
                        try { files = withContext(Dispatchers.IO) { FtpBackup.list(ctx) } } catch (e: Exception) { status = e.message ?: "목록을 못 불러왔어요" }
                        busy = null
                    }
                }) { Text("복원") }
            }
            TextButton(onClick = { confirmRemove = true }) { Text("서버 등록 지우기", color = Gray, fontSize = 13.sp) }
        }
        busy?.let {
            Text(it, fontSize = 12.sp, color = Gray)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (status.isNotEmpty()) {
            Text(
                status, fontSize = 12.sp,
                color = if (status.contains("완료") || status.contains("복원했어요") || status.contains("저장했어요")) Gray else Color(0xFFB8641A),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (notReady) {
        AlertDialog(
            onDismissRequest = { notReady = false },
            title = { Text("준비 중이에요") },
            text = { Text("구글 드라이브 백업은 아직 미구현이에요.\n지금은 아래 'FTP 서버 동기화'로 백업해 주세요.") },
            confirmButton = { TextButton(onClick = { notReady = false }) { Text("확인") } },
        )
    }

    if (editServer) {
        FtpServerDialog(
            initial = conf,
            onDismiss = { editServer = false },
            onSaved = { nc ->
                FtpBackup.save(ctx, nc); conf = nc; editServer = false
                BackupScheduler.schedule(ctx)
                status = "서버를 저장했어요"
                if (nc.folder.isEmpty()) pickFolder = true
            },
        )
    }

    if (pickFolder) {
        conf?.let { cur ->
            FtpFolderDialog(
                conf = cur,
                onDismiss = { pickFolder = false },
                onPick = { path ->
                    val nc = cur.copy(folder = path)
                    FtpBackup.save(ctx, nc); conf = nc; pickFolder = false
                    BackupScheduler.schedule(ctx)
                    status = "백업 폴더를 저장했어요: $path"
                },
            )
        }
    }

    if (pickHour) {
        AlertDialog(
            onDismissRequest = { pickHour = false },
            title = { Text("자동 백업 시각") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    (0..23).forEach { h ->
                        Text(
                            hourText(h), fontSize = 15.sp,
                            color = if (h == hour) Green else Color.Unspecified,
                            fontWeight = if (h == hour) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable {
                                hour = h; DriveBackup.setHour(ctx, h); BackupScheduler.schedule(ctx); pickHour = false
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickHour = false }) { Text("닫기") } },
        )
    }

    files?.let { list ->
        AlertDialog(
            onDismissRequest = { files = null },
            title = { Text("복원할 백업 고르기") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("백업 폴더에 백업 파일이 없어요", color = Gray)
                    list.forEach { f ->
                        Text(
                            f.name.removePrefix("가계부_").removeSuffix(".json") + "  (${f.size / 1024}KB)",
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable { restoreTarget = f; files = null }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { files = null }) { Text("닫기") } },
        )
    }

    restoreTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("이 백업으로 되돌릴까요?") },
            text = { Text("${f.name}\n\n지금 폰에 있는 가계부 내역·설정이 이 백업 내용으로 바뀌어요.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = null
                    run("복원 중…", { FtpBackup.restore(ctx, f.name); "${f.name} 으로 복원했어요" }, after = onChanged)
                }) { Text("되돌리기") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("취소") } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("FTP 서버 등록을 지울까요?") },
            text = { Text("자동 백업이 멈춰요. 서버에 이미 있는 백업 파일은 그대로 남아요.") },
            confirmButton = {
                TextButton(onClick = {
                    FtpBackup.save(ctx, null); conf = null; confirmRemove = false
                    BackupScheduler.schedule(ctx)
                    DriveBackup.saveStatus(ctx, false, ""); status = ""
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 14.sp, color = Green, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(" ›", fontSize = 16.sp, color = Gray)
    }
}

/** FTP 서버 등록·수정 (접속 확인까지) */
@Composable
private fun FtpServerDialog(initial: FtpConf?, onDismiss: () -> Unit, onSaved: (FtpConf) -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 21).toString()) }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pw by remember { mutableStateOf(initial?.password ?: "") }
    var enc by remember { mutableStateOf(initial?.encoding ?: "UTF-8") }
    var secure by remember { mutableStateOf(initial?.secure ?: false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "FTP 서버 등록" else "FTP 서버 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(host, { host = it.trim().take(100) }, label = { Text("서버 주소 (예: mynas.iptime.org)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("포트") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(user, { user = it.trim().take(60) }, label = { Text("아이디 (비우면 익명)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pw, { pw = it.take(100) }, label = { Text("비밀번호") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("한글 폴더 이름 인코딩", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(enc == "UTF-8", { enc = "UTF-8" }); Text("UTF-8", fontSize = 14.sp, modifier = Modifier.clickable { enc = "UTF-8" })
                    Spacer(Modifier.width(12.dp))
                    RadioButton(enc == "EUC-KR", { enc = "EUC-KR" }); Text("EUC-KR", fontSize = 14.sp, modifier = Modifier.clickable { enc = "EUC-KR" })
                }
                Text("폴더 이름이 깨져 보이면 EUC-KR로 바꿔 주세요", fontSize = 11.sp, color = Gray)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { secure = !secure }) {
                    Checkbox(secure, { secure = it }); Text("보안 연결 (FTPS) — 서버가 지원하면 켜기", fontSize = 13.sp)
                }
                if (testing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                if (error.isNotEmpty()) Text(error, fontSize = 12.sp, color = Red)
            }
        },
        confirmButton = {
            TextButton(enabled = host.isNotBlank() && !testing, onClick = {
                val nc = FtpConf(host, port.toIntOrNull() ?: 21, user, pw, enc, secure, initial?.folder ?: "")
                testing = true; error = ""
                scope.launch {
                    val err = withContext(Dispatchers.IO) { runCatching { FtpBackup.home(nc) }.exceptionOrNull() }
                    testing = false
                    if (err == null) onSaved(nc) else error = err.message ?: "접속하지 못했어요"
                }
            }) { Text("접속 확인 후 저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 서버 폴더 직접 고르기 */
@Composable
private fun FtpFolderDialog(conf: FtpConf, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(conf.folder) }
    var dirs by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf<String?>(null) }

    fun open(p: String?) {
        loading = true; error = ""
        scope.launch {
            try {
                val target = p ?: withContext(Dispatchers.IO) { FtpBackup.home(conf) }
                dirs = withContext(Dispatchers.IO) { FtpBackup.listDirs(conf, target) }
                path = target
            } catch (e: Exception) { error = e.message ?: "폴더를 못 열었어요" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { open(conf.folder.ifEmpty { null }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업 폴더 고르기") },
        text = {
            Column {
                Text(path.ifEmpty { "…" }, fontSize = 13.sp, color = Green, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                if (error.isNotEmpty()) Text(error, fontSize = 12.sp, color = Red)
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (path.isNotEmpty() && path != "/") {
                        Text("‹ 위 폴더로", fontSize = 15.sp, color = Gray,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !loading) { open(FtpBackup.parent(path)) }.padding(vertical = 10.dp))
                    }
                    dirs.forEach { d ->
                        Text("▸ $d", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !loading) { open(FtpBackup.join(path, d)) }.padding(vertical = 10.dp))
                    }
                    if (!loading && dirs.isEmpty() && error.isEmpty()) Text("하위 폴더가 없어요", fontSize = 13.sp, color = Gray)
                }
                TextButton(enabled = !loading && path.isNotEmpty(), onClick = { newName = "" }) { Text("+ 새 폴더") }
            }
        },
        confirmButton = { TextButton(enabled = !loading && path.isNotEmpty(), onClick = { onPick(path) }) { Text("이 폴더로 선택") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    newName?.let { n ->
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text("새 폴더") },
            text = { OutlinedTextField(n, { newName = it.take(40) }, singleLine = true, label = { Text("폴더 이름 (예: 가계부 백업)") }) },
            confirmButton = {
                TextButton(enabled = n.isNotBlank() && !n.contains('/'), onClick = {
                    val target = FtpBackup.join(path, n.trim())
                    newName = null; loading = true
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { runCatching { FtpBackup.makeDir(conf, target) }.exceptionOrNull() }
                        if (err != null) { error = err.message ?: "폴더를 못 만들었어요"; loading = false } else open(target)
                    }
                }) { Text("만들기") }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("취소") } },
        )
    }
}
