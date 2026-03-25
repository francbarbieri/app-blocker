package com.appblocker.presentation

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.appblocker.R
import com.appblocker.domain.model.BlockedApp
import com.google.android.material.switchmaterial.SwitchMaterial

class BlockedAppsAdapter(
    private val onToggleBlocking: (String, Boolean) -> Unit,
    private val onRemoveApp: (BlockedApp) -> Unit
) : ListAdapter<BlockedApp, BlockedAppsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val packageNameText: TextView = itemView.findViewById(R.id.packageName)
        private val blockingSwitch: SwitchMaterial = itemView.findViewById(R.id.blockingSwitch)

        fun bind(app: BlockedApp) {
            appName.text = app.appName
            packageNameText.text = app.packageName

            try {
                val icon = itemView.context.packageManager.getApplicationIcon(app.packageName)
                appIcon.setImageDrawable(icon)
            } catch (_: PackageManager.NameNotFoundException) {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            blockingSwitch.setOnCheckedChangeListener(null)
            blockingSwitch.isChecked = app.isBlockingEnabled
            blockingSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleBlocking(app.packageName, isChecked)
            }

            itemView.setOnLongClickListener {
                onRemoveApp(app)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BlockedApp>() {
        override fun areItemsTheSame(oldItem: BlockedApp, newItem: BlockedApp) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: BlockedApp, newItem: BlockedApp) =
            oldItem == newItem
    }
}
