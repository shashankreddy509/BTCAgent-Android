package com.gshashank.btcagent.di

import com.gshashank.btcagent.data.network.FirebaseTokenProvider
import com.gshashank.btcagent.data.network.TokenProvider
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessRepositoryImpl
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.AuthRepositoryImpl
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.CatalogRepositoryImpl
import com.gshashank.btcagent.data.repository.DashboardRepository
import com.gshashank.btcagent.data.repository.DashboardRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository interfaces and networking provider interfaces to their implementations.
 *
 * [TokenProvider] is bound here (rather than a separate NetworkBindingsModule) for simplicity —
 * all @Binds declarations live in one abstract module. The binding location is noted here for
 * discoverability.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAccessRepository(impl: AccessRepositoryImpl): AccessRepository

    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: FirebaseTokenProvider): TokenProvider

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository
}
