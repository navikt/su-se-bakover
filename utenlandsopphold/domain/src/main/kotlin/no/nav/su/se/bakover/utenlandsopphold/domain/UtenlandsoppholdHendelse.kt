package no.nav.su.se.bakover.utenlandsopphold.domain

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

interface UtenlandsoppholdHendelse : Hendelse {
    val utenlandsoppholdId: UUID
    override val hendelseId: UUID
    override val sakId: UUID
    val periode: DatoIntervall
    val dokumentasjon: UtenlandsoppholdDokumentasjon
    val journalposter: List<JournalpostId>
    val utførtAv: NavIdentBruker.Saksbehandler
    override val hendelsestidspunkt: Tidspunkt
    override val versjon: Hendelsesversjon
    val erAnnullert: Boolean
    override val meta: HendelseMetadata
}

fun List<UtenlandsoppholdHendelse>.toRegistrertUtenlandsOpphold(): List<RegistrertUtenlandsopphold> {
    return this.groupBy {
        it.utenlandsoppholdId
    }.map {
        toRegistrertUtenlandsOpphold(it.value.toNonEmptyList())
    }
}

private fun toRegistrertUtenlandsOpphold(
    hendelser: NonEmptyList<UtenlandsoppholdHendelse>,
): RegistrertUtenlandsopphold {
    val (opprettetAv, opprettetTidspunkt) = hendelser.minBy {
        it.versjon
    }.let {
        Pair(it.utførtAv, it.hendelsestidspunkt)
    }
    return hendelser.maxByOrNull { it.versjon }!!.let { hendelse ->
        RegistrertUtenlandsopphold.fraHendelse(
            utenlandsoppholdId = hendelse.utenlandsoppholdId,
            periode = hendelse.periode,
            dokumentasjon = hendelse.dokumentasjon,
            journalposter = hendelse.journalposter,
            opprettetAv = opprettetAv,
            opprettetTidspunkt = opprettetTidspunkt,
            endretAv = hendelse.utførtAv,
            endretTidspunkt = hendelse.hendelsestidspunkt,
            versjon = hendelse.versjon,
            erAnnullert = hendelse.erAnnullert,
        )
    }
}
