package com.madhi.tracker.domain.error

import kotlin.time.Duration

sealed interface ActivationFailure {

    val code: String

    data object InvalidCode : ActivationFailure {
        override val code = "invalid_code"
    }

    data object ExpiredCode : ActivationFailure {
        override val code = "expired_code"
    }

    data object NoNetwork : ActivationFailure {
        override val code = "no_network"
    }

    data class RateLimited(val retryAfter: Duration?) : ActivationFailure {
        override val code = "rate_limited"
    }

    data class ServerError(val status: Int) : ActivationFailure {
        override val code = "server_error_$status"
    }

    data class Unexpected(val detail: String) : ActivationFailure {
        override val code = "unexpected"
    }
}
