package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingTest_Søknadstype {

    @Test
    fun `eksisterende åpen og ny åpen - exception`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingSimulert().second,
        ).hentSøknadstypeFor(UUID.randomUUID()) shouldBe KunneIkkeAvgjøreOmFørstegangEllerNyPeriode.left()
    }

    @Test
    fun `eksisterende åpen og eksisterende åpen samme som parameter - exception`() {
        val underBehandling = søknadsbehandlingSimulert().second
        listOf<Søknadsbehandling>(
            søknadsbehandlingSimulert().second,
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe KunneIkkeAvgjøreOmFørstegangEllerNyPeriode.left()
    }

    @Test
    fun `ny åpen - førstegang`() {
        listOf<Søknadsbehandling>().hentSøknadstypeFor(UUID.randomUUID()) shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `iverksatt avslag og ny åpen - førstegang`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
        ).hentSøknadstypeFor(UUID.randomUUID()) shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `eksisterende åpen samme som current - førstegang`() {
        val underBehandling = søknadsbehandlingSimulert().second
        listOf<Søknadsbehandling>(
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `eksisterende åpen er samme som parameter - førstegang`() {
        søknadsbehandlingSimulert().second.let {
            listOf<Søknadsbehandling>(it).hentSøknadstypeFor(it.id)
        } shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `eksisterende innvilget samme som parameter - førstegang`() {
        val underBehandling = søknadsbehandlingIverksattInnvilget().second
        listOf<Søknadsbehandling>(
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `eksisterende innvilget og eksisterende innvilget samme som parameter - førstegang`() {
        val underBehandling = søknadsbehandlingIverksattInnvilget().second
        listOf<Søknadsbehandling>(
            søknadsbehandlingIverksattInnvilget().second,
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.NY_PERIODE.right()
    }

    @Test
    fun `eksisterende åpen og eksisterende innvilget samme som parameter - førstegang`() {
        val underBehandling = søknadsbehandlingIverksattInnvilget().second
        listOf(
            søknadsbehandlingSimulert().second,
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `åpen behandling og senere eksisterende innvilget og eksisterende innvilget samme som parameter - førstegang`() {
        val underBehandling = søknadsbehandlingIverksattInnvilget().second
        listOf(
            søknadsbehandlingSimulert().second,
            underBehandling,
            søknadsbehandlingIverksattInnvilget().second,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `eksisterende innvilget og senere eksisterende innvilget samme som parameter - ny periode`() {
        val underBehandling = søknadsbehandlingIverksattInnvilget().second
        listOf(
            søknadsbehandlingIverksattInnvilget().second,
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.NY_PERIODE.right()
    }

    @Test
    fun `iverksatt avslag og innvilget og ny åpen - ny periode`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingIverksattInnvilget().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
            søknadsbehandlingIverksattAvslagMedBeregning().second,
        ).hentSøknadstypeFor(UUID.randomUUID()) shouldBe Søknadstype.NY_PERIODE.right()
    }

    @Test
    fun `innvilget og eksisterende åpen samme som current - ny periode`() {
        val underBehandling = søknadsbehandlingSimulert().second
        listOf(
            søknadsbehandlingIverksattInnvilget().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            underBehandling,
        ).hentSøknadstypeFor(underBehandling.id) shouldBe Søknadstype.NY_PERIODE.right()
    }

    @Test
    fun `eksisterende åpen - exception`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingSimulert().second,
        ).hentSøknadstypeUtenBehandling() shouldBe KunneIkkeAvgjøreOmFørstegangEllerNyPeriode.left()
    }

    @Test
    fun `flere eksisterende åpen - exception`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingSimulert().second,
            søknadsbehandlingSimulert().second,
        ).hentSøknadstypeUtenBehandling() shouldBe KunneIkkeAvgjøreOmFørstegangEllerNyPeriode.left()
    }

    @Test
    fun `avslag - førstegangssøknad`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
        ).hentSøknadstypeUtenBehandling() shouldBe Søknadstype.FØRSTEGANGSSØKNAD.right()
    }

    @Test
    fun `innvilget - ny periode`() {
        listOf<Søknadsbehandling>(
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
            søknadsbehandlingIverksattInnvilget().second,
        ).hentSøknadstypeUtenBehandling() shouldBe Søknadstype.NY_PERIODE.right()
    }
}
