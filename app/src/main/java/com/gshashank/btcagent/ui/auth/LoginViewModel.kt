package com.gshashank.btcagent.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.UserCancelledException
import com.gshashank.btcagent.di.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val catalogRepository: CatalogRepository,
    @MainDispatcher mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : ViewModel() {

    // Use an injected scope so tests can drive coroutines without Dispatchers.Main.
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** True when the catalog flag [CatalogFlags.LOGIN_MOCK] is ON. */
    val isMockLayout: Boolean = catalogRepository.isEnabled(CatalogFlags.LOGIN_MOCK)

    fun onGoogleSignIn(activity: Activity) {
        scope.launch {
            _uiState.value = LoginUiState.Loading
            // yield() ensures the Loading emission is observed by collectors before the
            // repository call (and its result) runs. This prevents StateFlow conflation
            // from dropping the Loading state when the repo returns synchronously.
            yield()
            val result = repository.signInWithGoogle(activity)
            _uiState.value = result.fold(
                onSuccess = { user -> LoginUiState.Success(user) },
                onFailure = { throwable ->
                    val message = when (throwable) {
                        is UserCancelledException -> "Sign-in was cancelled"
                        else -> throwable.message ?: "An unknown error occurred"
                    }
                    LoginUiState.Error(message)
                },
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
