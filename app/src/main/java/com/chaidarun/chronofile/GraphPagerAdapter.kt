// © Art Chaidarun

package com.chaidarun.chronofile

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class GraphPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

  override fun getCount() = Tab.entries.size

  override fun getPageTitle(position: Int) = Tab.get(position).title

  override fun getItem(position: Int) = Tab.get(position).create()

  enum class Tab(val title: String, val create: () -> GraphFragment) {
    RADAR("Radar", { RadarFragment() }),
    PIE("Pie", { PieFragment() }),
    AREA("Area", { AreaFragment() });

    companion object {
      fun get(index: Int) = Tab.entries[index]
    }
  }
}
