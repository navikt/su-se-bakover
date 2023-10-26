package no.nav.su.se.bakover.database.attestering

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.attestering.AttesteringDbJson.Companion.toDbJson

fun Attesteringshistorikk.toDatabaseJson(): String = this.map { it.toDbJson() }.serialize()

fun String.toAttesteringshistorikk(): Attesteringshistorikk {
    val attesteringer = deserialize<List<AttesteringDbJson>>(this)
    return Attesteringshistorikk.create(attesteringer.map { it.toDomain() })
}
