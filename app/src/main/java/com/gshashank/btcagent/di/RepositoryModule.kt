package com.gshashank.btcagent.di

import com.gshashank.btcagent.data.network.FirebaseTokenProvider
import com.gshashank.btcagent.data.network.TokenProvider
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessRepositoryImpl
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.AuthRepositoryImpl
import com.gshashank.btcagent.data.repository.BriefingRepository
import com.gshashank.btcagent.data.repository.BriefingRepositoryImpl
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.CatalogRepositoryImpl
import com.gshashank.btcagent.data.repository.DashboardRepository
import com.gshashank.btcagent.data.repository.DashboardRepositoryImpl
import com.gshashank.btcagent.data.repository.OpenInterestRepository
import com.gshashank.btcagent.data.repository.OpenInterestRepositoryImpl
import com.gshashank.btcagent.data.repository.PositionsRepository
import com.gshashank.btcagent.data.repository.PositionsRepositoryImpl
import com.gshashank.btcagent.data.repository.RegimeRepository
import com.gshashank.btcagent.data.repository.RegimeRepositoryImpl
import com.gshashank.btcagent.data.repository.ReportsRepository
import com.gshashank.btcagent.data.repository.ReportsRepositoryImpl
import com.gshashank.btcagent.data.repository.ScannerRepository
import com.gshashank.btcagent.data.repository.ScannerRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindPositionsRepository(impl: PositionsRepositoryImpl): PositionsRepository

    @Binds
    @Singleton
    abstract fun bindReportsRepository(impl: ReportsRepositoryImpl): ReportsRepository

    @Binds
    @Singleton
    abstract fun bindScannerRepository(impl: ScannerRepositoryImpl): ScannerRepository

    @Binds
    @Singleton
    abstract fun bindBriefingRepository(impl: BriefingRepositoryImpl): BriefingRepository

    @Binds
    @Singleton
    abstract fun bindRegimeRepository(impl: RegimeRepositoryImpl): RegimeRepository

    @Binds
    @Singleton
    abstract fun bindOpenInterestRepository(impl: OpenInterestRepositoryImpl): OpenInterestRepository
}
