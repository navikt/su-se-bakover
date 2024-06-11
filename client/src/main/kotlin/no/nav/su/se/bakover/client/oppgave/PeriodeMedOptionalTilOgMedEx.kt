package no.nav.su.se.bakover.client.oppgave

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed

fun PeriodeMedOptionalTilOgMed.toOppgavePeriode(): String {
    return if (this.tilOgMed != null) {
        "${this.fraOgMed} - ${this.tilOgMed}"
    } else {
        "${this.fraOgMed} - ingen sluttdato"
    }
}
