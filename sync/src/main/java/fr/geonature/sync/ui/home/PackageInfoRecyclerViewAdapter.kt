package fr.geonature.sync.ui.home

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.work.WorkInfo
import fr.geonature.commons.ui.adapter.AbstractListItemRecyclerViewAdapter
import fr.geonature.sync.R
import fr.geonature.sync.sync.PackageInfo

/**
 * Default RecyclerView Adapter for [PackageInfo] used by [HomeActivity].
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 *
 * @see HomeActivity
 */
class PackageInfoRecyclerViewAdapter(listener: OnListItemRecyclerViewAdapterListener<PackageInfo>) : AbstractListItemRecyclerViewAdapter<PackageInfo>(listener) {
    override fun getViewHolder(view: View,
                               viewType: Int): AbstractViewHolder {
        return ViewHolder(view)
    }

    override fun getLayoutResourceId(position: Int,
                                     item: PackageInfo): Int {
        return R.layout.list_icon_item_2
    }

    override fun areItemsTheSame(oldItems: List<PackageInfo>,
                                 newItems: List<PackageInfo>,
                                 oldItemPosition: Int,
                                 newItemPosition: Int): Boolean {
        return oldItems[oldItemPosition].packageName == newItems[newItemPosition].packageName
    }

    override fun areContentsTheSame(oldItems: List<PackageInfo>,
                                    newItems: List<PackageInfo>,
                                    oldItemPosition: Int,
                                    newItemPosition: Int): Boolean {
        return oldItems[oldItemPosition] == newItems[newItemPosition] && oldItems[oldItemPosition].state == newItems[newItemPosition].state && oldItems[oldItemPosition].inputs == newItems[newItemPosition].inputs
    }

    inner class ViewHolder(itemView: View) : AbstractListItemRecyclerViewAdapter<PackageInfo>.AbstractViewHolder(itemView) {

        private val icon: ImageView = itemView.findViewById(android.R.id.icon1)
        private val iconStatus: TextView = itemView.findViewById(android.R.id.icon2)
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        private val stateAnimation = AlphaAnimation(0.0f,
                                                    1.0f).apply {
            duration = 250
            startOffset = 10
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        override fun onBind(item: PackageInfo) {
            icon.setImageDrawable(item.icon)
            text1.text = itemView.context.getString(R.string.home_app_version,
                                                    item.label,
                                                    item.versionName)
            text2.text = itemView.resources.getQuantityString(R.plurals.home_app_inputs,
                                                              item.inputs.size,
                                                              item.inputs.size)
            setState(item.state)
        }

        private fun setState(state: WorkInfo.State) {
            when (state) {
                WorkInfo.State.RUNNING -> {
                    iconStatus.setTextColor(ResourcesCompat.getColor(itemView.resources,
                                                                     R.color.status_pending,
                                                                     itemView.context?.theme))
                    iconStatus.startAnimation(stateAnimation)
                }
                WorkInfo.State.FAILED -> {
                    iconStatus.setTextColor(ResourcesCompat.getColor(itemView.resources,
                                                                     R.color.status_ko,
                                                                     itemView.context?.theme))
                    iconStatus.clearAnimation()
                }
                WorkInfo.State.SUCCEEDED -> {
                    iconStatus.setTextColor(ResourcesCompat.getColor(itemView.resources,
                                                                     R.color.status_ok,
                                                                     itemView.context?.theme))
                    iconStatus.clearAnimation()
                }
                else -> {
                    iconStatus.setTextColor(ResourcesCompat.getColor(itemView.resources,
                                                                     R.color.status_unknown,
                                                                     itemView.context?.theme))
                    iconStatus.clearAnimation()
                }
            }
        }
    }
}