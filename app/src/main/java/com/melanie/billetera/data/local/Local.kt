package com.melanie.billetera.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class TxType { DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN, SERVICE }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val owner: String,         // cuenta dueña del movimiento (cada usuario ve solo el suyo)
    val type: String,         // TxType.name
    val amount: Double,
    val counterpart: String?,  // cuenta destino/origen o código de contrato
    val category: String?,     // "Servicio Básico" para pagos de servicios
    val detail: String?,       // Luz / Agua / Internet
    val reference: String?,
    val timestamp: Long,
) {
    val txType: TxType get() = TxType.valueOf(type)
    val isIncome: Boolean get() = txType == TxType.DEPOSIT || txType == TxType.TRANSFER_IN
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE owner = :owner ORDER BY timestamp DESC, id DESC")
    fun observeAll(owner: String): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insert(tx: TransactionEntity): Long
}

@Database(entities = [TransactionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "wallet.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
