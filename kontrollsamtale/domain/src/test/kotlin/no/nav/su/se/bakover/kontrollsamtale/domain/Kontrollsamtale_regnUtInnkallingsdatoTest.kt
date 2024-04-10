package no.nav.su.se.bakover.kontrollsamtale.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class Kontrollsamtale_regnUtInnkallingsdatoTest {

    @Test
    fun `mottaksdatoSøknad før eller på stønadstart - standard innkalling`() {
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 30.november(2020),
            today = 31.desember(2020),
        ) shouldBe 1.mai(2021)

        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 31.desember(2020),
            today = 31.desember(2020),
        ) shouldBe 1.mai(2021)

        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.januar(2021),
            today = 1.januar(2021),
        ) shouldBe 1.mai(2021)
    }

    @Test
    fun `under 5 måneders periode - ingen innkalling`() {
        regnUtInnkallingsdato(
            stønadsperiode = januar(2021)..april(2021),
            mottattSøknadDato = 1.desember(2020),
            today = 15.desember(2020),
        ) shouldBe null

        regnUtInnkallingsdato(
            stønadsperiode = januar(2021)..mars(2021),
            mottattSøknadDato = 1.desember(2020),
            today = 15.desember(2021),
        ) shouldBe null

        regnUtInnkallingsdato(
            stønadsperiode = januar(2021)..februar(2021),
            mottattSøknadDato = 1.desember(2020),
            today = 15.desember(2021),
        ) shouldBe null

        regnUtInnkallingsdato(
            stønadsperiode = januar(2021)..januar(2021),
            mottattSøknadDato = 1.desember(2020),
            today = 15.desember(2021),
        ) shouldBe null
    }

    @Test
    fun `5 måneders periode - `() {
        regnUtInnkallingsdato(
            stønadsperiode = januar(2021)..mai(2021),
            mottattSøknadDato = 1.januar(2021),
            today = 15.januar(2021),
        ) shouldBe 1.mars(2021)
    }

    @Test
    fun `6 måneders periode - `() {
        regnUtInnkallingsdato(
            stønadsperiode = januar(2021)..juni(2021),
            mottattSøknadDato = 1.januar(2021),
            today = 15.januar(2021),
        ) shouldBe 1.april(2021)
    }

    @Test
    fun `7 - 11 måneders periode - standard innkalling`() {
        listOf(
            januar(2021)..juli(2021),
            januar(2021)..august(2021),
            januar(2021)..september(2021),
            januar(2021)..oktober(2021),
            januar(2021)..november(2021),
        ).forEach { stønadsperiode ->
            regnUtInnkallingsdato(
                stønadsperiode = stønadsperiode,
                mottattSøknadDato = 1.januar(2021),
                today = 15.januar(2021),
            ) shouldBe 1.mai(2021)
        }
    }

    @Test
    fun `today på standard innkallingsdato - standard innkalling`() {
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.januar(2021),
            today = 1.mai(2021),
        ) shouldBe 1.mai(2021)
    }

    @Test
    fun `today etter standard innkallingsdato - en måned forskjøvet`() {
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.januar(2021),
            today = 2.mai(2021),
        ) shouldBe 1.juni(2021)
    }

    @Test
    fun `mottatt søknad 1 måned etter stønadsstart - en måned forskjøvet `() {
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.februar(2021),
            today = 1.februar(2021),
        ) shouldBe 1.juni(2021)

        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.februar(2021),
            today = 1.juni(2021),
        ) shouldBe 1.juni(2021)
    }

    @Test
    fun `mottatt søknad 2 måneder etter stønadsstart - to måned forskjøvet `() {
        // TODO jah - sjekk med John Are og Camilla. Muligens skal vi ikke forskyve disse 1-1.
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.mars(2021),
            today = 1.februar(2021),
        ) shouldBe 1.juli(2021)
    }

    @Test
    fun `mottatt søknad 5 måneder etter stønadsstart - siste mulige innkallingsdag `() {
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.juni(2021),
            today = 1.juli(2021),
        ) shouldBe 1.oktober(2021)
    }

    @Test
    fun `mottatt søknad 6 måneder etter stønadsstart - ingen innkalling `() {
        regnUtInnkallingsdato(
            stønadsperiode = år(2021),
            mottattSøknadDato = 1.juli(2021),
            today = 1.august(2021),
        ) shouldBe null
    }
}
