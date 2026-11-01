package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.TrashDao

/** Permanent deletion of items already in Trash (restore lives on the owning repositories). */
class TrashRepository(private val trashDao: TrashDao) {
    suspend fun purgeTransaction(id: String) = trashDao.purgeTransaction(id)
    suspend fun purgeBudget(id: String) = trashDao.purgeBudget(id)
    suspend fun emptyTrash(userId: String) = trashDao.emptyTrash(userId)
}
