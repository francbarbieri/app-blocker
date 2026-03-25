package com.appblocker.presentation

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appblocker.R
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.MotivationalMessageRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BlockOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var recordUsage: RecordUsageUseCase
    private lateinit var getMotivationalMessage: GetMotivationalMessageUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage

        val database = AppDatabase.getInstance(this)
        recordUsage = RecordUsageUseCase(
            UsageRepositoryImpl(database.usageSessionDao(), database.unblockEventDao())
        )
        getMotivationalMessage = GetMotivationalMessageUseCase(
            MotivationalMessageRepositoryImpl(database.motivationalMessageDao())
        )

        findViewById<TextView>(R.id.blockedAppName).text = appName

        loadMotivationalMessage(blockedPackage)

        findViewById<MaterialButton>(R.id.goBackButton).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                recordUsage.recordUnblock(blockedPackage, userProceeded = false)
            }
            goHome()
        }

        findViewById<MaterialButton>(R.id.proceedButton).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                recordUsage.recordUnblock(blockedPackage, userProceeded = true)
            }
            finish()
        }
    }

    private fun loadMotivationalMessage(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val message = getMotivationalMessage(packageName)
            val text = message?.message ?: getString(R.string.default_motivational)
            runOnUiThread {
                findViewById<TextView>(R.id.motivationalMessage).text = text
            }
        }
    }

    private fun goHome() {
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    @Deprecated("Use OnBackPressedCallback instead")
    override fun onBackPressed() {
        goHome()
    }
}
