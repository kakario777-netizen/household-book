package com.kakario.householdbook.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.Identity
import com.kakario.householdbook.Green
import com.kakario.householdbook.backup.BackupScheduler
import com.kakario.householdbook.backup.DriveBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hourText(h: Int) = when (h) {
    0 -> "밤 12시"
    in 1..11 -> "오전 ${h}시"
    12 -> "낮 12시"
    else -> "오후 ${h - 12}시"
}

/** 설정 > 구글 드라이브 백업 */
@Composable
fun BackupSection(onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(DriveBackup.account(ctx)) }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(DriveBackup.status(ctx)) }
    var hour by remember { mutableIntStateOf(DriveBackup.hour(ctx)) }
    var pickHour by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<DriveBackup.FileInfo>?>(null) }
    var restoreTarget by remember { mutableStateOf<DriveBackup.FileInfo?>(null) }
    var restoreToken by remember { mutableStateOf<String?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var afterConsent by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val act = afterConsent
        afterConsent = null
        if (res.resultCode == Activity.RESULT_OK && act != null) {
            val token = runCatching {
                Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(res.data).accessToken
            }.getOrNull()
            if (token != null) act(token) else { busy = null; status = "구글 연결에 실패했어요" }
        } else {
            busy = null; status = "구글 연결을 취소했어요"
        }
    }

    /** 토큰 받기 (처음이거나 풀렸으면 구글 동의 화면이 뜸) */
    fun withToken(mail: String, label: String, act: (String) -> Unit) {
        busy = label
        Identity.getAuthorizationClient(ctx).authorize(DriveBackup.request(mail))
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    afterConsent = act
                    consent.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    val t = r.accessToken
                    if (t != null) act(t) else { busy = null; status = "구글 연결에 실패했어요" }
                }
            }
            .addOnFailureListener { e ->
                busy = null
                status = "구글 연결 실패: " + (e.message ?: "계정을 확인해 주세요") +
                    "\n(이 폰에 로그인된 구글 계정인지 확인해 주세요)"
            }
    }

    fun doBackup(token: String, mail: String) {
        scope.launch {
            val msg = try {
                val name = withContext(Dispatchers.IO) { DriveBackup.backupNow(ctx, token) }
                DriveBackup.saveStatus(ctx, true, "${BackupScheduler.timeText(System.currentTimeMillis())} 백업 완료 ($name)")
                if (account != mail) {
                    DriveBackup.setAccount(ctx, mail); account = mail
                }
                BackupScheduler.schedule(ctx)
                DriveBackup.status(ctx)
            } catch (e: Exception) {
                "백업 실패: ${e.message ?: e.javaClass.simpleName}"
            }
            status = msg; busy = null
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (account.isEmpty()) {
            Text(
                "구글 계정을 넣으면 매일 ${hourText(hour)}에 내 구글 드라이브 '가계부 백업' 폴더에 자동으로 저장돼요. " +
                    "앱은 자기가 만든 백업 파일만 볼 수 있어요.",
                fontSize = 12.sp, color = Gray,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim().take(80) },
                label = { Text("구글 계정 (예: abc@gmail.com)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = busy == null && email.contains("@"),
                onClick = { val m = email; withToken(m, "연결하고 첫 백업 중…") { doBackup(it, m) } },
            ) { Text("연결하고 지금 백업") }
        } else {
            Text("연결된 계정: $account", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().clickable { pickHour = true }.padding(vertical = 8.dp)) {
                Text("자동 백업 시각", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("매일 ${hourText(hour)} ›", fontSize = 14.sp, color = Green)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = busy == null, onClick = { withToken(account, "백업 중…") { doBackup(it, account) } }) {
                    Text("지금 백업")
                }
                OutlinedButton(enabled = busy == null, onClick = {
                    withToken(account, "백업 목록 불러오는 중…") { token ->
                        scope.launch {
                            try {
                                files = withContext(Dispatchers.IO) { DriveBackup.list(ctx, token) }
                                restoreToken = token
                            } catch (e: Exception) { status = "목록을 못 불러왔어요: ${e.message}" }
                            busy = null
                        }
                    }
                }) { Text("드라이브에서 복원") }
            }
            TextButton(onClick = { confirmDisconnect = true }) { Text("연결 끊기", color = Gray, fontSize = 13.sp) }
        }
        busy?.let {
            Text(it, fontSize = 12.sp, color = Gray)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (status.isNotEmpty()) {
            Text(
                status, fontSize = 12.sp,
                color = if (status.contains("완료") || status.contains("복원했어요")) Gray else Color(0xFFB8641A),
                modifier = Modifier.padding(top = 4.dp),
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
                    if (list.isEmpty()) Text("드라이브에 백업이 없어요", color = Gray)
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
                    val token = restoreToken
                    restoreTarget = null
                    if (token != null) {
                        busy = "복원 중…"
                        scope.launch {
                            status = try {
                                withContext(Dispatchers.IO) { DriveBackup.restore(ctx, token, f.id) }
                                "${f.name} 으로 복원했어요"
                            } catch (e: Exception) { "복원 실패: ${e.message}" }
                            busy = null
                            onChanged()
                        }
                    }
                }) { Text("되돌리기") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("취소") } },
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("구글 드라이브 연결을 끊을까요?") },
            text = { Text("자동 백업이 멈춰요. 드라이브에 이미 있는 백업 파일은 그대로 남아요.") },
            confirmButton = {
                TextButton(onClick = {
                    DriveBackup.setAccount(ctx, ""); DriveBackup.saveStatus(ctx, false, "")
                    BackupScheduler.schedule(ctx)
                    account = ""; status = ""; confirmDisconnect = false
                }) { Text("끊기") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("취소") } },
        )
    }
}
