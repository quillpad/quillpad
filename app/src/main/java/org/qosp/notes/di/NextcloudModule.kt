package org.qosp.notes.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.qosp.notes.data.sync.nextcloud.NextcloudAPIProvider
import org.qosp.notes.data.sync.nextcloud.ValidateNextcloud

object NextcloudModule {
    val nextcloudModule = module {
        singleOf(::NextcloudAPIProvider)
        singleOf(::ValidateNextcloud)
    }
}
