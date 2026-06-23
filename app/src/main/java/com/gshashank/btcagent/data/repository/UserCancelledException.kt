package com.gshashank.btcagent.data.repository

/** Thrown when the user explicitly cancels the credential picker. */
class UserCancelledException(message: String = "Sign-in cancelled by user") : Exception(message)
