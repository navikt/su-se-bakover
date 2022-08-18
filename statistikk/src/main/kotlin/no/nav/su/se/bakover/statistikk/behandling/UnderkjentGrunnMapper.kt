package no.nav.su.se.bakover.statistikk.behandling

import no.nav.su.se.bakover.domain.behandling.Attestering

fun Attestering.Underkjent.toResultatBegrunnelse(): String = when (this.grunn) {
    Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT -> UnderkjennelsesGrunner.INNGANGSVILKÅRENE_ER_FEILVURDERT
    Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL -> UnderkjennelsesGrunner.BEREGNINGEN_ER_FEIL
    Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER -> UnderkjennelsesGrunner.DOKUMENTASJON_MANGLER
    Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL -> UnderkjennelsesGrunner.VEDTAKSBREVET_ER_FEIL
    Attestering.Underkjent.Grunn.ANDRE_FORHOLD -> UnderkjennelsesGrunner.ANDRE_FORHOLD
}.toString()

internal enum class UnderkjennelsesGrunner {
    INNGANGSVILKÅRENE_ER_FEILVURDERT,
    BEREGNINGEN_ER_FEIL,
    DOKUMENTASJON_MANGLER,
    VEDTAKSBREVET_ER_FEIL,
    ANDRE_FORHOLD,
}
