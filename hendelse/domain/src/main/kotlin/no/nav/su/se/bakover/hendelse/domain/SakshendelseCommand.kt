package no.nav.su.se.bakover.hendelse.domain

import java.util.UUID

interface SakshendelseCommand : HendelsesCommand {
    val sakId: UUID
}
