package com.jarvislin.drugstores.page.map

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.jarvislin.domain.entity.EntireInfo
import com.jarvislin.domain.entity.Progress
import com.jarvislin.domain.interactor.DrugstoreUseCase
import com.jarvislin.drugstores.extension.bind
import com.jarvislin.drugstores.base.BaseViewModel
import com.jarvislin.drugstores.data.remote.HttpException
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.koin.core.inject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MapViewModel : BaseViewModel() {
    private val useCase: DrugstoreUseCase by inject()
    val dataPrepared = MutableLiveData<Boolean>(false)
    val drugstoreInfo = MutableLiveData<List<EntireInfo>>(emptyList())
    val downloadProgress = MutableLiveData<Progress>()
    val autoUpdate = MutableLiveData<Boolean>()
    val searchedResult = MutableLiveData<List<EntireInfo>>()
    val statusBarHeight = MutableLiveData<Int>()

    fun fetchOpenData() {
        useCase.fetchOpenData()
            .subscribe({ downloadProgress.postValue(it) }, {
                when (it) {
                    is IOException -> toastText.postValue("無法連線，請檢查網路狀況")
                    is HttpException -> toastText.postValue("連線錯誤，請稍後再試")
                    else -> toastText.postValue("發生異常，請聯絡作者")
                }
                dataPrepared.postValue(false)
                Timber.e(it)
            })
            .bind(this)
    }

    fun fetchNearDrugstoreInfo(latitude: Double, longitude: Double) {
        if (dataPrepared.value != true) {
            // from on camera idled
            return
        }
        useCase.findNearDrugstoreInfo(latitude, longitude)
            .subscribe({ drugstoreInfo.postValue(it) }, { Timber.e(it) })
            .bind(this)
    }

    fun saveLastLocation(location: Location?) {
        location?.let { useCase.saveLocation(it.latitude, it.longitude) }
    }

    fun getLastLocation(): LatLng {
        return useCase.getLastLocation().run { LatLng(first, second) }
    }

    fun handleLatestOpenData(file: File) {
        useCase.handleLatestOpenData(file)
            .subscribe({ dataPrepared.postValue(true) }, { Timber.e(it) })
            .bind(this)
    }

    fun countDown() {
        Flowable.interval(1, 1, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.computation())
            .take(120)
            .doOnComplete {
                Timber.i("count down complete")
                autoUpdate.postValue(true)
            }
            .subscribe({ }, { Timber.e(it) })
            .bind(this)
    }

    fun searchAddress(keyword: String) {
        useCase.searchAddress(keyword)
            .subscribe({ searchedResult.postValue(it)}, { Timber.e(it) })
            .bind(this)
    }

    fun saveStatusBarHeight(height: Int) {
        statusBarHeight.postValue(height)
    }
}