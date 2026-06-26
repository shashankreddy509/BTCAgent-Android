package com.gshashank.btcagent.ui.trade.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.biometric.BiometricResult
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.ManualOrderDraft
import com.gshashank.btcagent.data.model.OrderSummary
import com.gshashank.btcagent.data.model.OrderSummaryCalculator
import com.gshashank.btcagent.data.model.OrderType
import com.gshashank.btcagent.data.model.PendingOrder
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.ManualEntryRepository
import com.gshashank.btcagent.data.repository.TradingControlRepository
import com.gshashank.btcagent.data.repository.TradingControlResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Manual Entry screen — MOBILE-19.
 *
 * Biometric design: the ViewModel does NOT call biometric hardware directly. Instead:
 * 1. LIVE mode [placeMarket]/[placeLimit] sets [pendingConfirmState] to a non-null draft.
 * 2. The UI observes [pendingConfirmState], launches the system biometric prompt, and feeds
 *    the result back via [onBiometricResult].
 * 3. [onBiometricResult] Success → POST; Cancelled/Failed → no POST.
 *
 * Catalog gating: [catalogEnabled] is a reactive StateFlow built from
 * [CatalogRepository.isEnabledFlow] so the screen reacts when the fetch lands.
 */
@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val manualEntryRepository: ManualEntryRepository,
    private val tradingControlRepository: TradingControlRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    /**
     * Immutable form state exposed to the UI.
     *
     * [qty], [entry], [sl], [tp] are nullable Doubles — null means the field has not yet been
     * filled. [orderSummary] is non-null only when all required fields are present.
     */
    data class FormState(
        val direction: Side = Side.Long,
        val orderType: OrderType = OrderType.MARKET,
        val qty: Double? = null,
        val entry: Double? = null,
        val sl: Double? = null,
        val tp: Double? = null,
        val slValidationError: String? = null,
        val orderSummary: OrderSummary? = null,
    )

    private val _formState = MutableStateFlow(FormState())
    val formState: StateFlow<FormState> = _formState.asStateFlow()

    /** Non-null while a LIVE biometric confirm bottom-sheet should be shown. */
    private val _pendingConfirmState = MutableStateFlow<ManualOrderDraft?>(null)
    val pendingConfirmState: StateFlow<ManualOrderDraft?> = _pendingConfirmState.asStateFlow()

    /** One-shot action result for the UI snackbar. */
    private val _actionResult = MutableStateFlow<ActionResultUiState?>(null)
    val actionResult: StateFlow<ActionResultUiState?> = _actionResult.asStateFlow()

    /** Non-null when a 403 admin-only message should be shown. */
    private val _adminOnlyMessage = MutableStateFlow<String?>(null)
    val adminOnlyMessage: StateFlow<String?> = _adminOnlyMessage.asStateFlow()

    private val _pendingOrders = MutableStateFlow<List<PendingOrder>>(emptyList())
    val pendingOrders: StateFlow<List<PendingOrder>> = _pendingOrders.asStateFlow()

    private val _executionMode = MutableStateFlow(ExecutionMode.PAPER)
    val executionMode: StateFlow<ExecutionMode> = _executionMode.asStateFlow()

    /**
     * Reactive catalog flag for MANUAL_ENTRY (id = 100007).
     * Seeded with the sync [CatalogRepository.isEnabled] value so the screen renders correctly
     * before the first fetch lands. Updates reactively via [CatalogRepository.isEnabledFlow].
     */
    val catalogEnabled: StateFlow<Boolean> = catalogRepository
        .isEnabledFlow(CatalogFlags.MANUAL_ENTRY, default = false)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = catalogRepository.isEnabled(CatalogFlags.MANUAL_ENTRY, default = false),
        )

    /** Guards against concurrent in-flight write operations (double-tap protection). */
    private var activeJob: Job? = null

    init {
        loadModeAndPending()
    }

    private fun loadModeAndPending() {
        viewModelScope.launch {
            try {
                when (val result = tradingControlRepository.fetchState()) {
                    is TradingControlResult.Success -> {
                        _executionMode.value = result.data.mode
                    }
                    is TradingControlResult.Error -> {
                        // W9 fix: default to LIVE on error (more restrictive) so an unknown mode
                        // always requires biometric — prevents bypassing the biometric gate on
                        // transient network failures.
                        _executionMode.value = ExecutionMode.LIVE
                    }
                }
                _pendingOrders.value = manualEntryRepository.fetchPending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // fetchState contract is never-throw, but guard anyway: an unknown mode must
                // stay LIVE (biometric-required) and the screen must remain usable.
                _executionMode.value = ExecutionMode.LIVE
            }
        }
    }

    fun updateDirection(direction: Side) {
        _formState.update { it.copy(direction = direction) }
        recomputeSummary()
    }

    fun updateOrderType(type: OrderType) {
        _formState.update { it.copy(orderType = type) }
    }

    fun updateQty(v: String) {
        _formState.update { it.copy(qty = v.toDoubleOrNull()) }
        recomputeSummary()
    }

    fun updateEntry(v: String) {
        _formState.update { it.copy(entry = v.toDoubleOrNull()) }
        recomputeSummary()
    }

    // W7 fix: updateLimitPrice removed — limitPrice is passed directly to placeLimit() from
    // the UI as a string parameter; it is not stored in FormState (it was a dead no-op).

    fun updateSl(v: String) {
        _formState.update { it.copy(sl = v.toDoubleOrNull()) }
        recomputeSummary()
    }

    fun updateTp(v: String) {
        _formState.update { it.copy(tp = v.toDoubleOrNull()) }
        recomputeSummary()
    }

    private fun recomputeSummary() {
        val f = _formState.value
        val summary = OrderSummaryCalculator.compute(
            qty = f.qty,
            entry = f.entry,
            sl = f.sl,
            tp = f.tp,
            direction = f.direction,
        )
        val slError = if (f.entry != null && f.sl != null) {
            when (f.direction) {
                Side.Long -> if (f.sl >= f.entry) "SL must be below entry for long" else null
                Side.Short -> if (f.sl <= f.entry) "SL must be above entry for short" else null
            }
        } else null
        _formState.update { it.copy(orderSummary = summary, slValidationError = slError) }
    }

    /**
     * Initiates a market order placement.
     *
     * PAPER mode → calls [ManualEntryRepository.placeMarket] directly.
     * LIVE mode → sets [pendingConfirmState] to trigger the biometric confirm bottom-sheet.
     * Blocked when [formState].[slValidationError] is non-null or required fields are missing.
     * Double-tap guard: no-op if a job is already in-flight.
     */
    fun placeMarket() {
        if (activeJob?.isActive == true) return
        val f = _formState.value
        if (f.slValidationError != null) return
        val qty = f.qty ?: return
        val sl = f.sl ?: return

        val draft = ManualOrderDraft(
            direction = f.direction,
            orderType = OrderType.MARKET,
            qty = qty,
            limitPrice = null,
            sl = sl,
            tp = f.tp,
        )

        if (_executionMode.value == ExecutionMode.PAPER) {
            activeJob = viewModelScope.launch {
                delay(1L)
                val result = manualEntryRepository.placeMarket(draft)
                handleActionResult(result)
                if (result is ActionResult.Success) {
                    // W6 fix: refresh inside the active job, not in a separate untracked launch.
                    _pendingOrders.value = manualEntryRepository.fetchPending()
                }
            }
        } else {
            // LIVE mode — require biometric confirmation before POSTing.
            _pendingConfirmState.value = draft
        }
    }

    /**
     * Initiates a limit order placement.
     *
     * Follows the same PAPER/LIVE split as [placeMarket].
     */
    fun placeLimit(limitPriceStr: String) {
        if (activeJob?.isActive == true) return
        val f = _formState.value
        if (f.slValidationError != null) return
        val qty = f.qty ?: return
        val limitPrice = limitPriceStr.toDoubleOrNull() ?: return
        val sl = f.sl ?: return

        val draft = ManualOrderDraft(
            direction = f.direction,
            orderType = OrderType.LIMIT,
            qty = qty,
            limitPrice = limitPrice,
            sl = sl,
            tp = f.tp,
        )

        if (_executionMode.value == ExecutionMode.PAPER) {
            activeJob = viewModelScope.launch {
                delay(1L)
                val result = manualEntryRepository.placeLimit(draft)
                handleActionResult(result)
                if (result is ActionResult.Success) {
                    // W6 fix: refresh inside the active job, not in a separate untracked launch.
                    _pendingOrders.value = manualEntryRepository.fetchPending()
                }
            }
        } else {
            _pendingConfirmState.value = draft
        }
    }

    /**
     * Called by the UI after the system biometric prompt returns.
     *
     * [BiometricResult.Success] → POST the pending draft and clear the confirm state.
     * [BiometricResult.Cancelled] / [BiometricResult.Failed] → discard; no POST.
     * [BiometricResult.Unavailable] → discard; no POST (VM delegates messaging to the screen).
     */
    fun onBiometricResult(biometricResult: BiometricResult) {
        when (biometricResult) {
            is BiometricResult.Success -> {
                // Double-tap guard: if a write is already in-flight, ignore the second confirm.
                // Discard semantics (not preempt) — never cancel an in-flight real-money order.
                if (activeJob?.isActive == true) return
                val draft = _pendingConfirmState.value ?: return
                _pendingConfirmState.value = null
                activeJob = viewModelScope.launch {
                    delay(1L)
                    val result = when (draft.orderType) {
                        OrderType.MARKET -> manualEntryRepository.placeMarket(draft)
                        OrderType.LIMIT -> manualEntryRepository.placeLimit(draft)
                    }
                    handleActionResult(result)
                    if (result is ActionResult.Success) {
                        // W6 fix: refresh inside the active job, not in a separate untracked launch.
                        _pendingOrders.value = manualEntryRepository.fetchPending()
                    }
                }
            }
            else -> {
                // Cancelled, Failed, or Unavailable — discard the pending draft; no POST.
                _pendingConfirmState.value = null
            }
        }
    }

    /**
     * Cancels a pending limit order and refreshes the pending list on success.
     * Double-tap guard: no-op if a job is already in-flight.
     */
    fun cancelPending(id: String) {
        // Double-tap guard: ignore a second cancel while one is in-flight (discard, not preempt).
        if (activeJob?.isActive == true) return
        activeJob = viewModelScope.launch {
            delay(1L)
            when (val result = manualEntryRepository.cancelPending(id)) {
                is ActionResult.Success -> {
                    // W6 fix: refresh inside the active job, not in a separate untracked launch.
                    _pendingOrders.value = manualEntryRepository.fetchPending()
                }
                is ActionResult.Error -> {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    /** Clears the one-shot action result (called after the snackbar has been shown). */
    fun clearActionResult() {
        _actionResult.value = null
    }

    /** Clears the admin-only message (called after the snackbar has been shown). */
    fun clearAdminOnlyMessage() {
        _adminOnlyMessage.value = null
    }

    private fun handleActionResult(result: ActionResult) {
        when (result) {
            is ActionResult.Success -> {
                _actionResult.value = ActionResultUiState.Success
                // W6 fix: pendingOrders refresh is now inlined in each call site (placeMarket,
                // placeLimit, onBiometricResult) inside the existing active job. No untracked
                // nested launch here.
            }
            is ActionResult.Error -> {
                // W11 fix: detect 403 by code, not by message string matching.
                if (result.code == 403) {
                    _adminOnlyMessage.value = result.message
                } else {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }
}
