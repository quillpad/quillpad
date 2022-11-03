package org.qosp.notes.data.repo

import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.preferences.CloudService

class IdMappingRepository(private val idMappingDao: IdMappingDao) {

    suspend fun insert(vararg mappings: IdMapping) = idMappingDao.insert(*mappings)

    suspend fun update(vararg mappings: IdMapping) = idMappingDao.update(*mappings)

    suspend fun delete(vararg mappings: IdMapping) = idMappingDao.delete(*mappings)

    suspend fun assignProviderToNote(mapping: IdMapping) {
        val unassignedMappingId = idMappingDao.getNonRemoteByLocalId(mapping.localNoteId)?.mappingId

        if (unassignedMappingId != null) {
            return idMappingDao.update(
                mapping.copy(mappingId = unassignedMappingId)
            )
        }

        idMappingDao.insert(mapping)
    }

    suspend fun deleteByRemoteId(provider: CloudService, vararg remoteIds: Long) {
        idMappingDao.deleteByRemoteId(provider, *remoteIds)
    }

    suspend fun getAllByLocalId(localId: Long) = idMappingDao.getAllByLocalId(localId)

    suspend fun getByLocalIdAndProvider(localId: Long, provider: CloudService): IdMapping? {
        return idMappingDao.getByLocalIdAndProvider(localId, provider)
    }

    suspend fun getByRemoteId(remoteId: Long, provider: CloudService): IdMapping? {
        return idMappingDao.getByRemoteId(remoteId, provider)
    }

    suspend fun unassignProviderFromRemotelyDeletedNotes(idsInUse: List<Long>, provider: CloudService) {
        idMappingDao.unassignProviderFromRemotelyDeletedNotes(idsInUse, provider)
    }

    suspend fun unassignProviderFromNote(provider: CloudService, localId: Long) {
        idMappingDao.unassignProviderFromNote(localId, provider)
    }

    suspend fun deleteIfLocalIdNotIn(ids: List<Long>) = idMappingDao.deleteIfLocalIdNotIn(ids)
}
