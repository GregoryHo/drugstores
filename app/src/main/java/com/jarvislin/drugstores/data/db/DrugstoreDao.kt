package com.jarvislin.drugstores.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.IGNORE
import androidx.room.Query
import com.jarvislin.domain.entity.Drugstore
import com.jarvislin.domain.entity.DrugstoreInfo
import com.jarvislin.domain.entity.OpenData
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface DrugstoreDao {
    @Query("SELECT Drugstores.*, OpenData.* FROM Drugstores INNER JOIN OpenData ON Drugstores.id = OpenData.drugstore_id WHERE  Drugstores.id = :id")
    fun getDrugstoreInfo(id: String): Single<List<DrugstoreInfo>>

    @Insert(onConflict = IGNORE)
    fun insertDrugstores(shelters: List<Drugstore>)

    @Insert(onConflict = IGNORE)
    fun insertOpenData(openData: List<OpenData>): Completable

    @Query("DELETE FROM Drugstores")
    fun deleteDrugstores(): Single<Int>

    @Query("DELETE FROM OpenData")
    fun deleteOpenData(): Single<Int>

    @Query("SELECT * FROM Drugstores ORDER BY ABS(:latitude - lat) + ABS(:longitude - lng) ASC LIMIT :limit")
    fun selectNearStores(latitude: Double, longitude: Double, limit: Int): Single<List<Drugstore>>

    @Query("SELECT Drugstores.*, OpenData.* FROM Drugstores INNER JOIN OpenData ON Drugstores.id = OpenData.drugstore_id ORDER BY ABS(:latitude - Drugstores.lat) + ABS(:longitude - Drugstores.lng) ASC LIMIT :limit")
    fun findNearDrugstoreInfo(
        latitude: Double,
        longitude: Double,
        limit: Int
    ): Single<List<DrugstoreInfo>>

    @Query("SELECT Drugstores.*, OpenData.* FROM Drugstores INNER JOIN OpenData ON Drugstores.id = OpenData.drugstore_id WHERE Drugstores.address LIKE '%' || :keyword || '%' LIMIT 300")
    fun searchAddress(keyword: String): Single<List<DrugstoreInfo>>
}