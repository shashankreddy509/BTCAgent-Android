package com.gshashank.btcagent.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/** Marks the IO dispatcher (network / disk). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

/** Marks the Default dispatcher (CPU-bound work). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

/** Marks the Main dispatcher (UI / ViewModel coroutines). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher

/** Marks the Google server client id string (OAuth web client id). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ServerClientId

/**
 * Provides coroutine dispatchers behind qualifiers so tests can swap in a TestDispatcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
