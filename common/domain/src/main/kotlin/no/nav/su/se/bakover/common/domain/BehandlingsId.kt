package no.nav.su.se.bakover.common.domain

import java.util.UUID

interface BehandlingsId {
    val value: UUID

    override fun toString(): String
}
