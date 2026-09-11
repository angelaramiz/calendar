package com.fintrack.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fintrack.app.MainActivity
import com.fintrack.app.R
import com.fintrack.app.ui.tile.QuickExpenseTileService

class QuickAddWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)

            views.setOnClickPendingIntent(
                R.id.widget_btn_expense,
                activityIntent(context, QuickExpenseTileService.ACTION_QUICK_ENTRY, widgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_open,
                activityIntent(context, null, widgetId + 1000)
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun activityIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("fintrack://widget/$requestCode")
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
