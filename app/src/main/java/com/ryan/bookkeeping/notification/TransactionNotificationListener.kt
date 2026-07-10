package com.ryan.bookkeeping.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ryan.bookkeeping.domain.NotificationPolicy
import com.ryan.bookkeeping.domain.RawNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NotificationPolicy.shouldInspectPackage(sbn.packageName)) return
        val raw = sbn.toRawNotification() ?: return
        val parsed = AppDependencies.parser.parse(raw) ?: return
        serviceScope.launch {
            AppDependencies.repository(applicationContext).recordParsed(parsed)
        }
    }

    private fun StatusBarNotification.toRawNotification(): RawNotification? {
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ).distinct().joinToString(" ")

        if (title.isBlank() && text.isBlank()) return null

        return RawNotification(
            sourcePackage = packageName,
            sourceApp = appLabel(packageName),
            title = title,
            content = text,
            postedAt = postTime,
        )
    }

    private fun appLabel(packageName: String): String {
        return runCatching {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrElse { error ->
            if (error is PackageManager.NameNotFoundException) packageName else packageName
        }
    }
}
