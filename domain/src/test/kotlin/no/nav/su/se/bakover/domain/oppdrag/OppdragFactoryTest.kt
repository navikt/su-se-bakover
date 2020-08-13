package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test

internal class OppdragFactoryTest {
    @Test
    fun `no existing oppdrag`() {
        val oppdragDto = OppdragFactory(
            behandling = behandling(),
            sak = sak()
        ).build().toDto()

        oppdragDto.endringskode shouldBe Oppdrag.Endringskode.NY
        oppdragDto.oppdragslinjer shouldHaveSize 1
        oppdragDto.oppdragslinjer.first().endringskode shouldBe Oppdragslinje.Endringskode.NY
    }

    /**
     * L1 |-----|
     * L2       |-----|
     */
    @Test
    fun `no overlap in oppdragslinjer`() {
    }

    /**
     * L1 |-----|
     * L2    |-----|
     */
    @Test
    fun `overlap in oppdragslinjer`() {
    }

    fun behandling() = Behandling(søknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build()))
    fun sak() = Sak(fnr = Fnr("12345678910"), behandlinger = mutableListOf(behandling()))
}
