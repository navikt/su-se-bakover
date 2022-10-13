package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
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
    val erAnnulert: Boolean
    override val meta: HendelseMetadata
}
