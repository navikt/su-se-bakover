package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.FeilVedHentingAvGjeldendeVedtaksdataForPeriode
import no.nav.su.se.bakover.domain.vilkår.flyktningVilkår
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class SøknadsbehandlingGjeldendeVedtaksdataTidligerePeriodeTest {

    @Test
    fun `gjeldende vedtaksdata finnes ikke`() {
        val (sak, innvilget) = søknadsbehandlingVilkårsvurdertInnvilget()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock<SakService> {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).let {
            val actual = it.søknadsbehandlingService.gjeldendeVedtaksdataForTidligerePeriode(
                sak.id,
                innvilget.id,
            )

            actual shouldBe FeilVedHentingAvGjeldendeVedtaksdataForPeriode.GjeldendeVedtaksdataFinnesIkke.left()
            verify(it.sakService).hentSak(argShouldBe(sak.id))
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `gjeldende vedtaksdata for tidligere periode finnes`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val innvilget = søknadsbehandlingVilkårsvurdertInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2022)),
            clock = enUkeEtterFixedClock,
            sakOgSøknad = sak to vedtak.behandling.søknad,
        ).second
        val sakMedTidligereBehandling = sak.copy(
            behandlinger = sak.behandlinger.copy(
                søknadsbehandlinger = sak.behandlinger.søknadsbehandlinger + innvilget,
            ),
        )

        SøknadsbehandlingServiceAndMocks(
            sakService = mock<SakService> {
                on { hentSak(any<UUID>()) } doReturn sakMedTidligereBehandling.right()
            },
        ).let {
            val actual = it.søknadsbehandlingService.gjeldendeVedtaksdataForTidligerePeriode(
                sak.id,
                innvilget.id,
            )

            actual.getOrFail().let {
                it.first shouldBe vedtak.periode
                it.second.shouldBeEqualToExceptId(
                    GrunnlagsdataOgVilkårsvurderingerRevurdering(
                        grunnlagsdata = vedtak.behandling.grunnlagsdata,
                        vilkårsvurderinger = VilkårsvurderingerRevurdering.Uføre(
                            uføre = vedtak.behandling.vilkårsvurderinger.uføreVilkårKastHvisAlder(),
                            lovligOpphold = vedtak.behandling.vilkårsvurderinger.lovligOpphold,
                            formue = vedtak.behandling.vilkårsvurderinger.formue,
                            utenlandsopphold = vedtak.behandling.vilkårsvurderinger.utenlandsopphold,
                            opplysningsplikt = vedtak.behandling.vilkårsvurderinger.opplysningsplikt,
                            flyktning = vedtak.behandling.vilkårsvurderinger.flyktningVilkår().getOrFail(),
                            fastOpphold = vedtak.behandling.vilkårsvurderinger.fastOpphold,
                            personligOppmøte = vedtak.behandling.vilkårsvurderinger.personligOppmøte,
                            institusjonsopphold = vedtak.behandling.vilkårsvurderinger.institusjonsopphold,
                        ),
                    ),
                )
            }
            verify(it.sakService).hentSak(argShouldBe(sak.id))
            it.verifyNoMoreInteractions()
        }
    }
}
