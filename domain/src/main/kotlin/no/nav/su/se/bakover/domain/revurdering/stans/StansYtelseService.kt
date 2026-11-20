package no.nav.su.se.bakover.domain.revurdering.stans

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.tid.FørsteDagIMåneden
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.statistikk.Kode6Vurdering

interface StansYtelseService {

    fun vurderKode6ForAutomatiskStans(
        sak: Sak,
        fraOgMed: FørsteDagIMåneden,
    ): Kode6Vurdering

    fun stansAvYtelse(
        request: StansYtelseRequest,
    ): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse>

    /**
     * Kun for automatisk behandling. Brukerflyter skal bruke [stansAvYtelse], som vurderer kode 6 før transaksjonen.
     * [kode6Vurdering] skal forberedes med [vurderKode6ForAutomatiskStans] før transaksjonen.
     *
     * Konsument er ansvarlig for [transactionContext] og tilhørende commit/rollback. Dette innebærer også at
     * konsument må kalle aktuelle callbacks som returneres på et fornuftig tidspunkt.
     *
     * @throws IverksettStansAvYtelseTransactionException for alle feilsituasjoner vi selv har rådighet over.
     *
     * @return [StansAvYtelseITransaksjonResponse.revurdering] simulert revurdering for stans
     * @return [StansAvYtelseITransaksjonResponse.sendStatistikkCallback] callback som publiserer statistikk på kafka
     */
    fun stansAvYtelseITransaksjon(
        request: StansYtelseRequest,
        transactionContext: TransactionContext,
        kode6Vurdering: Kode6Vurdering,
    ): StansAvYtelseRevurdering.SimulertStansAvYtelse

    fun iverksettStansAvYtelse(
        revurderingId: RevurderingId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse>

    /**
     * Kun for automatisk behandling. Brukerflyter skal bruke [iverksettStansAvYtelse], som vurderer kode 6 før transaksjonen.
     * [kode6Vurdering] skal forberedes med [vurderKode6ForAutomatiskStans] før transaksjonen.
     *
     * Konsument er ansvarlig for [transactionContext] og tilhørende commit/rollback. Dette innebærer også at
     * konsument må kalle aktuelle callbacks som returneres på et fornuftig tidspunkt.
     *
     * @throws IverksettStansAvYtelseTransactionException for alle feilsituasjoner vi selv har rådighet over.
     *
     * @return [IverksettStansAvYtelseITransaksjonResponse.revurdering] iverksatt revurdering for stans
     * @return [IverksettStansAvYtelseITransaksjonResponse.vedtak] vedtak for stans
     * @return [IverksettStansAvYtelseITransaksjonResponse.sendUtbetalingCallback] callback som publiserer utbetalinger på kø
     * @return [IverksettStansAvYtelseITransaksjonResponse.sendStatistikkCallback] callback som publiserer statistikk på kafka
     */
    fun iverksettStansAvYtelseITransaksjon(
        revurderingId: RevurderingId,
        attestant: NavIdentBruker.Attestant,
        transactionContext: TransactionContext,
        kode6Vurdering: Kode6Vurdering,
    ): IverksettStansAvYtelseITransaksjonResponse
}
