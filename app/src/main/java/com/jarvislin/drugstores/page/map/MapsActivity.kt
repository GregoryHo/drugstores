package com.jarvislin.drugstores.page.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.jakewharton.rxbinding2.view.RxView
import com.jarvislin.domain.entity.DrugstoreInfo
import com.jarvislin.domain.entity.EntireInfo
import com.jarvislin.domain.entity.Progress
import com.jarvislin.drugstores.R
import com.jarvislin.drugstores.base.BaseActivity
import com.jarvislin.drugstores.extension.*
import com.jarvislin.drugstores.page.detail.DetailActivity
import com.jarvislin.drugstores.page.search.SearchDialogFragment
import com.jarvislin.drugstores.page.search.SearchDialogFragment.Companion.KEY_INFO
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_maps.*
import org.jetbrains.anko.dip
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.concurrent.TimeUnit


class MapsActivity : BaseActivity(), OnMapReadyCallback {

    override val viewModel: MapViewModel by viewModel()
    private val cacheManager: MarkerCacheManager by inject()
    private val infoWindowView by lazy {
        LayoutInflater.from(this).inflate(R.layout.window, null, false)
    }

    private val positionLatitude get() = map.cameraPosition.target.latitude
    private val positionLongitude get() = map.cameraPosition.target.longitude
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var dots: Disposable
    private var myLocation: LatLng? = null
    private var lastClickedMarker: Marker? = null

