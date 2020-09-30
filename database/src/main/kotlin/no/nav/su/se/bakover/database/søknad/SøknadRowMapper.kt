package no.nav.su.se.bakover.database.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.Søknad

internal fun Row.toSøknad(): Søknad {
    return Søknad(
        id = uuid("id"),
        søknadInnhold = objectMapper.readValue(string("søknadInnhold")),
        opprettet = tidspunkt("opprettet")
    )
}
