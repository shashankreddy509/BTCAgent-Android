package com.gshashank.btcagent.data.repository

import android.app.Activity
import com.google.firebase.auth.FirebaseUser

interface AuthRepository {
    val currentUser: FirebaseUser?
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser>

    /**
     * Firebase ID token. [forceRefresh] = false returns the cached token (cheap — for
     * attaching to every request); true forces a network refresh (for the 401 retry path).
     * Defaults to true so existing callers keep the prior force-refresh behavior.
     */
    suspend fun getIdToken(forceRefresh: Boolean = true): Result<String>
    fun signOut()
}
