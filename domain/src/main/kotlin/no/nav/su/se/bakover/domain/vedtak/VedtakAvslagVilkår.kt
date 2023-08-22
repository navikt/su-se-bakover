package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.grunnlag.krevMinstEttAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import java.time.Clock
import java.util.UUID

data class VedtakAvslagVilkår private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val avslagsgrunner: List<Avslagsgrunn>,
    override val behandling: IverksattSøknadsbehandling.Avslag.UtenBeregning,
    override val periode: Periode,
    override val dokumenttilstand: Dokumenttilstand,
) : Avslagsvedtak {

    override val utbetalingId = null

    init {
        behandling.grunnlagsdataOgVilkårsvurderinger.krevMinstEttAvslag()
        require(dokumenttilstand != Dokumenttilstand.SKAL_IKKE_GENERERE)
        require(behandling.skalSendeVedtaksbrev())
        require(periode == behandling.periode)
    }

    companion object {
        fun from(
            avslag: IverksattSøknadsbehandling.Avslag.UtenBeregning,
            clock: Clock,
        ): VedtakAvslagVilkår {
            return VedtakAvslagVilkår(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = avslag,
                saksbehandler = avslag.saksbehandler,
                attestant = avslag.attesteringer.hentSisteAttestering().attestant,
                periode = avslag.periode,
                avslagsgrunner = avslag.avslagsgrunner,
                // Per tidspunkt er det implisitt at vi genererer og lagrer brev samtidig som vi oppretter vedtaket.
                // TODO jah: Hvis vi heller flytter brevgenereringen ut til ferdigstill-jobben, blir det mer riktig og sette denne til IKKE_GENERERT_ENDA
                dokumenttilstand = Dokumenttilstand.GENERERT,
            )
        }

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            avslagsgrunner: List<Avslagsgrunn>,
            behandling: IverksattSøknadsbehandling.Avslag.UtenBeregning,
            periode: Periode,
            dokumenttilstand: Dokumenttilstand?,
        ) = VedtakAvslagVilkår(
            id = id,
            opprettet = opprettet,
            behandling = behandling,
            saksbehandler = saksbehandler,
            attestant = attestant,
            periode = periode,
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

    override fun erInnvilget(): Boolean = false
    override fun erOpphør(): Boolean = false
    override fun erStans(): Boolean = false
    override fun erGjenopptak(): Boolean = false
}
