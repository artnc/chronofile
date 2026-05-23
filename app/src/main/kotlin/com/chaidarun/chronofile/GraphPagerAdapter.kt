// © Art Chaidarun

package com.chaidarun.chronofile

import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class GraphPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

  override fun getItemCount() = Tab.entries.size

  override fun createFragment(position: Int) = Tab.entries[position].create()

  enum class Tab(val title: String, val create: () -> GraphFragment) {
    RADAR("Radar", { RadarFragment() }),
    PIE("Pie", { PieFragment() }),
    AREA("Area", { AreaFragment() }),
  }
}
