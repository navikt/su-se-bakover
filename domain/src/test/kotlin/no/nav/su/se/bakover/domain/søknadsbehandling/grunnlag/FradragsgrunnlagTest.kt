package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag

import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import org.junit.jupiter.api.Test

internal class FradragsgrunnlagTest {
    @Test
    fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
        underkjentSøknadsbehandlingUføre(
            customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
        ).also { (_, underkjent) ->
            (underkjent as UnderkjentSøknadsbehandling.Avslag.MedBeregning).let {
                underkjent.oppdaterFradragsgrunnlag(
                    saksbehandler = saksbehandler,
                    emptyList(),
                    clock = fixedClock,
                ).getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }
}
