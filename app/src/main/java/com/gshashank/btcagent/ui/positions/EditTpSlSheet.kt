package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet content for editing TP/SL values — MOBILE-6.
 *
 * Field semantics (verified against the backend edit handler):
 *  - BLANK → omit the field (null). The server's `body.get("sl")` treats absent AND explicit-null
 *    identically as "leave that level unchanged" (`if sl is not None`). There is NO clear-via-null
 *    on this endpoint; clearing a TP requires sending an explicit 0.
 *  - Non-blank, positive number → set that level.
 *  - Non-blank but UNPARSEABLE (or ≤ 0) → input error, submit blocked. It is NOT coerced to null:
 *    even though null is harmless ("unchanged") today, a mistype must surface as an error, not
 *    silently no-op the user's intended edit.
 */
@Composable
fun EditTpSlSheet(
    onSubmit: (sl: Double?, tp: Double?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var slText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }

    // null = blank (leave unchanged); the Boolean is "valid" (blank or a positive number).
    fun parse(text: String): Pair<Double?, Boolean> = when {
        text.isBlank() -> null to true
        else -> {
            val v = text.toDoubleOrNull()
            if (v != null && v > 0.0) v to true else null to false
        }
    }

    val (sl, slValid) = parse(slText)
    val (tp, tpValid) = parse(tpText)
    val formValid = slValid && tpValid

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Edit TP / SL")

        OutlinedTextField(
            value = slText,
            onValueChange = { slText = it },
            label = { Text("Stop Loss (SL)") },
            isError = !slValid,
            supportingText = if (!slValid) {
                { Text("Enter a positive number, or leave blank to keep unchanged") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = tpText,
            onValueChange = { tpText = it },
            label = { Text("Take Profit (TP)") },
            isError = !tpValid,
            supportingText = if (!tpValid) {
                { Text("Enter a positive number, or leave blank to keep unchanged") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(
                enabled = formValid && (slText.isNotBlank() || tpText.isNotBlank()),
                onClick = { onSubmit(sl, tp) },
            ) {
                Text("Submit")
            }
        }
    }
}
