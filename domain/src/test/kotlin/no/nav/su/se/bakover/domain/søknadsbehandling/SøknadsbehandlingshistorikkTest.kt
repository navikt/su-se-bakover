package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import org.junit.jupiter.api.Test

internal class SøknadsbehandlingshistorikkTest {

    @Test
    fun `lager en ny historikk med 1 innslag`() {
        Søknadsbehandlingshistorikk.nyHistorikk(
            nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.StartetBehandling),
        ).let {
            it.historikk.size shouldBe 1
        }
    }

    @Test
    fun `lager ny historikk basert på eksisterende historikk`() {
        val eksisterende = listOf(
            nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.StartetBehandling),
            nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.OppdatertStønadsperiode),
        )
        Søknadsbehandlingshistorikk.createFromExisting(eksisterende).let {
            it.historikk.size shouldBe 2
            it.historikk.first().handling shouldBe SøknadsbehandlingsHandling.StartetBehandling
            it.historikk.last().handling shouldBe SøknadsbehandlingsHandling.OppdatertStønadsperiode
        }
    }

    @Test
    fun `Kan legge til en ny hendelse`() {
        Søknadsbehandlingshistorikk.nyHistorikk(
            nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.StartetBehandling),
        ).leggTilNyHendelse(
            nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.OppdatertStønadsperiode),
        ).let {
            it.historikk.size shouldBe 2
            it.historikk.first().handling shouldBe SøknadsbehandlingsHandling.StartetBehandling
            it.historikk.last().handling shouldBe SøknadsbehandlingsHandling.OppdatertStønadsperiode
        }
    }

    @Test
    fun `Kan legge til flere nye hendelser`() {
        Søknadsbehandlingshistorikk.nyHistorikk(
            nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.StartetBehandling),
        ).leggTilNyeHendelser(
            listOf(
                nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.OppdatertStønadsperiode),
                nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Uførhet)),
            ),
        ).let {
            it.historikk.size shouldBe 3
            it.historikk.first().handling shouldBe SøknadsbehandlingsHandling.StartetBehandling
            it.historikk.last().handling shouldBe SøknadsbehandlingsHandling.OppdatertVilkår(Inngangsvilkår.Uførhet)
        }
    }
}
