package com.gshashank.btcagent.data.repository

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.gshashank.btcagent.di.IoDispatcher
import com.gshashank.btcagent.di.ServerClientId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val credentialManager: CredentialManager,
    // Default is a non-empty placeholder so unit tests can construct without Hilt while
    // GetGoogleIdOption.Builder does not throw. Production value is provided by FirebaseModule.
    @ServerClientId private val serverClientId: String = "_placeholder_",
) : AuthRepository {

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> =
        withContext(ioDispatcher) {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(
                    context = activity,
                    request = request,
                )

                // The OS credential provider can return another credential type even when
                // only a Google ID option is requested — guard instead of an unchecked cast.
                val credential = response.credential
                if (credential !is GoogleIdTokenCredential) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Unexpected credential type: ${credential::class.simpleName}"
                        )
                    )
                }
                val idToken = credential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

                val authResult = auth.signInWithCredential(firebaseCredential).awaitTask()
                val user = authResult.user
                    ?: return@withContext Result.failure(
                        IllegalStateException("Firebase user is null after sign-in")
                    )
                Result.success(user)
            } catch (e: GetCredentialCancellationException) {
                Result.failure(UserCancelledException())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getIdToken(): Result<String> =
        withContext(ioDispatcher) {
            try {
                val user = auth.currentUser
                    ?: return@withContext Result.failure(
                        IllegalStateException("No signed-in user")
                    )
                // forceRefresh=true: avoid forwarding a near-expired cached token the
                // backend would reject. Firebase throttles refreshes internally.
                val tokenResult = user.getIdToken(true).awaitTask()
                val token = tokenResult.token
                    ?: return@withContext Result.failure(
                        IllegalStateException("ID token is null")
                    )
                Result.success(token)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun signOut() {
        auth.signOut()
    }
}

/**
 * Awaits a [Task] result by suspending the coroutine.
 *
 * Primary path (real GMS tasks): registers [addOnCompleteListener]; GMS fires the listener
 * synchronously for already-complete tasks and asynchronously for pending ones.
 *
 * Fallback path (unit-test mock tasks): [addOnCompleteListener] is a no-op on Mockito mocks,
 * so [cont] is still active after registration. The fallback reads [Task.isSuccessful],
 * [Task.result], and [Task.exception] directly — the only fields test mocks configure.
 * For a real pending GMS task: isSuccessful=false AND exception=null → no-op, listener fires.
 * For a real already-complete GMS task: listener fires synchronously inside [addOnCompleteListener],
 * making [cont.isActive] false before the fallback is reached — no double-resume risk.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    // Primary path — fires immediately for completed tasks, async for pending ones.
    addOnCompleteListener { task ->
        if (cont.isActive) {
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            } else {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("Task failed with no exception")
                )
            }
        }
    }

    // Fallback for test mocks: listener never fires → read fields synchronously.
    if (cont.isActive && isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        cont.resume(result as T)
    } else if (cont.isActive && exception != null) {
        cont.resumeWithException(exception!!) // !! is safe: guarded by non-null check in condition
    }
}
