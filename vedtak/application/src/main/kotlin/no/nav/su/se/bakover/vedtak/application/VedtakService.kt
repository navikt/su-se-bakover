package no.nav.su.se.bakover.vedtak.application

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import vedtak.domain.KunneIkkeStarteNySøknadsbehandling
import vedtak.domain.Vedtak
import vedtak.domain.VedtakSomKanRevurderes
import java.time.LocalDate
import java.util.UUID

interface VedtakService {
    fun lagre(vedtak: Vedtak)
    fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext)
    fun hentForVedtakId(vedtakId: UUID): Vedtak?
    fun hentForRevurderingId(revurderingId: RevurderingId): Vedtak?
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?

    /**
     * Henter en liste med fødselsnumre som har rett på stønad for en gitt måned.
     * Dersom måneden har opphørt igjen, vil fødselsnummeret filtreres vekk.
     *
     * Merk at skjermede personer kan inkluderes i resultatsettet, men vi inkluderer ikke annen persondata enn selve fødselsnummeret.
     */
    fun hentInnvilgetFnrForMåned(måned: Måned): InnvilgetForMåned
    fun hentInnvilgetFnrFraOgMedMåned(måned: Måned, inkluderEps: Boolean): List<Fnr>
    fun hentForUtbetaling(utbetalingId: UUID30, sessionContext: SessionContext? = null): VedtakSomKanRevurderes?
    fun hentForBrukerFødselsnumreOgFraOgMedMåned(fødselsnumre: List<Fnr>, fraOgMed: Måned): List<VedtaksammendragForSak>
    fun hentForEpsFødselsnumreOgFraOgMedMåned(fnr: List<Fnr>, fraOgMedEllerSenere: Måned): List<VedtaksammendragForSak>
    fun hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed: LocalDate): List<UUID>

    fun startNySøknadsbehandlingForAvslag(
        sakId: UUID,
        vedtakId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeStarteNySøknadsbehandling, Søknadsbehandling>
}
