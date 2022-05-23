package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknadstype
import java.util.UUID

data class SakInfo(val sakId: UUID, val saksnummer: Saksnummer, val fnr: Fnr, val type: Søknadstype)
