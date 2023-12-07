package no.nav.su.se.bakover.utenlandsopphold.domain.korriger

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

/**
 * @param klientensSisteSaksversjon For utenlandsopphold er entitetId == sakId. Dette feltet tilsvarer den siste hendelsen som er registrert på saken. Brukes for å forhindre parallelle endringer.
 * @param korrigererVersjon Hvilken hendelse vi skal korrigere.
 */
data class KorrigerUtenlandsoppholdCommand(
    val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val begrunnelse: String?,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: List<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val korrigererVersjon: Hendelsesversjon,
) {

    /**
     * @param nesteVersjon Det kan ha skjedd andre endringer på saken så den trenger ikke være [korrigererHendelse] sin versjon + 1.
     */
    fun toHendelse(
        korrigererHendelse: UtenlandsoppholdHendelse,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): KorrigerUtenlandsoppholdHendelse {
        return KorrigerUtenlandsoppholdHendelse.create(
            korrigererHendelse = korrigererHendelse,
            nesteVersjon = nesteVersjon,
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            begrunnelse = begrunnelse,
            utførtAv = opprettetAv,
            clock = clock,
        )
    }

    fun toMetadata(): DefaultHendelseMetadata {
        return DefaultHendelseMetadata(
            correlationId = correlationId,
            ident = opprettetAv,
            brukerroller = brukerroller,
        )
    }
}
