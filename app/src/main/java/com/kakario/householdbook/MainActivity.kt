package com.kakario.householdbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kakario.householdbook.data.Book
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.ymOf
import com.kakario.householdbook.ui.CalendarScreen
import com.kakario.householdbook.ui.RecurringScreen
import com.kakario.householdbook.ui.SettingsScreen
import com.kakario.householdbook.ui.StatsScreen
import com.kakario.householdbook.ui.UpdateDialog
import com.kakario.householdbook.update.Updater
import com.kakario.householdbook.widget.TodayWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var data by mutableStateOf(BookData.EMPTY)
    private var loading by mutableStateOf(false)
    private var hasSms by mutableStateOf(false)
    private var askedOnce = false

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasSms = smsGranted()
        refresh(sync = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasSms = smsGranted()
        ThemeState.palette = ThemeState.load(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green)) {
                App()
            }
        }
        Updater.get(this).check(quiet = true)
        com.kakario.householdbook.backup.BackupScheduler.catchUp(this)
    }

    override fun onResume() {
        super.onResume()
        hasSms = smsGranted()
        if (!hasSms && !askedOnce) {
            askedOnce = true
            requestSms()
        } else {
            refresh(sync = true)
        }
    }

    private fun smsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun requestSms() {
        val perms = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())
    }

    fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** sync = 새 문자 읽기까지. 끝나면 화면·위젯 갱신 */
    fun refresh(sync: Boolean) {
        lifecycleScope.launch {
            if (sync) loading = true
            val d = withContext(Dispatchers.IO) {
                if (sync) runCatching { Book.sync(this@MainActivity) }
                Book.load(this@MainActivity)
            }
            data = d
            loading = false
            withContext(Dispatchers.IO) { runCatching { TodayWidget.render(this@MainActivity, d) } }
        }
    }

    @Composable
    private fun App() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var ym by rememberSaveable { mutableIntStateOf(ymOf(System.currentTimeMillis())) }
        val updater = Updater.get(this)
        val upState by updater.state.collectAsState()

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("달력", "통계", "정기결제", "설정").forEachIndexed { i, name ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                Box(
                                    Modifier.size(8.dp).background(
                                        if (tab == i) Green else Color(0xFFD0D5DA), CircleShape
                                    )
                                )
                            },
                            label = { Text(name) },
                        )
                    }
                }
            },
            containerColor = Color.White,
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> CalendarScreen(
                        data = data, ym = ym, onYm = { ym = it }, loading = loading, hasSms = hasSms,
                        onAskSms = { requestSms() }, onOpenSettings = { openAppSettings() },
                        onChanged = { refresh(sync = false) },
                    )
                    1 -> StatsScreen(data = data, ym = ym, onYm = { ym = it }, onChanged = { refresh(sync = false) })
                    2 -> RecurringScreen(data = data, onChanged = { refresh(sync = false) })
                    else -> SettingsScreen(
                        data = data, hasSms = hasSms, loading = loading,
                        onAskSms = { requestSms() }, onOpenSettings = { openAppSettings() },
                        onResync = { refresh(sync = true) }, onChanged = { refresh(sync = false) },
                    )
                }
            }
        }
        UpdateDialog(upState, updater)
    }
}
