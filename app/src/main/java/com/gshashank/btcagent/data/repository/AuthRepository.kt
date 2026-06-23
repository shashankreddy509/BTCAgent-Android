package com.gshashank.btcagent.data.repository

import android.app.Activity
import com.google.firebase.auth.FirebaseUser

interface AuthRepository {
    val currentUser: FirebaseUser?
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser>
    suspend fun getIdToken(): Result<String>
    fun signOut()
}
