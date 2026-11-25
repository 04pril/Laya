package com.music.laya.proxy

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.music.laya.MainActivity

/**
 * Reboots app-local networking after a validated .conf import.
 *
 * Must run in a DIFFERENT Android process (:proxy_relaunch) because simply
 * recreating the settings Activity does not reset the process-frozen OkHttp,
 * WebView and native WireGuard proxy. The helper stays in the foreground,
 * terminates the previous PID, then starts a fresh normal app process.
 *
 * No profile contents, credentials, ports or other secrets cross this Intent.
 */
class AppProxyRelaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousPid = intent.getIntExtra(EXTRA_OLD_PID, -1)
        if (previousPid <= 0 || previousPid == Process.myPid()) {
            finish()
            return
        }

        // We share the app UID with the original process. Kill it only after
        // this helper process is alive; the helper survives the old PID's exit.
        Process.killProcess(previousPid)

        // Let Android tear down the old task/process before opening the new
        // activity. This delay runs in the distinct, still-foreground helper.
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val restart = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(restart)
            finish()
        }, 450L)
    }

    companion object {
        private const val EXTRA_OLD_PID = "com.music.laya.proxy.previous_process_pid"
        private const val RESTART_SUFFIX = ":proxy_relaunch"

        fun restartFrom(activity: Activity) {
            val helper = Intent(activity, AppProxyRelaunchActivity::class.java).apply {
                putExtra(EXTRA_OLD_PID, Process.myPid())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            activity.startActivity(helper)
            activity.finish()
        }

        /**
         * The Application class is shared by both processes. Avoid binding
         * another WireGuard proxy or restoring a media queue in the helper.
         * Application.getProcessName() exists since API 28; minSdk is 26.
         */
        fun isRelaunchProcess(context: Context): Boolean {
            val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                    ?.runningAppProcesses
                    ?.firstOrNull { it.pid == Process.myPid() }
                    ?.processName
            }
            return processName == context.packageName + RESTART_SUFFIX
        }
    }
}
