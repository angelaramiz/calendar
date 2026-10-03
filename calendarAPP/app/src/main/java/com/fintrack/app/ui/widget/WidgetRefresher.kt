package com.fintrack.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fintrack.app.MainActivity
import com.fintrack.app.R
import com.fintrack.app.domain.WidgetBalance
import com.fintrack.app.ui.tile.QuickExpenseTileService

/**
 * Punto de refresh del widget clásico (§C7): Inicio lo llama cuando cambia
 * el balance (ver `DashboardScreen`); pinta el balance de hoy en memoria,
 * sin persistir nada. Si no hay widget puesto, no hace nada.
 */
object WidgetRefresher {

    fun refresh(context: Context, balanceHoy: Double) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, QuickAddWidget::class.java))
        }.getOrDefault(intArrayOf())
        if (ids.isEmpty()) return
        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setTextViewText(R.id.widget_balance, WidgetBalance.format(balanceHoy))
            views.setOnClickPendingIntent(
                R.id.widget_btn_expense,
                quickEntryIntent(context, widgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_open,
                openAppIntent(context, widgetId + 1000)
            )
            runCatching { manager.updateAppWidget(widgetId, views) }
        }
    }

    private fun quickEntryIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = QuickExpenseTileService.ACTION_QUICK_ENTRY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("fintrack://widget/$requestCode")
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("fintrack://widget/$requestCode")
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
