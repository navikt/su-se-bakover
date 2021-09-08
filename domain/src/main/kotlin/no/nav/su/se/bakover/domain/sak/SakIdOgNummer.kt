package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

data class SakIdOgNummer(val sakId: UUID, val saksnummer: Saksnummer)
