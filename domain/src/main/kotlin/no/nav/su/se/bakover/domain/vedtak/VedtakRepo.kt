package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import vedtak.domain.Vedtak
import vedtak.domain.VedtakSomKanRevurderes
import java.time.LocalDate
import java.util.UUID

interface VedtakRepo {
    fun hentVedtakForId(vedtakId: UUID): Vedtak?
    fun hentForRevurderingId(revurderingId: RevurderingId): Vedtak?
    fun hentForMåned(måned: Måned): List<VedtaksammendragForSak>
    fun hentForBrukerFødselsnumreOgFraOgMedMåned(fødselsnumre: List<Fnr>, fraOgMed: Måned): List<VedtaksammendragForSak>

    /**
     * henter en sak, gitt en liste med eps fnr fra en gitt måned.
     *
     * TODO - når alder implementeres, kan denne matche for samme søkeren 2 ganger.
     * merk også at dersom EPS er registrert på flere saker, vil du få flere også
     */
    fun hentForEpsFødselsnumreOgFraOgMedMåned(fnr: List<Fnr>, fraOgMedEllerSenere: Måned): List<VedtaksammendragForSak>

    /** Denne vil feile dersom vedtaket er lagret før. */
    fun lagre(vedtak: Vedtak)

    /** Denne vil feile dersom vedtaket er lagret før. */
    fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext)

    /** En spesialoperasjon som kun skal brukes ved resending av utbetalinger */
    fun oppdaterUtbetalingId(vedtakId: UUID, utbetalingId: UUID30, sessionContext: SessionContext? = null)
    fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes?
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
    fun hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed: LocalDate): List<UUID>
}
