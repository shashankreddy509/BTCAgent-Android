package com.gshashank.btcagent.di

import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase graph. Currently provides FirebaseAuth for Google sign-in token minting.
 *
 * NOTE: requires google-services.json in app/ at runtime. The google-services Gradle
 * plugin stays unapplied until the Login screen step, so this compiles today.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
