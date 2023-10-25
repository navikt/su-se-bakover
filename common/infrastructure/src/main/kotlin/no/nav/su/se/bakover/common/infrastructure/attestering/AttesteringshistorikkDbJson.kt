package no.nav.su.se.bakover.common.infrastructure.attestering

import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.infrastructure.attestering.AttesteringDbJson.Companion.toDbJson
import no.nav.su.se.bakover.common.serialize

fun Attesteringshistorikk.toDatabaseJson(): String = this.map { it.toDbJson() }.serialize()
