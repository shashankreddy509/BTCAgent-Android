package com.gshashank.btcagent.data.repository

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [AuthRepositoryImpl].
 *
 * [FirebaseAuth], [FirebaseUser], and [Task] are final/complex SDK classes that cannot be
 * faked by hand — mockito-kotlin is used for them. [CredentialManager] is an interface, so it
 * can also be conveniently mocked.
 *
 * Every test is expected to FAIL until [AuthRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    private val mockAuth: FirebaseAuth = mock()
    private val mockCredentialManager: CredentialManager = mock()
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: AuthRepositoryImpl

    private val mockActivity: Activity = mock()
    private val mockUser: FirebaseUser = mock()

    @Before
    fun setUp() {
        repository = AuthRepositoryImpl(
            auth = mockAuth,
            ioDispatcher = testDispatcher,
            credentialManager = mockCredentialManager,
        )
    }

    // -------------------------------------------------------------------------
    // 1. Firebase success → Result.success(user)
    // -------------------------------------------------------------------------

    @Test
    fun `signInWithGoogle returns success when Firebase succeeds`() =
        runTest(testDispatcher) {
            // Arrange: CredentialManager returns a response holding a GoogleIdTokenCredential.
            val fakeIdToken = "fake.google.id.token"
            val mockGoogleCredential: GoogleIdTokenCredential = mock()
            whenever(mockGoogleCredential.idToken).thenReturn(fakeIdToken)

            val mockGetCredentialResponse: GetCredentialResponse = mock()
            whenever(mockGetCredentialResponse.credential).thenReturn(mockGoogleCredential)

            whenever(
                mockCredentialManager.getCredential(
                    context = any(),
                    request = any<GetCredentialRequest>(),
                )
            ).thenReturn(mockGetCredentialResponse)

            // Arrange: Firebase signs in successfully and returns our mock user.
            val mockAuthResult: AuthResult = mock()
            whenever(mockAuthResult.user).thenReturn(mockUser)

            val mockTask: Task<AuthResult> = mock()
            whenever(mockTask.isSuccessful).thenReturn(true)
            whenever(mockTask.result).thenReturn(mockAuthResult)
            whenever(mockTask.exception).thenReturn(null)

            whenever(mockAuth.signInWithCredential(any())).thenReturn(mockTask)

            // Act
            val result = repository.signInWithGoogle(mockActivity)

            // Assert
            assertTrue("Result must be success, was: $result", result.isSuccess)
            assertEquals(
                "Success value must be the FirebaseUser from the AuthResult",
                mockUser,
                result.getOrNull(),
            )
        }

    // -------------------------------------------------------------------------
    // 2. Firebase failure → Result.failure wrapping the exception
    // -------------------------------------------------------------------------

    @Test
    fun `signInWithGoogle returns failure wrapping exception when Firebase fails`() =
        runTest(testDispatcher) {
            // Arrange: CredentialManager happy path.
            val fakeIdToken = "fake.google.id.token"
            val mockGoogleCredential: GoogleIdTokenCredential = mock()
            whenever(mockGoogleCredential.idToken).thenReturn(fakeIdToken)

            val mockGetCredentialResponse: GetCredentialResponse = mock()
            whenever(mockGetCredentialResponse.credential).thenReturn(mockGoogleCredential)

            whenever(
                mockCredentialManager.getCredential(
                    context = any(),
                    request = any<GetCredentialRequest>(),
                )
            ).thenReturn(mockGetCredentialResponse)

            // Arrange: Firebase task fails with FirebaseAuthException.
            val firebaseException: FirebaseAuthException = mock()
            val mockTask: Task<AuthResult> = mock()
            whenever(mockTask.isSuccessful).thenReturn(false)
            whenever(mockTask.exception).thenReturn(firebaseException)

            whenever(mockAuth.signInWithCredential(any())).thenReturn(mockTask)

            // Act
            val result = repository.signInWithGoogle(mockActivity)

            // Assert
            assertTrue("Result must be failure, was: $result", result.isFailure)
            assertEquals(
                "The wrapped exception must be the FirebaseAuthException",
                firebaseException,
                result.exceptionOrNull(),
            )
        }

    // -------------------------------------------------------------------------
    // 3. Credential cancellation → Result.failure wrapping UserCancelledException
    // -------------------------------------------------------------------------

    @Test
    fun `signInWithGoogle returns failure on credential cancellation`() =
        runTest(testDispatcher) {
            // Arrange: CredentialManager throws a cancellation exception.
            whenever(
                mockCredentialManager.getCredential(
                    context = any(),
                    request = any<GetCredentialRequest>(),
                )
            ).thenAnswer { throw GetCredentialCancellationException() }

            // Act
            val result = repository.signInWithGoogle(mockActivity)

            // Assert
            assertTrue("Result must be failure on cancellation, was: $result", result.isFailure)
            assertTrue(
                "Wrapped exception must be UserCancelledException, was: ${result.exceptionOrNull()}",
                result.exceptionOrNull() is UserCancelledException,
            )
        }

    // -------------------------------------------------------------------------
    // 4. getIdToken returns the token string on success
    // -------------------------------------------------------------------------

    @Test
    fun `getIdToken returns token string on success`() = runTest(testDispatcher) {
        // Arrange: currentUser is available and its getIdToken task resolves successfully.
        val expectedToken = "firebase.id.token.string"

        val mockTokenResult: GetTokenResult = mock()
        whenever(mockTokenResult.token).thenReturn(expectedToken)

        val mockTokenTask: Task<GetTokenResult> = mock()
        whenever(mockTokenTask.isSuccessful).thenReturn(true)
        whenever(mockTokenTask.result).thenReturn(mockTokenResult)
        whenever(mockTokenTask.exception).thenReturn(null)

        whenever(mockUser.getIdToken(true)).thenReturn(mockTokenTask)
        whenever(mockAuth.currentUser).thenReturn(mockUser)

        // Act
        val result = repository.getIdToken()

        // Assert
        assertTrue("getIdToken result must be success, was: $result", result.isSuccess)
        assertEquals(
            "Token string must match what the SDK returned",
            expectedToken,
            result.getOrNull(),
        )
    }

    // -------------------------------------------------------------------------
    // 5. currentUser delegates to FirebaseAuth.currentUser — non-null user
    // -------------------------------------------------------------------------

    @Test
    fun `currentUser delegates to FirebaseAuth currentUser`() {
        // Arrange
        whenever(mockAuth.currentUser).thenReturn(mockUser)

        // Act & Assert
        assertEquals(
            "repository.currentUser must equal auth.currentUser",
            mockAuth.currentUser,
            repository.currentUser,
        )
    }

    // -------------------------------------------------------------------------
    // 5b. currentUser is null when no user signed in
    // -------------------------------------------------------------------------

    @Test
    fun `currentUser is null when FirebaseAuth has no signed-in user`() {
        // Arrange
        whenever(mockAuth.currentUser).thenReturn(null)

        // Act & Assert — will throw NotImplementedError until impl lands, making test red.
        assertNull(
            "repository.currentUser must be null when auth has no user",
            repository.currentUser,
        )
    }
}
