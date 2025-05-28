package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføreDefault
import org.junit.jupiter.api.Test
import vilkår.formue.domain.Formueverdier

internal class LeggTilFormuevilkårTest {

    @Test
    fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
        vilkårsvurdertSøknadsbehandlingUføreDefault(customVilkår = listOf(avslåttUførevilkårUtenGrunnlag())).also { (_, avslag) ->
            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().also {
                it.leggTilFormuegrunnlag(
                    request = LeggTilFormuevilkårRequest(
                        behandlingId = avslag.id,
                        formuegrunnlag = nonEmptyListOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                                periode = avslag.periode,
                                epsFormue = null,
                                søkersFormue = Formueverdier.empty(),
                                begrunnelse = null,
                                måInnhenteMerInformasjon = false,
                            ),
                        ),
                        saksbehandler = saksbehandler,
                        tidspunkt = fixedTidspunkt,
                    ),
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                ).getOrFail()
                    .also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
            }
        }
    }

    @Test
    fun `vilkårsvurdert innvilget til vilkårsvurdert innvilget`() {
        vilkårsvurdertSøknadsbehandlingUføreDefault().also { (_, innvilget) ->
            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().also {
                it.leggTilFormuegrunnlag(
                    request = LeggTilFormuevilkårRequest(
                        behandlingId = innvilget.id,
                        formuegrunnlag = nonEmptyListOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                                periode = innvilget.periode,
                                epsFormue = null,
                                søkersFormue = Formueverdier.empty(),
                                begrunnelse = null,
                                måInnhenteMerInformasjon = false,
                            ),
                        ),
                        saksbehandler = saksbehandler,
                        tidspunkt = fixedTidspunkt,
                    ),
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                ).getOrFail().also {
                    it.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                }
            }
        }
    }
}
