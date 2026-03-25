package com.appblocker.presentation

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.appblocker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppPickerDialog : DialogFragment() {

    var onAppSelected: ((packageName: String, appName: String) -> Unit)? = null
    private var alreadyBlockedPackages: Set<String> = emptySet()

    fun setAlreadyBlocked(packages: Set<String>) {
        alreadyBlockedPackages = packages
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_app_picker, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.appsRecyclerView)
        val progressBar = view.findViewById<ProgressBar>(R.id.loadingProgress)

        val adapter = InstalledAppsAdapter { app ->
            onAppSelected?.invoke(app.packageName, app.appName)
            dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val apps = loadInstalledApps()
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    adapter.submitList(apps)
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_app)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun loadInstalledApps(): List<InstalledApp> {
        val pm = requireContext().packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == requireContext().packageName || pkgName in alreadyBlockedPackages) {
                    null
                } else {
                    InstalledApp(
                        packageName = pkgName,
                        appName = resolveInfo.loadLabel(pm).toString(),
                        icon = resolveInfo.loadIcon(pm)
                    )
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    private class InstalledAppsAdapter(
        private val onClick: (InstalledApp) -> Unit
    ) : ListAdapter<InstalledApp, InstalledAppsAdapter.ViewHolder>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_installed_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = getItem(position)
            holder.appName.text = app.appName
            holder.appIcon.setImageDrawable(app.icon)
            holder.itemView.setOnClickListener { onClick(app) }
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
            val appName: TextView = itemView.findViewById(R.id.appName)
        }

        private object DiffCallback : DiffUtil.ItemCallback<InstalledApp>() {
            override fun areItemsTheSame(a: InstalledApp, b: InstalledApp) =
                a.packageName == b.packageName

            override fun areContentsTheSame(a: InstalledApp, b: InstalledApp) = a == b
        }
    }
}
