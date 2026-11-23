package no.nav.su.se.bakover.domain.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.ForNav
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFailAsType
import no.nav.su.se.bakover.test.sakInfo
import no.nav.su.se.bakover.test.søknad.journalpostIdSøknad
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadTest {

    @Test
    fun `mottatt tidspunkt for digital søknad er opprettet`() {
        val opprettet = fixedTidspunkt
        val søknad = Søknad.Ny(
            sakId = UUID.randomUUID(),
            id = UUID.randomUUID(),
            opprettet = opprettet,
            søknadInnhold = søknadinnholdUføre(
                forNav = ForNav.DigitalSøknad(),
            ),
            innsendtAv = veileder,
        )
        søknad.mottaksdato shouldBe fixedLocalDate
    }

    @Test
    fun `mottatt tidspunkt for papirsøknad er opprettet`() {
        val dato = fixedLocalDate
        val søknad = Søknad.Ny(
            sakId = UUID.randomUUID(),
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            søknadInnhold = søknadinnholdUføre(
                forNav = ForNav.Papirsøknad(
                    mottaksdatoForSøknad = dato,
                    grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.Annet,
                    annenGrunn = null,
                ),
            ),
            innsendtAv = veileder,
        )
        søknad.mottaksdato shouldBe dato
    }

    @Test
    fun `brev for trukket papirsøknad bruker mottatt dato, ikke registreringsdato`() {
        val mottattDato = fixedLocalDate.minusDays(12)

        val command = trukketSøknadDokumentCommand(
            forNav = ForNav.Papirsøknad(
                mottaksdatoForSøknad = mottattDato,
                grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.Annet,
                annenGrunn = null,
            ),
        )

        command.søknadMottatt shouldBe mottattDato
    }

    @Test
    fun `brev for trukket digital søknad bruker datoen søknaden ble opprettet`() {
        val command = trukketSøknadDokumentCommand(
            forNav = ForNav.DigitalSøknad(),
        )

        command.søknadMottatt shouldBe fixedLocalDate
    }

    /**
     * Registrerer søknaden på [fixedTidspunkt], trekker den og lager brevbestillingen.
     */
    private fun trukketSøknadDokumentCommand(
        forNav: ForNav,
    ): TrukketSøknadDokumentCommand {
        val søknad = Søknad.Ny(
            sakId = UUID.randomUUID(),
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            søknadInnhold = søknadinnholdUføre(forNav = forNav),
            innsendtAv = veileder,
        )
        return søknad
            .journalfør(journalpostIdSøknad)
            .medOppgave(oppgaveIdSøknad)
            .lukk(trekkSøknad(søknadId = søknad.id))
            .lagGenererDokumentKommando(sakInfo())
            .getOrFailAsType<TrukketSøknadDokumentCommand>()
    }
}
