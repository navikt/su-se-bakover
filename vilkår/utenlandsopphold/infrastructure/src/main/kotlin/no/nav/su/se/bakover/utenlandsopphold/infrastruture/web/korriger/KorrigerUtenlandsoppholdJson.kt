package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.korriger

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.UtenlandsoppholdDokumentasjonJson
import java.util.UUID

/**
 * @param journalposter kan være tom dersom det ikke er knyttet noen journalposter til utenlandsoppholdet.
 */
data class KorrigerUtenlandsoppholdJson(
    val periode: PeriodeJson,
    val journalposter: List<String>,
    val dokumentasjon: UtenlandsoppholdDokumentasjonJson,
    val begrunnelse: String?,
    val saksversjon: Long,
) {
    fun toCommand(
        sakId: UUID,
        opprettetAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
        korrigererVersjon: Long,
    ) = KorrigerUtenlandsoppholdCommand(
        sakId = sakId,
        periode = periode.toDatoIntervall(),
        dokumentasjon = dokumentasjon.toDomain(),
        journalposter = journalposter.map { JournalpostId(it) },
        begrunnelse = begrunnelse,
        opprettetAv = opprettetAv,
        correlationId = correlationId,
        brukerroller = brukerroller,
        klientensSisteSaksversjon = Hendelsesversjon(saksversjon),
        korrigererVersjon = Hendelsesversjon(korrigererVersjon),
    )
}
