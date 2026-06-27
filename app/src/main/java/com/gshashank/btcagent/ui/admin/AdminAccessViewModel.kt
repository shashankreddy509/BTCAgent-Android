package com.gshashank.btcagent.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel that exposes [isAdmin] — whether the signed-in user has admin access.
 *
 * Used in SettingsScreen to show/hide the "Manage Users" row, and in UsersScreen as
 * a belt-and-suspenders guard (backend 403s anyway on non-admin calls — MOBILE-21).
 */
@HiltViewModel
class AdminAccessViewModel @Inject constructor(
    private val accessRepository: AccessRepository,
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    init {
        viewModelScope.launch {
            val result = accessRepository.checkAccess()
            _isAdmin.value = result is AccessResult.Allowed && result.admin
        }
    }
}
