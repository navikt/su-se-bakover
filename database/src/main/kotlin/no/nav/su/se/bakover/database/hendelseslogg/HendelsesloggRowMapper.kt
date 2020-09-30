package no.nav.su.se.bakover.database.hendelseslogg

import kotliquery.Row
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListReader

internal fun Row.toHendelseslogg(): Hendelseslogg {
    return Hendelseslogg(
        id = string(columnLabel = "id"),
        hendelser = stringOrNull("hendelser")?.let { HendelseListReader.readValue(it) } ?: mutableListOf()
    )
}
