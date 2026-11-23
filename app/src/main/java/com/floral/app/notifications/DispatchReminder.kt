package com.floral.app.notifications
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.floral.app.MainActivity
import com.floral.app.data.FloralDatabase
import java.util.concurrent.TimeUnit
class DispatchReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
 override suspend fun doWork(): Result {
  val id = inputData.getLong("order_id", -1L)
  val order = FloralDatabase.get(applicationContext).orders().get(id) ?: return Result.success()
  val deadline = order.expectedDispatchAt ?: return Result.success()
  if (order.dispatchedAt != null || deadline <= System.currentTimeMillis()) return Result.success()
  val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dispatch_deadlines", "Dispatch reminders", NotificationManager.IMPORTANCE_DEFAULT))
  val openApp = PendingIntent.getActivity(
   applicationContext, id.toInt(), Intent(applicationContext, MainActivity::class.java),
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )
  val n = NotificationCompat.Builder(applicationContext, "dispatch_deadlines")
   .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
   .setContentTitle("Dispatch due tomorrow")
   .setContentText("Remember to dispatch " + (order.plantName ?: "this order") + ".")
   .setContentIntent(openApp)
   .setAutoCancel(true).build()
  nm.notify(id.toInt(), n)
  return Result.success()
 }
}
object DispatchReminder {
 fun schedule(context: Context, id: Long, deadline: Long?) {
  val work = WorkManager.getInstance(context); val name = "dispatch-reminder-" + id
  work.cancelUniqueWork(name)
  if (deadline == null) return
  val delay = (deadline - TimeUnit.DAYS.toMillis(1) - System.currentTimeMillis()).coerceAtLeast(0L)
  val request = OneTimeWorkRequestBuilder<DispatchReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(workDataOf("order_id" to id)).build()
  work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
 }
 fun cancel(context: Context, id: Long) { WorkManager.getInstance(context).cancelUniqueWork("dispatch-reminder-" + id) }
}