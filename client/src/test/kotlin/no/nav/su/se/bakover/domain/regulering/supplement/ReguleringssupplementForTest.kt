package no.nav.su.se.bakover.domain.regulering.supplement

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyEksterndata
import no.nav.su.se.bakover.test.nyEksternvedtakEndring
import no.nav.su.se.bakover.test.nyEksternvedtakRegulering
import no.nav.su.se.bakover.test.nyFradragperiodeEndring
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nyReguleringssupplementInnholdPerType
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype

class ReguleringssupplementForTest {

    @Test
    fun `henter ut eksterne data fra alle fradragstypene`() {
        val alderspensjon = nyReguleringssupplementInnholdPerType()
        val dagpenger = nyReguleringssupplementInnholdPerType(
            kategori = Fradragstype.Kategori.Dagpenger,
            vedtak = listOf(
                nyEksternvedtakEndring(fradrag = listOf(nyFradragperiodeEndring(eksterndata = nyEksterndata()))),
                nyEksternvedtakRegulering(),
            ),
        )
        val supplementFor = nyReguleringssupplementFor(Fnr.generer(), alderspensjon, dagpenger)

        supplementFor.eksternedataForAlleTyper().let {
            it.size shouldBe 4

            // alderspensjon
            it.first() shouldBe alderspensjon.endringsvedtak.eksterneData().single()
            alderspensjon.reguleringsvedtak.size shouldBe 1
            it[1] shouldBe alderspensjon.reguleringsvedtak.single().eksterneData().single()

            // dagpenger
            it[2] shouldBe dagpenger.endringsvedtak.eksterneData().single()
            dagpenger.reguleringsvedtak.size shouldBe 1
            it.last() shouldBe dagpenger.endringsvedtak.eksterneData().single()
        }
    }
}
