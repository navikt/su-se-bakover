package no.nav.su.se.bakover.web.routes.regulering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.Reguleringsjobb
import java.util.UUID

data class ReguleringJson(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val reguleringType: ReguleringType,
    val jobbnavn: Reguleringsjobb,
    val erFerdigstilt: Boolean,
)

fun Regulering.toJson() = ReguleringJson(
    id = id,
    opprettet = opprettet,
    sakId = sakId,
    saksnummer = saksnummer,
    reguleringType = reguleringType,
    jobbnavn = jobbnavn,
    erFerdigstilt = when (this) {
        is Regulering.IverksattRegulering -> true
        is Regulering.OpprettetRegulering -> false
    }
)
