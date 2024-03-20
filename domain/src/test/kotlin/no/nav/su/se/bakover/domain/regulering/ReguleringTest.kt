package no.nav.su.se.bakover.domain.regulering

import arrow.core.nonEmptyListOf
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.test.avsluttetRegulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.iverksattAutomatiskRegulering
import no.nav.su.se.bakover.test.nyFradragperiode
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nyReguleringssupplementInnholdPerType
import no.nav.su.se.bakover.test.opprettetRegulering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

internal class ReguleringTest {

    @Test
    fun `opprett regulering legger inn de regulerte verdiene fra supplementet`() {
        val (_, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            customGrunnlag = listOf(
                nyFradragsgrunnlag(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.Alderspensjon,
                    månedsbeløp = 980.00,
                    periode = stønadsperiode2021.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val actual = Regulering.opprettRegulering(
            id = ReguleringId.generer(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            gjeldendeVedtaksdata = GjeldendeVedtaksdata(
                periode = stønadsperiode2021.periode,
                vedtakListe = nonEmptyListOf(vedtak),
                clock = fixedClock,
            ),
            clock = fixedClock,
            opprettet = fixedTidspunkt,
            sakstype = Sakstype.UFØRE,
            eksternSupplementRegulering = EksternSupplementRegulering(
                bruker = nyReguleringssupplementFor(
                    fnr = fnr,
                    nyReguleringssupplementInnholdPerType(
                        Fradragstype.Alderspensjon,
                        nyFradragperiode(),
                    ),
                ),
                eps = emptyList(),
            ),
            BigDecimal(100),
        )

        actual.getOrFail().let {
            it.grunnlagsdata.fradragsgrunnlag.single().let {
                it.fradrag.månedsbeløp shouldBe 1000.00
                it.fradragstype shouldBe Fradragstype.Alderspensjon
            }
        }
    }

    @Test
    fun `opprett regulering legger inn de regulerte verdiene fra supplementet for EPS`() {
        fail("Not implementet yet")
    }

    @Nested
    internal inner class erÅpen {
        @Test
        fun `opprettet skal være åpen`() {
            opprettetRegulering().erÅpen() shouldBe true
        }

        @Test
        fun `iverksatt skal ikke være åpen`() {
            iverksattAutomatiskRegulering().erÅpen() shouldBe false
        }

        @Test
        fun `avsluttet skal ikke være åpen`() {
            avsluttetRegulering().erÅpen() shouldBe false
        }
    }
}
