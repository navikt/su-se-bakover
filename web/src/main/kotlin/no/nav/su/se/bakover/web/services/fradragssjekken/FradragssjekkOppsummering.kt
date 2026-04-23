package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import java.util.UUID

internal fun lagOppsummeringPerÅrsak(
    saksresultater: List<FradragssjekkSakResultat>,
): Map<FradragssjekkSakStatus, Int> {
    return saksresultater
        .groupingBy { it.status }
        .eachCount()
}

internal data class FradragssjekkOppsummering(
    val nøkkeltall: Map<FradragssjekkSakStatus, Int>,
    val antallOppgaver: Int,
    val oppgaverPerSakstype: List<FradragssjekkSakstypeStatistikk>,
)

internal data class FradragssjekkSakstypeStatistikk(
    val sakstype: Sakstype,
    val antallOppgaver: Int,
    val oppgaverPerFradrag: List<FradragssjekkFradragStatistikk>,
)

internal data class FradragssjekkFradragStatistikk(
    val fradragstype: String,
    val beskrivelse: String?,
    val antallOppgaver: Int,
)

internal fun lagFradragssjekkOppsummering(
    saksresultater: List<FradragssjekkSakResultat>,
): FradragssjekkOppsummering {
    val sakerSomGirOppgavegrunnlag = saksresultater.filterIsInstance<SkalOppretteOppgave>()

    return FradragssjekkOppsummering(
        antallOppgaver = sakerSomGirOppgavegrunnlag.size,
        oppgaverPerSakstype = sakerSomGirOppgavegrunnlag.tilOppgavestatistikk(),
        nøkkeltall = lagOppsummeringPerÅrsak(saksresultater),
    )
}

private data class FradragNokkel(
    val fradragstype: FradragstypeData,
)

private data class Oppgavearsak(
    val sakId: UUID,
    val fradragstype: FradragstypeData,
)

private fun List<SkalOppretteOppgave>.tilOppgavestatistikk(): List<FradragssjekkSakstypeStatistikk> {
    return groupBy { it.sakstype }
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

private fun List<SkalOppretteOppgave>.tilFradragsstatistikk(): List<FradragssjekkFradragStatistikk> {
    val sakIderPerFradrag = mutableMapOf<FradragNokkel, MutableSet<UUID>>()

    for (saksresultat in this) {
        for (arsak in saksresultat.tilOppgavearsaker()) {
            sakIderPerFradrag
                .getOrPut(arsak.tilNokkel()) { mutableSetOf() }
                .add(arsak.sakId)
        }
    }

    return sakIderPerFradrag
        .map { (nokkel, sakIder) -> nokkel.tilStatistikk(sakIder.size) }
        .sortedWith(
            compareByDescending<FradragssjekkFradragStatistikk> { it.antallOppgaver }
                .thenBy { it.fradragstype }
                .thenBy { it.beskrivelse.orEmpty() },
        )
}

private fun SkalOppretteOppgave.tilOppgavearsaker(): List<Oppgavearsak> {
    return oppgaveGrunnlag.mapNotNull { avvik ->
        val fradragstype = avvik.fradragstype ?: return@mapNotNull null

        Oppgavearsak(
            sakId = sakId,
            fradragstype = fradragstype,
        )
    }.distinct()
}

private fun Oppgavearsak.tilNokkel(): FradragNokkel {
    return FradragNokkel(
        fradragstype = fradragstype,
    )
}

private fun FradragNokkel.tilStatistikk(
    antallOppgaver: Int,
): FradragssjekkFradragStatistikk {
    return FradragssjekkFradragStatistikk(
        fradragstype = fradragstype.kategori.name,
        beskrivelse = fradragstype.beskrivelse,
        antallOppgaver = antallOppgaver,
    )
}
