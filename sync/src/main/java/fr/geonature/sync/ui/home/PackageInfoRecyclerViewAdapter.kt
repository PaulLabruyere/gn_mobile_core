package fr.geonature.sync.ui.home

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.work.WorkInfo
import fr.geonature.commons.ui.adapter.AbstractListItemRecyclerViewAdapter
import fr.geonature.commons.util.ThemeUtils
import fr.geonature.sync.R
import fr.geonature.sync.sync.PackageInfo

/**
 * Default RecyclerView Adapter for [PackageInfo] used by [HomeActivity].
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 *
 * @see HomeActivity
 */
class PackageInfoRecyclerViewAdapter(private val listener: OnPackageInfoRecyclerViewAdapterListener) :
    AbstractListItemRecyclerViewAdapter<PackageInfo>(listener) {
    override fun getViewHolder(
        view: View,
        viewType: Int
    ): AbstractViewHolder {
        return ViewHolder(view)
    }

    override fun getLayoutResourceId(
        position: Int,
        item: PackageInfo
    ): Int {
        return R.layout.list_icon_item_2
    }

    override fun areItemsTheSame(
        oldItems: List<PackageInfo>,
        newItems: List<PackageInfo>,
        oldItemPosition: Int,
        newItemPosition: Int
    ): Boolean {
        return oldItems[oldItemPosition].packageName == newItems[newItemPosition].packageName
    }

    override fun areContentsTheSame(
        oldItems: List<PackageInfo>,
        newItems: List<PackageInfo>,
        oldItemPosition: Int,
        newItemPosition: Int
    ): Boolean {
        return oldItems[oldItemPosition] == newItems[newItemPosition] && oldItems[oldItemPosition].state == newItems[newItemPosition].state && oldItems[oldItemPosition].inputs == newItems[newItemPosition].inputs
    }

    inner class ViewHolder(itemView: View) :
        AbstractListItemRecyclerViewAdapter<PackageInfo>.AbstractViewHolder(itemView) {

        private val icon: ImageView = itemView.findViewById(android.R.id.icon1)
        private val button: Button = itemView.findViewById(android.R.id.button1)
        private val iconStatus: TextView = itemView.findViewById(android.R.id.icon2)
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        private val stateAnimation = AlphaAnimation(
            0.0f,
            1.0f
        ).apply {
            duration = 250
            startOffset = 10
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        override fun onBind(item: PackageInfo) {
            with(button) {
                visibility =
                    if (item.apk.isNullOrEmpty()) View.GONE
                    else View.VISIBLE
                text =
                    if (item.versionName.isNullOrEmpty()) itemView.context.getString(R.string.home_app_install)
                    else itemView.context.getString(R.string.home_app_upgrade)
                contentDescription =
                    if (item.versionName.isNullOrEmpty()) itemView.context.getString(R.string.home_app_install_desc, item.label)
                    else itemView.context.getString(R.string.home_app_upgrade_desc, item.label)
                setOnClickListener {
                    listener.onUpgrade(item)
                }
            }

            iconStatus.visibility = if (item.apk.isNullOrEmpty()) View.VISIBLE else View.GONE

            with(icon) {
                setImageDrawable(item.icon ?: itemView.context.getDrawable(R.drawable.ic_upgrade))

                if (item.icon == null) {
                    DrawableCompat.setTint(
                        DrawableCompat.wrap(icon.drawable),
                        ThemeUtils.getPrimaryColor(context)
                    )
                }
            }

            text1.text =
                if (item.versionName.isNullOrEmpty()) item.label
                else itemView.context.getString(
                    R.string.home_app_version_full,
                    item.label,
                    item.versionName
                )
            text2.text = itemView.resources.getQuantityString(
                R.plurals.home_app_inputs,
                item.inputs,
                item.inputs
            )
            setState(item.state)
        }

        private fun setState(state: WorkInfo.State) {
            when (state) {
                WorkInfo.State.RUNNING -> {
                    iconStatus.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.status_pending,
                            itemView.context?.theme
                        )
                    )
                    iconStatus.startAnimation(stateAnimation)
                }
                WorkInfo.State.FAILED -> {
                    iconStatus.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.status_ko,
                            itemView.context?.theme
                        )
                    )
                    iconStatus.clearAnimation()
                }
                WorkInfo.State.SUCCEEDED -> {
                    iconStatus.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.status_ok,
                            itemView.context?.theme
                        )
                    )
                    iconStatus.clearAnimation()
                }
                else -> {
                    iconStatus.setTextColor(
                        ResourcesCompat.getColor(
                            itemView.resources,
                            R.color.status_unknown,
                            itemView.context?.theme
                        )
                    )
                    iconStatus.clearAnimation()
                }
            }
        }
    }

    /**
     * Callback used by [PackageInfoRecyclerViewAdapter].
     */
    interface OnPackageInfoRecyclerViewAdapterListener :
        OnListItemRecyclerViewAdapterListener<PackageInfo> {

        /**
         * Called when a [PackageInfo] should be upgraded.
         */
        fun onUpgrade(item: PackageInfo)
    }
}
