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

  override fun onHandleIntent(intent: Intent) {
    val receiver = intent.getParcelableExtra(RECEIVER) as? ResultReceiver
    try {
      val location = intent.getParcelableExtra<Location>(LOCATION_DATA_EXTRA)
      val geocoder = Geocoder(this, Locale.US)
      val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)[0]
      val text = address.getAddressLine(0)
      receiver?.send(SUCCESS_CODE, Bundle().apply { putString(RESULT_DATA_KEY, text) })
    } catch (e: Exception) {
      receiver?.send(FAILURE_CODE, Bundle())
    }
  }

  companion object {
    private const val PACKAGE_NAME = "com.google.android.gms.location.sample.locationaddress"
    const val SUCCESS_CODE = 1
    const val FAILURE_CODE = 0
    const val RECEIVER = "$PACKAGE_NAME.RECEIVER"
    const val RESULT_DATA_KEY = "$PACKAGE_NAME.RESULT_DATA_KEY"
    const val LOCATION_DATA_EXTRA = "$PACKAGE_NAME.LOCATION_DATA_EXTRA"
  }
}
