package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

data class SakIdSaksnummerFnr(val sakId: UUID, val saksnummer: Saksnummer, val fnr: Fnr)
