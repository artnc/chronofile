package com.chaidarun.chronofile

import android.app.IntentService
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.ResultReceiver
import java.util.*

/** https://developer.android.com/training/location/display-address.html */
class FetchAddressIntentService : IntentService(TAG) {

  companion object {
    private val TAG = "FetchAddressIS"
    private val PACKAGE_NAME = "com.google.android.gms.location.sample.locationaddress"
    val SUCCESS_CODE = 1
    val FAILURE_CODE = 0
    val RECEIVER = PACKAGE_NAME + ".RECEIVER"
    val RESULT_DATA_KEY = PACKAGE_NAME + ".RESULT_DATA_KEY"
    val LOCATION_DATA_EXTRA = PACKAGE_NAME + ".LOCATION_DATA_EXTRA"
  }

  private lateinit var mReceiver: ResultReceiver

  override fun onHandleIntent(intent: Intent) {
    try {
      mReceiver = intent.getParcelableExtra(RECEIVER)
      val location = intent.getParcelableExtra<Location>(LOCATION_DATA_EXTRA)
      val geocoder = Geocoder(this, Locale.US)
      val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)[0]
      val text = address.getAddressLine(0)
      mReceiver.send(SUCCESS_CODE, Bundle().apply { putString(RESULT_DATA_KEY, text) })
    } catch (e: Exception) {
      mReceiver.send(FAILURE_CODE, Bundle())
    }
  }
}
