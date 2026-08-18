package com.madhi.tracker.domain

/**
 * Convention unique de retour d'erreur du projet (ADR-003).
 *
 * Les ports ne lèvent pas d'exception métier : ils retournent un `Outcome`
 * quand l'échec est un cas normal, ou `null` quand l'absence est le seul
 * échec possible. Les exceptions restent réservées aux erreurs de
 * programmation et sont capturées à la frontière des adaptateurs.
 */
sealed interface Outcome<out S, out F> {

    data class Success<out S>(val value: S) : Outcome<S, Nothing>

    data class Failure<out F>(val reason: F) : Outcome<Nothing, F>

    val isSuccess: Boolean get() = this is Success

    fun valueOrNull(): S? = (this as? Success)?.value

    fun failureOrNull(): F? = (this as? Failure)?.reason
}

fun <S> success(value: S): Outcome<S, Nothing> = Outcome.Success(value)

fun <F> failure(reason: F): Outcome<Nothing, F> = Outcome.Failure(reason)
