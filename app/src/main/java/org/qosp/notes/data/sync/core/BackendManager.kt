package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.Note

class BackendManager<C>(
    private val backend: ISyncBackend<C>,
    private val config: C
) : ISyncProvider {

    override suspend fun sync(): BaseResult = backend.sync(config)

    override fun getConfig() = (config as? ProviderConfig)

    override val service = backend.service

    override suspend fun createNote(note: Note): BaseResult = backend.createNote(note, config)

    override suspend fun deleteNote(note: Note): BaseResult = backend.deleteNote(note, config)

    override suspend fun updateNote(note: Note): BaseResult = backend.updateNote(note, config)

    override suspend fun authenticate(config: ProviderConfig?): BaseResult =
        if (config == null) backend.authenticate(this.config)
        else (config as? C)?.let { backend.authenticate(it) } ?: InvalidConfig

    override suspend fun isServerCompatible(config: ProviderConfig?): BaseResult =
        if (config == null) backend.isServerCompatible(this.config)
        else (config as? C)?.let { backend.isServerCompatible(it) } ?: InvalidConfig


}
