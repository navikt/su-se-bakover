package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.LocalDate
import java.util.UUID

interface VedtakService {
    fun lagre(vedtak: Vedtak)
    fun lagre(vedtak: Vedtak, sessionContext: TransactionContext)
    fun hentForVedtakId(vedtakId: UUID): Vedtak?
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
    fun hentAktiveFnr(fomDato: LocalDate): List<Fnr>
    fun kopierGjeldendeVedtaksdata(
        sakId: UUID,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata>

    fun historiskGrunnlagForVedtaksperiode(
        sakId: UUID,
        vedtakId: UUID,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata>
}

sealed class KunneIkkeKopiereGjeldendeVedtaksdata {
    object FantIkkeSak : KunneIkkeKopiereGjeldendeVedtaksdata()
    object FantIngenVedtak : KunneIkkeKopiereGjeldendeVedtaksdata()
    data class UgyldigPeriode(val cause: Periode.UgyldigPeriode) : KunneIkkeKopiereGjeldendeVedtaksdata()
}

sealed class KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak {
    object FantIkkeVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
    object IngenTidligereVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
}
