package com.gshashank.btcagent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Provides a SEPARATE DataStore for appearance preferences — MOBILE-20.
 *
 * Uses @Named("appearance_prefs") to distinguish from the catalog_prefs DataStore
 * provided by [DataStoreModule]. Do NOT modify [DataStoreModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppearanceDataStoreModule {

    @Provides
    @Singleton
    @Named("appearance_prefs")
    fun provideAppearanceDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("appearance_prefs") },
        )
}
