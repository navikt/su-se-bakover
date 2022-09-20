package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.sak.hent.hentSaksnummer
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtaleKomponentTest {

    @Test
    fun `oppretter kontrollsamtale, kall inn og annuller`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()
        val andreInnkalling = stønadStart.plusMonths(8).startOfMonth()
        val andreFrist = stønadStart.plusMonths(8).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale

            val (sakId, _) = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
            ).let {
                hentSak(BehandlingJson.hentSakId(it)).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson)) to Saksnummer(hentSaksnummer(sakJson).toLong())
                }
            }

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId)
                .let { kontrollsamtaler ->
                    kontrollsamtaler.single().also {
                        it.innkallingsdato shouldBe førsteInnkalling
                        it.frist shouldBe førsteFrist
                        it.dokumentId shouldBe beNull()
                        it.status shouldBe Kontrollsamtalestatus.PLANLAGT_INNKALLING
                        it.sakId shouldBe sakId
                    }
                }

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale
            )

            val andrePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId)
                .let { kontrollsamtaler ->
                    kontrollsamtaler.first().also {
                        it.innkallingsdato shouldBe førsteInnkalling
                        it.frist shouldBe førsteFrist
                        it.dokumentId shouldNot beNull()
                        it.status shouldBe Kontrollsamtalestatus.INNKALT
                        it.sakId shouldBe sakId
                    }
                    // ny samtale planlegges ved innkalling av forrige
                    kontrollsamtaler.last().also {
                        it.innkallingsdato shouldBe andreInnkalling
                        it.frist shouldBe andreFrist
                        it.dokumentId shouldBe beNull()
                        it.status shouldBe Kontrollsamtalestatus.PLANLAGT_INNKALLING
                        it.sakId shouldBe sakId
                    }
                }

            tikkendeKlokke.spolTil(andrePlanlagteKontrollsamtale.innkallingsdato)

            opprettIverksattRevurdering(
                sakId = sakId.toString(),
                fraOgMed = andreInnkalling.toString(),
                tilOgMed = stønadSlutt.toString(),
                leggTilUtenlandsopphold = { revurderingId ->
                    leggTilUtenlandsoppholdRevurdering(
                        sakId = sakId.toString(),
                        behandlingId = revurderingId,
                        fraOgMed = andreInnkalling.toString(),
                        tilOgMed = stønadSlutt.toString(),
                        vurdering = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet.toString(),
                    )
                }
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

    @Test
    fun `oppdaterer kontrollsamtale med journalpost for innsendt kontrollnotat hvis funnet`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale
            val utløptFristForKontrollsamtaleService = appComponents.services.utløptFristForKontrollsamtaleService

            val (sakId, _) = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
            ).let {
                hentSak(BehandlingJson.hentSakId(it)).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson)) to Saksnummer(hentSaksnummer(sakJson).toLong())
                }
            }

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId)
                .let { kontrollsamtaler ->
                    kontrollsamtaler.single().also {
                        it.innkallingsdato shouldBe førsteInnkalling
                        it.frist shouldBe førsteFrist
                        it.dokumentId shouldBe beNull()
                        it.status shouldBe Kontrollsamtalestatus.PLANLAGT_INNKALLING
                        it.sakId shouldBe sakId
                    }
                }

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale
            )

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.frist)

            utløptFristForKontrollsamtaleService.håndterKontrollsamtalerMedFristUtløpt(førstePlanlagteKontrollsamtale.frist)

            kontrollsamtaleService.hentForSak(sakId = sakId)
                .let { kontrollsamtaler ->
                    kontrollsamtaler.find { it.id == førstePlanlagteKontrollsamtale.id }!!
                        .also {
                            it.innkallingsdato shouldBe førsteInnkalling
                            it.frist shouldBe førsteFrist
                            it.dokumentId shouldNot beNull()
                            it.status shouldBe Kontrollsamtalestatus.GJENNOMFØRT
                            it.sakId shouldBe sakId
                        }
                }
        }
    }
}
