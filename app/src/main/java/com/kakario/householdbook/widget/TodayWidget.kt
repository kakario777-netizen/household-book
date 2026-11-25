package com.kakario.householdbook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.widget.RemoteViews
import com.kakario.householdbook.MainActivity
import com.kakario.householdbook.R
import com.kakario.householdbook.data.Book
import com.kakario.householdbook.data.BookData
import com.kakario.householdbook.data.dayStart
import com.kakario.householdbook.data.num
import com.kakario.householdbook.data.ymEnd
import com.kakario.householdbook.data.ymOf
import com.kakario.householdbook.data.ymStart
import java.util.Calendar
import java.util.Locale

/** 홈 화면 위젯: "오늘 12,300원 쓰셨어요" + 이번 달 합계 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // 30분마다 + 위젯을 놓을 때: 새 문자 읽고 갱신 (채팅+ 문자처럼 수신 알림이 안 오는 경우까지 잡음)
        val pending = goAsync()
        Thread {
            try {
                runCatching { Book.sync(context) }
                render(context, Book.load(context))
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        /** 앱에서 데이터가 바뀌었을 때 (문자는 이미 읽은 상태) */
        fun render(context: Context, data: BookData) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val today = data.spentBetween(dayStart(now), now + 60_000)
            val ym = ymOf(now)
            val month = data.spentBetween(ymStart(ym), ymEnd(ym))
            val c = Calendar.getInstance()
            val title = String.format(Locale.KOREA, "오늘 %d월 %d일", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.w_title, title)
                setTextViewText(R.id.w_amount, if (today == 0L) "아직 안 쓰셨어요" else "${num(today)}원 쓰셨어요")
                setTextViewText(R.id.w_month, "이번 달 ${num(month)}원")
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }

        fun refreshAsync(context: Context) {
            val app = context.applicationContext
            Thread { runCatching { render(app, Book.load(app)) } }.start()
        }
    }
}

/** 문자가 오면 몇 초 뒤(문자함에 저장된 다음) 읽어서 위젯 갱신 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                Thread.sleep(4000)
                runCatching { Book.sync(app) }
                runCatching { TodayWidget.render(app, Book.load(app)) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
