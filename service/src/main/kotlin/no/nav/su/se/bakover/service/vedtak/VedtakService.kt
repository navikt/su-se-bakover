package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import java.time.LocalDate
import java.util.UUID

interface VedtakService {
    fun lagre(vedtak: Vedtak)
    fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext)
    fun hentForVedtakId(vedtakId: UUID): Vedtak?
    fun hentForRevurderingId(revurderingId: UUID): Vedtak?
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?

    /**
     * Henter en liste med fødselsnumre som har rett på stønad for en gitt måned.
     * Dersom måneden har opphørt igjen, vil fødselsnummeret filtreres vekk.
     *
     * Merk at skjermede personer kan inkluderes i resultatsettet, men vi inkluderer ikke annen persondata enn selve fødselsnummeret.
     */
    fun hentInnvilgetFnrForMåned(måned: Måned): InnvilgetForMåned
    fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes?
    fun hentForFødselsnumreOgFraOgMedMåned(fødselsnumre: List<Fnr>, fraOgMed: Måned): List<Vedtaksammendrag>
    fun hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed: LocalDate): List<UUID>
}
