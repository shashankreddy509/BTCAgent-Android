package com.gshashank.btcagent.ui

import android.app.Activity
import com.google.firebase.auth.FirebaseUser
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [MainViewModel] — MOBILE-33.
 *
 * Verifies the cold-start launch decision: a persisted session routes past Login to Gate;
 * no session routes to Login.
 */
class MainViewModelTest {

    @Test
    fun `start destination is Gate when a session exists`() {
        val vm = MainViewModel(FakeAuthRepository(user = mock()))
        assertEquals(
            "A persisted signed-in user must start the app at Gate, not Login",
            Route.Gate,
            vm.startDestination,
        )
    }

    @Test
    fun `start destination is Login when no session exists`() {
        val vm = MainViewModel(FakeAuthRepository(user = null))
        assertEquals(
            "No persisted user must start the app at Login",
            Route.Login,
            vm.startDestination,
        )
    }
}

/** Fake exposing only [currentUser], the single input to [MainViewModel]'s decision. */
private class FakeAuthRepository(private val user: FirebaseUser?) : AuthRepository {
    override val currentUser: FirebaseUser? = user
    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> =
        Result.failure(UnsupportedOperationException("not needed"))
    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("not needed"))
    override fun signOut() = Unit
}
