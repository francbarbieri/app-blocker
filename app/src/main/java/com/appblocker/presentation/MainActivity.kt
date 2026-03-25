package com.appblocker.presentation

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appblocker.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: BlockedAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityService()
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
    }

    private fun setupRecyclerView() {
        adapter = BlockedAppsAdapter(
            onToggleBlocking = { packageName, enabled ->
                viewModel.toggleAppBlocking(packageName, enabled)
            },
            onRemoveApp = { app ->
                viewModel.removeApp(app.packageName)
                Snackbar.make(
                    findViewById(R.id.blockedAppsRecyclerView),
                    getString(R.string.app_removed),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.undo) {
                    viewModel.addApp(app.packageName, app.appName)
                }.show()
            }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.blockedAppsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val app = adapter.currentList[viewHolder.bindingAdapterPosition]
                viewModel.removeApp(app.packageName)
                Snackbar.make(
                    recyclerView,
                    getString(R.string.app_removed),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.undo) {
                    viewModel.addApp(app.packageName, app.appName)
                }.show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.addAppFab).setOnClickListener {
            val dialog = AppPickerDialog()
            dialog.setAlreadyBlocked(
                viewModel.blockedApps.value.map { it.packageName }.toSet()
            )
            dialog.onAppSelected = { packageName, appName ->
                viewModel.addApp(packageName, appName)
            }
            dialog.show(supportFragmentManager, "app_picker")
        }
    }

    private fun observeState() {
        val emptyState = findViewById<View>(R.id.emptyStateText)
        val loading = findViewById<View>(R.id.loadingIndicator)
        val recyclerView = findViewById<RecyclerView>(R.id.blockedAppsRecyclerView)

        lifecycleScope.launch {
            combine(viewModel.blockedApps, viewModel.isLoading) { apps, isLoading ->
                Pair(apps, isLoading)
            }.collect { (apps, isLoading) ->
                loading.visibility = if (isLoading) View.VISIBLE else View.GONE
                recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
                emptyState.visibility = if (!isLoading && apps.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(apps)
            }
        }
    }

    private fun checkAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            val prefs = getPreferences(Context.MODE_PRIVATE)
            val dismissed = prefs.getBoolean("accessibility_prompt_dismissed", false)
            if (!dismissed) {
                showAccessibilityPrompt()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun showAccessibilityPrompt() {
        AlertDialog.Builder(this)
            .setTitle(R.string.enable_accessibility_title)
            .setMessage(R.string.enable_accessibility_message)
            .setPositiveButton(R.string.enable) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.later) { _, _ ->
                getPreferences(Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("accessibility_prompt_dismissed", true)
                    .apply()
            }
            .show()
    }
}
