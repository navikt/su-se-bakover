package vilkår.opplysningsplikt.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import org.junit.jupiter.api.Test

class OpplysningspliktVilkårTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val vilkår = tilstrekkeligDokumentert()

        vilkår.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(
                vilkår,
                OpplysningspliktVilkår.Vurdert::vurderingsperioder,
                OpplysningspliktVilkår.Vurdert::grunnlag,
            )
            it.vurderingsperioder.size shouldBe 1
            it.vurderingsperioder.first().shouldBeEqualToIgnoringFields(
                vilkår.vurderingsperioder.first(),
                VurderingsperiodeOpplysningsplikt::id,
                VurderingsperiodeOpplysningsplikt::grunnlag,
            )
            it.vurderingsperioder.first().id shouldNotBe vilkår.vurderingsperioder.first().id
        }
    }
}
