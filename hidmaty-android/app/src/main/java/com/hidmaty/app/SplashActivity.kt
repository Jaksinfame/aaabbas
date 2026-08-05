package com.hidmaty.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * شاشة بداية بسيطة (Android SplashScreen API) — تنتقل فوراً لـMainActivity.
 * التأخير الطبيعي لتحميل الصفحة يحدث داخل WebView نفسه (شريط تقدّم أعلى الشاشة).
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
