package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import java.util.UUID

data class FradragssjekkOppsummering(
    val antallOppgaver: Int,
    val oppgaverPerSakstype: List<FradragssjekkSakstypeStatistikk>,
)

data class FradragssjekkSakstypeStatistikk(
    val sakstype: Sakstype,
    val antallOppgaver: Int,
    val oppgaverPerFradrag: List<FradragssjekkFradragStatistikk>,
)

data class FradragssjekkFradragStatistikk(
    val fradragstype: String,
    val beskrivelse: String?,
    val antallOppgaver: Int,
)

internal fun FradragssjekkKjøring.lagOppsummering(): FradragssjekkOppsummering {
    val sakerMedOpprettetOppgave = resultat.saksresultater.filter { it.status.harOpprettetOppgave }

    return FradragssjekkOppsummering(
        antallOppgaver = sakerMedOpprettetOppgave.size,
        oppgaverPerSakstype = sakerMedOpprettetOppgave.tilOppgavestatistikk(),
    )
}

internal fun FradragssjekkKjøring.antallSakerMedUspesifisertÅrsak(): Int {
    return resultat.saksresultater
        .filter { it.status.harOpprettetOppgave }
        .count { it.harUspesifisertÅrsak() }
}

internal fun FradragssjekkSakstypeStatistikk.tilLoggtekst(): String {
    val fradragstekst = if (oppgaverPerFradrag.isEmpty()) {
        "ingen spesifiserte fradrag"
    } else {
        oppgaverPerFradrag.joinToString(separator = ", ") { it.tilLoggtekst() }
    }

    return "sakstype=$sakstype, antallOppgaver=$antallOppgaver, fradrag=[$fradragstekst]"
}

internal fun FradragssjekkFradragStatistikk.tilLoggtekst(): String {
    val fradragstypeTekst = beskrivelse?.let {
        "$fradragstype ($it)"
    } ?: fradragstype

    return "$fradragstypeTekst=$antallOppgaver"
}

private data class FradragNøkkel(
    val fradragstype: FradragstypeData,
)

private data class Oppgaveårsak(
    val sakId: UUID,
    val fradragstype: FradragstypeData,
)

private fun List<FradragssjekkSakResultat>.tilOppgavestatistikk(): List<FradragssjekkSakstypeStatistikk> {
    return groupBy { it.sjekkplan.sak.type }
        .map { (sakstype, saksresultater) ->
            FradragssjekkSakstypeStatistikk(
                sakstype = sakstype,
                antallOppgaver = saksresultater.size,
                oppgaverPerFradrag = saksresultater.tilFradragsstatistikk(),
            )
        }
        .sortedWith(
            compareByDescending<FradragssjekkSakstypeStatistikk> { it.antallOppgaver }
                .thenBy { it.sakstype.name },
        )
}

private fun List<FradragssjekkSakResultat>.tilFradragsstatistikk(): List<FradragssjekkFradragStatistikk> {
    val sakIderPerFradrag = mutableMapOf<FradragNøkkel, MutableSet<UUID>>()

    for (saksresultat in this) {
        for (årsak in saksresultat.tilOppgaveårsaker()) {
            sakIderPerFradrag
                .getOrPut(årsak.tilNøkkel()) { mutableSetOf() }
                .add(årsak.sakId)
        }
    }

    return sakIderPerFradrag
        .map { (nøkkel, sakIder) -> nøkkel.tilStatistikk(sakIder.size) }
        .sortedWith(
            compareByDescending<FradragssjekkFradragStatistikk> { it.antallOppgaver }
                .thenBy { it.fradragstype }
                .thenBy { it.beskrivelse.orEmpty() },
        )
}

private fun FradragssjekkSakResultat.tilOppgaveårsaker(): List<Oppgaveårsak> {
    if (!status.harOpprettetOppgave) return emptyList()

    return tilEksplisitteOppgaveårsaker()
}

private fun FradragssjekkSakResultat.harUspesifisertÅrsak(): Boolean {
    if (!status.harOpprettetOppgave || oppgaveAvvik.isEmpty()) return false
    return tilOppgaveårsaker().size < oppgaveAvvik.size
}

private fun FradragssjekkSakResultat.tilEksplisitteOppgaveårsaker(): List<Oppgaveårsak> {
    return oppgaveAvvik.mapNotNull { avvik ->
        val fradragstype = avvik.fradragstype ?: return@mapNotNull null

        Oppgaveårsak(
            sakId = sakId,
            fradragstype = fradragstype,
        )
    }.distinct()
}

private fun Oppgaveårsak.tilNøkkel(): FradragNøkkel {
    return FradragNøkkel(
        fradragstype = fradragstype,
    )
}

private fun FradragNøkkel.tilStatistikk(
    antallOppgaver: Int,
): FradragssjekkFradragStatistikk {
    return FradragssjekkFradragStatistikk(
        fradragstype = fradragstype.kategori.name,
        beskrivelse = fradragstype.beskrivelse,
        antallOppgaver = antallOppgaver,
    )
}
