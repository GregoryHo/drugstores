package com.jarvislin.drugstores.page.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.jakewharton.rxbinding2.view.RxView
import com.jarvislin.domain.entity.DrugstoreInfo
import com.jarvislin.domain.entity.EntireInfo
import com.jarvislin.drugstores.R
import com.jarvislin.drugstores.base.BaseActivity
import com.jarvislin.drugstores.base.BaseViewModel
import com.jarvislin.drugstores.extension.*
import com.jarvislin.drugstores.page.map.MarkerInfoManager
import kotlinx.android.synthetic.main.activity_detail.*
import org.jetbrains.anko.toast
import java.util.*


class DetailActivity(override val viewModel: BaseViewModel? = null) : BaseActivity(),
    OnMapReadyCallback {

    companion object {
        private const val KEY_INFO = "key_info"
        fun start(context: Context, info: EntireInfo) {
            Intent(context, DetailActivity::class.java).apply {
                putExtra(KEY_INFO, info)
                context.startActivity(this)
            }
        }
    }

    private val info by lazy { intent.getSerializableExtra(KEY_INFO) as EntireInfo }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        toolbar.navigationIcon?.tint(ContextCompat.getColor(this, R.color.secondaryIcons))
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        layoutAdult.background = info.getAdultMaskAmount().toBackground()
        layoutChild.background = info.getChildMaskAmount().toBackground()

        textAdultAmount.text = info.getAdultMaskAmount().toString()
        textChildAmount.text = info.getChildMaskAmount().toString()

        textName.text = info.getName()
        textAddress.text = info.getAddress()
        textPhone.text = "電話  " + info.getPhone()

        val calendar = Calendar.getInstance(Locale.getDefault())
        var day = calendar.get(Calendar.DAY_OF_WEEK)
        if (calendar.firstDayOfWeek == Calendar.SUNDAY) {
            day--
        }
        val text = when (day) {
            1, 3, 5 -> "奇數"
            2, 4, 6 -> "偶數"
            else -> "無限制"
        }
        textDateType.text = text

        RxView.clicks(textInfo)
            .throttleClick()
            .subscribe { showInfoDialog() }
            .bind(this)

        RxView.clicks(imagePhone)
            .throttleClick()
            .subscribe { showPhoneDialog() }
            .bind(this)

        RxView.clicks(imageLocation)
            .throttleClick()
            .subscribe { openMap() }
            .bind(this)
    }

    override fun onMapReady(map: GoogleMap) {

        // set map UI
        map.uiSettings.setAllGesturesEnabled(false)
        map.uiSettings.isMapToolbarEnabled = false

        // move camera
        info
            .let { LatLng(it.getLat(), it.getLng()) }
            .also { CameraUpdateFactory.newLatLngZoom(it, 18f).run { map.moveCamera(this) } }

        // add marker
        val markerInfo = MarkerInfoManager.getMarkerInfo(info.getAdultMaskAmount())
        val option = MarkerOptions()
            .position(LatLng(info.getLat(), info.getLng()))

        ContextCompat.getDrawable(this, markerInfo.drawableId)?.getBitmap()
            .let { option.icon(BitmapDescriptorFactory.fromBitmap(it)) }

        map.addMarker(option)
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.id_note_title))
            .setMessage(getString(R.string.id_note_message))
            .setPositiveButton(getString(R.string.dismiss)) { _, _ -> }
            .show()
    }

    private fun showPhoneDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dial_title))
            .setMessage(getString(R.string.dial_message))
            .setNegativeButton(getString(R.string.dismiss)) { _, _ -> }
            .setPositiveButton(getString(R.string.dial)) { _, _ ->
                Intent(Intent.ACTION_DIAL).apply {
                    try {
                        data = Uri.parse("tel:${info.getPhone()}")
                        startActivity(this)
                    } catch (ex: Exception) {
                        toast(getString(R.string.dial_error))
                    }
                }
            }
            .show()
    }

    private fun openMap() {
        val gmmIntentUri =
            Uri.parse("geo:${info.getLat()},${info.getLng()}?q=" + Uri.encode(info.getName()))
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        }
    }
}