package org.qosp.notes.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class RepositoryModuleTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun testRepositoryModule() {
        RepositoryModule.module.verify()
    }

}
