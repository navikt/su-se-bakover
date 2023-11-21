package no.nav.su.se.bakover.common.domain.client

/**
 * TODO jah: Her eksponerer vi ekstern status og melding til domenet og evt. videre til klienter. La hver bruker ha sitt eget sealed interface.
 */
data class ClientError(val httpStatus: Int, val message: String)
