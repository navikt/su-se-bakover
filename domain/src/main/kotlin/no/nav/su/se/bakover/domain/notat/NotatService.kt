package no.nav.su.se.bakover.domain.notat

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.time.Clock
import java.util.UUID

interface NotatService {
    fun hentNotaterForSak(sakId: UUID): Either<NotatFeil, List<Notat>>

    fun hentNotatMedVedlegg(sakId: UUID, notatId: UUID): Either<NotatFeil, NotatMedVedlegg>

    fun hentNotataForReferanse(sakId: UUID, referanseId: UUID, referanseType: ReferanseType): Either<NotatFeil, NotatResponse>

    fun opprettNotat(
        sakId: UUID,
        referanseId: UUID,
        referanseType: ReferanseType,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat>

    fun oppdaterNotatSaksbehandler(
        sakId: UUID,
        notatId: UUID,
        notat: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<NotatFeil, Notat>

    fun oppdaterNotatAttestant(
        sakId: UUID,
        notatId: UUID,
        attestantNotat: String,
        attestant: NavIdentBruker.Attestant,
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
