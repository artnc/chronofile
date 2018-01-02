package com.chaidarun.chronofile

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter

class GraphPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

  private val tabTitles = arrayOf("Pie", "Area")

  override fun getCount() = tabTitles.size
  override fun getPageTitle(position: Int) = tabTitles[position]

  override fun getItem(position: Int): Fragment = when (position) {
    0 -> PieFragment()
    1 -> AreaFragment()
    else -> throw Exception("Invalid graph fragment")
  }
}
