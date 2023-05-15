package no.nav.su.se.bakover.database.skatt

import java.util.UUID

data class Skattereferanser(
    val søkers: UUID,
    val eps: UUID?,
)
