package com.gshashank.btcagent.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds repository interfaces to implementations.
 *
 * Empty for Phase 0 — @Binds entries are added per screen as repos land
 * (Auth, Access, Trading, Settings, MarketData, Price, Device — design 3e).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
