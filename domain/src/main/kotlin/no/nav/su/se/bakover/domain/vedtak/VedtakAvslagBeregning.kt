package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.grunnlag.krevAlleVilkårInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import java.time.Clock
import java.util.UUID

data class VedtakAvslagBeregning private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattSøknadsbehandling.Avslag.MedBeregning,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    override val beregning: Beregning,
    override val avslagsgrunner: List<Avslagsgrunn>,
    override val dokumenttilstand: Dokumenttilstand,
) : Avslagsvedtak {

    override val utbetalingId = null

    init {
        behandling.grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
        require(dokumenttilstand != Dokumenttilstand.SKAL_IKKE_GENERERE)
        require(behandling.skalSendeVedtaksbrev())
        require(periode == behandling.periode)
    }

    companion object {

        fun from(
            avslag: IverksattSøknadsbehandling.Avslag.MedBeregning,
            clock: Clock,
        ) = VedtakAvslagBeregning(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            behandling = avslag,
            beregning = avslag.beregning,
            saksbehandler = avslag.saksbehandler,
            attestant = avslag.attesteringer.hentSisteAttestering().attestant,
            periode = avslag.periode,
            avslagsgrunner = avslag.avslagsgrunner,
            // Per tidspunkt er det implisitt at vi genererer og lagrer brev samtidig som vi oppretter vedtaket.
            // TODO jah: Hvis vi heller flytter brevgenereringen ut til ferdigstill-jobben, blir det mer riktig og sette denne til IKKE_GENERERT_ENDA
            dokumenttilstand = Dokumenttilstand.GENERERT,
        )

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            behandling: IverksattSøknadsbehandling.Avslag.MedBeregning,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            beregning: Beregning,
            avslagsgrunner: List<Avslagsgrunn>,
            dokumenttilstand: Dokumenttilstand?,
        ) = VedtakAvslagBeregning(
            id = id,
            opprettet = opprettet,
            behandling = behandling,
            saksbehandler = saksbehandler,
            attestant = attestant,
            periode = periode,
            beregning = beregning,
            avslagsgrunner = avslagsgrunner,
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

    override fun harIdentifisertBehovForFremtidigAvkorting() = false

    override fun accept(visitor: VedtakVisitor) {
        visitor.visit(this)
    }

    override fun erOpphør(): Boolean = false
    override fun erStans(): Boolean = false
    override fun erGjenopptak(): Boolean = false
}