    companion object {
        private const val DELAY_MILLISECONDS = 100L
        private const val REQUEST_LOCATION = 5566
        private const val DEFAULT_ZOOM_LEVEL = 15f
        fun start(context: Context) {
            Intent(context, MapsActivity::class.java).apply {
                context.startActivity(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // init margin
        val id = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            val height = resources.getDimensionPixelSize(id)
            if (height == 0) {
                return
            }
            (layoutSearch.layoutParams as ConstraintLayout.LayoutParams).let {
                val param = it
                param.setMargins(it.marginStart, height + dip(16), it.marginEnd, 0)
                layoutSearch.layoutParams = param
            }
            viewModel.saveStatusBarHeight(height)
        }

        // progress bar
        progressBarTransform.indeterminateDrawable.tint(ContextCompat.getColor(this, R.color.colorAccent))

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { viewModel.saveLastLocation(it) }
        requestLocation()

        // download open data
        viewModel.downloadProgress.observe(this, Observer { progress ->
            progressBarDownload.progress = (100 * progress.bytesDownloaded / progress.contentLength).toInt()
            if (progress is Progress.Done) {
                dots.dispose()
                progressBarDownload.hide()
                progressBarTransform.show()
                Timber.i("open data downloaded")
                textProgressHint.text = "資料轉換中..."
                viewModel.handleLatestOpenData(progress.file)
            }
        })

        viewModel.autoUpdate.observe(this, Observer { startDownload() })

        startDownload()

        // handle search
        RxView.clicks(layoutSearch)
            .throttleClick()
            .subscribe {
                val dialogFragment = SearchDialogFragment()
                dialogFragment.arguments = Bundle().apply {
                    putSerializable(KEY_INFO, viewModel.drugstoreInfo.value?.let { ArrayList(it) }
                        ?: ArrayList<DrugstoreInfo>())
                }
                dialogFragment.show(supportFragmentManager, "Search")
            }.bind(this)
    }

    private fun startDownload() {
        progressBarDownload.progress = 0
        progressBarDownload.show()
        progressBarTransform.hide()
        layoutDownloadHint.animate().alpha(1f).start()
        dots = Flowable.interval(300, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.computation())
            .take(100)
            .map {
                when (it % 6) {
                    1L -> "."
                    2L -> ".."
                    3L -> "..."
                    4L -> ".."
                    5L -> "."
                    else -> ""
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                textProgressHint.text = "資料下載中" + it
            }, { Timber.e(it) })
            .addTo(compositeDisposable)

        viewModel.fetchOpenData()
    }

    private fun requestLocation() {
        fusedLocationClient.requestLocationUpdates(
            LocationRequest().setInterval(30_000),
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult?) {
                    super.onLocationResult(result)
                    result?.let {
                        it.locations.firstOrNull()?.let {
                            myLocation = LatLng(it.latitude, it.longitude)
                            viewModel.saveLastLocation(it)
                        }
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        if ((hasPermission(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))) {
            enableMyLocation()
        }

        moveTo(myLocation ?: viewModel.getLastLocation())

        viewModel.downloaded.observe(this, Observer { done ->
            if (done) {
                layoutDownloadHint.animate().setStartDelay(1_000).alpha(0f).start()
                viewModel.countDown()
                viewModel.fetchNearDrugstoreInfo(positionLatitude, positionLongitude)
                viewModel.drugstoreInfo.observe(this, Observer { addMarkers(it) })
            }
        })

        map.uiSettings.isMapToolbarEnabled = false

        map.setOnInfoWindowClickListener {
            DetailActivity.start(
                this,
                cacheManager.getEntireInfo(it)
            )
        }
        map.setOnCameraIdleListener {
            viewModel.fetchNearDrugstoreInfo(positionLatitude, positionLongitude)
        }

        map.setOnCameraMoveStartedListener { updateFabColor(R.color.secondaryIcons) }

        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoContents(marker: Marker): View? {
                lastClickedMarker = marker
                bindView(infoWindowView, cacheManager.getEntireInfo(marker))
                return infoWindowView
            }

            override fun getInfoWindow(p0: Marker): View? {
                return null
            }
        })

        fab.setOnClickListener { checkPermission() }
    }

    private fun updateFabColor(colorId: Int) {
        val drawable = ContextCompat.getDrawable(
            this,
            R.drawable.ic_my_location
        )
        drawable?.tint(ContextCompat.getColor(this, colorId))
        fab.setImageDrawable(drawable)
    }

    private fun bindView(view: View, info: EntireInfo) {
        view.findViewById<TextView>(R.id.textName).text = info.getName()
        view.findViewById<TextView>(R.id.textUpdate).text = info.getUpdateAt().toUpdateWording()
        view.findViewById<TextView>(R.id.textAdultAmount).text =
            info.getAdultMaskAmount().toString()
        view.findViewById<TextView>(R.id.textChildAmount).text =
            info.getChildMaskAmount().toString()
        view.findViewById<View>(R.id.layoutAdult).background =
            info.getAdultMaskAmount().toBackground()
        view.findViewById<View>(R.id.layoutChild).background =
            info.getChildMaskAmount().toBackground()
    }


    private fun addMarkers(drugstores: List<EntireInfo>) {
        Flowable.fromIterable(drugstores)
            .subscribeOn(Schedulers.computation())
            .filter { cacheManager.isCached(it).not() }
            .map {
                val markerInfo =
                    MarkerInfoManager.getMarkerInfo(it.getAdultMaskAmount())

                val option = MarkerOptions()
                    .position(LatLng(it.getLat(), it.getLng()))
                    .snippet(it.getId())
                    .zIndex(markerInfo.zIndex)

                ContextCompat.getDrawable(this, markerInfo.drawableId)?.getBitmap()
                    .let { option.icon(BitmapDescriptorFactory.fromBitmap(it)) }

                Pair(it, option)
            }
            .delay(DELAY_MILLISECONDS, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ pair ->
                pair.let { map.addMarker(it.second) }
                    .also { cacheManager.add(pair.first, it) }
            }, { Timber.e(it) })
            .bind(this)
    }


    private fun checkPermission() {
        if (hasPermission(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)) {
            enableMyLocation()
            animateTo(
                myLocation ?: viewModel.getLastLocation(),
                callback = { updateFabColor(R.color.colorAccent) })
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    ACCESS_COARSE_LOCATION,
                    ACCESS_FINE_LOCATION
                )
                ,
                REQUEST_LOCATION
            )
        }
    }


    private fun moveTo(latLng: LatLng) {
        CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL).let {
            map.moveCamera(it)
        }
    }

    private fun animateTo(latLng: LatLng, callback: () -> Unit = {}) {
        CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL).let {
            map.animateCamera(it, object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    callback.invoke()
                }

                override fun onCancel() {}
            })
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermission(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)) {
            enableMyLocation()
            requestLocation()
        }
    }

    private fun enableMyLocation() {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.isMyLocationEnabled = true
    }

    override fun onBackPressed() {
        if (lastClickedMarker?.isInfoWindowShown == true) {
            lastClickedMarker?.hideInfoWindow()
        } else {
            super.onBackPressed()
        }
    }
}

fun Context.hasPermission(vararg permission: String): Boolean {
    return permission.all {
        ActivityCompat.checkSelfPermission(
            this,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }
}