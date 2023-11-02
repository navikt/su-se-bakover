package no.nav.su.se.bakover.domain.vedtak

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.grunnlag.krevAlleVilkårInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

data class VedtakInnvilgetSøknadsbehandling private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattSøknadsbehandling.Innvilget,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    override val beregning: Beregning,
    override val simulering: Simulering,
    override val utbetalingId: UUID30,
    override val dokumenttilstand: Dokumenttilstand,
) : VedtakEndringIYtelse, VedtakIverksattSøknadsbehandling {

    init {
        behandling.grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
        require(dokumenttilstand != Dokumenttilstand.SKAL_IKKE_GENERERE)
        require(periode == behandling.periode)
    }

    companion object {

        fun fromSøknadsbehandling(
            id: UUID = UUID.randomUUID(),
            søknadsbehandling: IverksattSøknadsbehandling.Innvilget,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakInnvilgetSøknadsbehandling(
            id = id,
            opprettet = Tidspunkt.now(clock),
            periode = søknadsbehandling.periode,
            behandling = søknadsbehandling,
            beregning = søknadsbehandling.beregning,
            simulering = søknadsbehandling.simulering,
            saksbehandler = søknadsbehandling.saksbehandler,
            attestant = søknadsbehandling.attesteringer.hentSisteAttestering().attestant,
            utbetalingId = utbetalingId,
            dokumenttilstand = søknadsbehandling.dokumenttilstandForBrevvalg(),
        )

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            behandling: IverksattSøknadsbehandling.Innvilget,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            beregning: Beregning,
            simulering: Simulering,
            utbetalingId: UUID30,
            dokumenttilstand: Dokumenttilstand?,
        ) = VedtakInnvilgetSøknadsbehandling(
            id = id,
            opprettet = opprettet,
            behandling = behandling,
            saksbehandler = saksbehandler,
            attestant = attestant,
            periode = periode,
            beregning = beregning,
            simulering = simulering,
            utbetalingId = utbetalingId,
            dokumenttilstand = dokumenttilstand.setDokumentTilstandBasertPåBehandlingHvisNull(behandling),
        )
    }

    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return when (dokumenttilstand) {
            Dokumenttilstand.SKAL_IKKE_GENERERE -> throw IllegalStateException("Skal ha brev ved avslag")
            Dokumenttilstand.IKKE_GENERERT_ENDA -> true
            // Her har vi allerede generert brev fra før og ønsker ikke generere et til.
            Dokumenttilstand.GENERERT,
            Dokumenttilstand.JOURNALFØRT,
            Dokumenttilstand.SENDT,
            -> false
        }
    }

    override fun erInnvilget(): Boolean = true
    override fun erOpphør(): Boolean = false
    override fun erStans(): Boolean = false
    override fun erGjenopptak(): Boolean = false
}
