package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.LocalDate
import java.util.UUID

data class AutomatiskEllerManuelleSak(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt,
    val behandlingId: UUID,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val vedtakType: VedtakType,
    val behandlingType: BehandlingType,
)

enum class BehandlingType {
    AUTOMATISK,
    MANUELL,
}

enum class VedtakType {
    SØKNAD, // Innvilget Søknadsbehandling                  -> EndringIYtelse
    AVSLAG, // Avslått Søknadsbehandling                    -> Avslag
    ENDRING, // Revurdering innvilget                       -> EndringIYtelse
    INGEN_ENDRING, // Revurdering mellom 2% og 10% endring  -> IngenEndringIYtelse
    OPPHØR, // Revurdering ført til opphør                  -> EndringIYtelse
    STANS_AV_YTELSE,
    GJENOPPTAK_AV_YTELSE,
    AVVIST_KLAGE,
}

interface VedtakRepo {
    fun hentForVedtakId(vedtakId: UUID): Vedtak?
    fun hentForSakId(sakId: UUID): List<Vedtak>
    fun hentAktive(dato: LocalDate): List<VedtakSomKanRevurderes.EndringIYtelse>
    fun hentVedtakSomKanReguleres(dato: LocalDate): List<AutomatiskEllerManuelleSak>
    fun lagre(vedtak: Vedtak)
    fun lagre(vedtak: Vedtak, sessionContext: TransactionContext)
    fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes?
    fun hentAlle(): List<Vedtak>
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
}
