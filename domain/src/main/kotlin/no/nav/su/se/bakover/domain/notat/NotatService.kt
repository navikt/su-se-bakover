package no.nav.su.se.bakover.domain.notat

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.time.Clock
import java.util.UUID

interface NotatService {
    fun hentNotaterForSak(sakId: UUID): Either<NotatFeil, List<Notat>>

    fun hentNotatMedVedlegg(sakId: UUID, notatId: UUID): Either<NotatFeil, NotatMedVedlegg>

    fun opprettNotat(
        sakId: UUID,
        referanseId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat>

    fun oppdaterNotat(
        sakId: UUID,
        notatId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat>

    fun leggTilVedlegg(
        sakId: UUID,
        notatId: UUID,
        filnavn: String,
        mimeType: String,
        innhold: ByteArray,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, NotatVedlegg>

    fun slettVedlegg(
        sakId: UUID,
        notatId: UUID,
        vedleggId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Unit>
}
