package no.nav.su.se.bakover.web.komponenttest.kontrollsamtale

import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.withKomptestApplication
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class OppretterKontrollsamtaleKallerInnOgAnnullererTest {

    @Test
    fun `oppretter kontrollsamtale, kall inn og annuller`() {
        // TODO jah: Denne vil feile dersom førsteFrist eller andreFrist havner i november, fordi da er det 25. og 30. (siste) som gjelder. På grunn av utbetalingskjøringer før jul.
        // TODO jah: Dersom man legger inn en sats som er innenfor periode 2022-juni til 2023-mai vil testen feil. Da kan man endre testStartTidspunkt til neste knekkpunkt.
        val testStartTidspunkt = 20.mai(2022)
        val tikkendeKlokke = TikkendeKlokke(testStartTidspunkt.fixedClock())
        val stønadStart = testStartTidspunkt.førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()
        val andreInnkalling = stønadStart.plusMonths(8).startOfMonth()
        val andreFrist = stønadStart.plusMonths(8).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val kontrollsamtaleService =
                appComponents.services.kontrollsamtaleSetup.kontrollsamtaleService as KontrollsamtaleServiceImpl

            val sakId = innvilgSøknad(
                fraOgMed = stønadStart,
                tilOgMed = stønadSlutt,
                client = this.client,
                appComponents = appComponents,
            )

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).first()

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale,
            )

            val andrePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).last()

            tikkendeKlokke.spolTil(andrePlanlagteKontrollsamtale.innkallingsdato)

            opprettIverksattRevurdering(
                sakid = sakId.toString(),
                fraogmed = andreInnkalling.toString(),
                tilogmed = stønadSlutt.toString(),
                leggTilUtenlandsoppholdRevurdering = { sakid, behandlingId, fraOgMed, tilOgMed, _ ->
                    leggTilUtenlandsoppholdRevurdering(
                        sakId = sakid,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        vurdering = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet.toString(),
                        client = this.client,
                    )
                },
                client = this.client,
                appComponents = appComponents,
            )

            kontrollsamtaleService.hentForSak(sakId)
                .also { kontrollsamtaler ->
                    kontrollsamtaler.first().also {
                        it.innkallingsdato shouldBe førsteInnkalling
                        it.frist shouldBe førsteFrist
                        it.dokumentId shouldNot beNull()
                        it.status shouldBe Kontrollsamtalestatus.INNKALT
                        it.sakId shouldBe sakId
                    }
                    kontrollsamtaler.last().also {
                        it.innkallingsdato shouldBe andreInnkalling
                        it.frist shouldBe andreFrist
                        it.dokumentId shouldBe beNull()
                        it.status shouldBe Kontrollsamtalestatus.ANNULLERT
                        it.sakId shouldBe sakId
                    }
                }
        }
    }

    private fun innvilgSøknad(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        client: HttpClient,
        appComponents: AppComponents,
    ): UUID {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = fraOgMed.toString(),
            tilOgMed = tilOgMed.toString(),
            client = client,
            appComponents = appComponents,
        ).let {
            hentSak(BehandlingJson.hentSakId(it), client = client).let { sakJson ->
                UUID.fromString(hentSakId(sakJson))
            }
        }
    }
}
