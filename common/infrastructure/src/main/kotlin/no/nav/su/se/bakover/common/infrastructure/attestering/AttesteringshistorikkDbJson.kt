package no.nav.su.se.bakover.common.infrastructure.attestering

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.infrastructure.attestering.AttesteringDbJson.Companion.toDbJson
import no.nav.su.se.bakover.common.serialize

fun Attesteringshistorikk.toDatabaseJson(): String = this.map { it.toDbJson() }.serialize()

fun String.toAttesteringshistorikk(): Attesteringshistorikk {
    val attesteringer = deserialize<List<AttesteringDbJson>>(this)
    return Attesteringshistorikk.create(attesteringer.map { it.toDomain() })
}
