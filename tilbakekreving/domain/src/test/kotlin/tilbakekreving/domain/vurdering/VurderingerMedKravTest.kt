package tilbakekreving.domain.vurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import no.nav.su.se.bakover.test.nyVurderinger
import org.junit.jupiter.api.Test

class VurderingerMedKravTest {

    @Test
    fun `periodene i kravgrunnlaget og vurderingene må samsvare`() {
        val kravgrunnlag = kravgrunnlag()
        val vurderingSomSamsvarerMedKravgrunnlag = nyVurderinger()
        VurderingerMedKrav.utledFra(
            vurderinger = vurderingSomSamsvarerMedKravgrunnlag,
            kravgrunnlag = kravgrunnlag,
        ).shouldBeRight()

        val vurderingSomIkkeSamsvarerMedKravgrunnlag = nyVurderinger(
            perioderVurderinger = nonEmptyListOf(
                Vurderinger.Periodevurdering(periode = mai(2023), vurdering = Vurdering.SkalTilbakekreve),
            ),
        )

        VurderingerMedKrav.utledFra(
            vurderinger = vurderingSomIkkeSamsvarerMedKravgrunnlag,
            kravgrunnlag = kravgrunnlag,
        ) shouldBe KunneIkkeVurdereTilbakekrevingsbehandling.VurderingeneStemmerIkkeOverensMedKravgrunnlaget.left()
    }

    @Test
    fun `har minst en periode som skal tilbakekreves`() {
        val kravgrunnlag = kravgrunnlag()
        val vurderingSomSamsvarerMedKravgrunnlag = nyVurderinger()
        VurderingerMedKrav.utledFra(
            vurderinger = vurderingSomSamsvarerMedKravgrunnlag,
            kravgrunnlag = kravgrunnlag,
        ).getOrFail().minstEnPeriodeSkalTilbakekreves() shouldBe true
    }

    @Test
    fun `har ingen perioder som skal tilbakekreves`() {
        val kravgrunnlag = kravgrunnlag()
        val vurderingSomSamsvarerMedKravgrunnlag = nyVurderinger(
            perioderVurderinger = nonEmptyListOf(
                Vurderinger.Periodevurdering(periode = januar(2021), vurdering = Vurdering.SkalIkkeTilbakekreve),
            ),
        )
        VurderingerMedKrav.utledFra(
            vurderinger = vurderingSomSamsvarerMedKravgrunnlag,
            kravgrunnlag = kravgrunnlag,
        ).getOrFail().minstEnPeriodeSkalTilbakekreves() shouldBe false
    }
}
