package org.qosp.notes.di

import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker

object AppModule {

    val module = module {
        workerOf(::BinCleaningWorker)
        workerOf(::SyncWorker)
    }
}
