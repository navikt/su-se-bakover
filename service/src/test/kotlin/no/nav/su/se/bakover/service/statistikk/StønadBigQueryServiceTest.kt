package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import org.junit.jupiter.api.Test
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal class StønadBigQueryServiceTest {

    /**
     * Antall kolonner i CSV-en MÅ matche antall kolonner i BigQuery-skjemaet for tabellen
     * `stoenadstatistikk`. Hvis du legger til en ny kolonne i [toCSV], må du også:
     *  1) Legge den til i BigQuery-skjemaet (append-only, sist).
     *  2) Oppdatere kolonnelista i kommentaren over [toCSV].
     *  3) Bumpe forventet antall under.
     *
     * Denne testen finnes for å sikre at vi sender over alle felter i StønadstatistikkMåned siden vi har skrudd på
     * `setAllowJaggedRows(true)` i BigQuery-loaderen uten testen blitt silent NULL i BQ.
     */
    @Test
    fun `CSV har eksakt forventet antall kolonner per rad`() {
        val csv = listOf(minimalDto()).toCSV()
        val rader = csv.lines().filter { it.isNotEmpty() }
        rader.forEach { rad ->
            rad.split(",").size shouldBe ANTALL_KOLONNER
        }
    }

    private companion object {
        private const val ANTALL_KOLONNER = 79

        private fun minimalDto(): StønadstatistikkMåned = StønadstatistikkMåned(
            id = UUID.randomUUID(),
            måned = YearMonth.of(2025, 1),
            funksjonellTid = fixedTidspunkt,
            tekniskTid = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            stonadstype = StønadstatistikkDto.Stønadstype.SU_UFØR,
            personnummer = fnr,
            personNummerEps = null,
            vedtaksdato = LocalDate.of(2025, 1, 1),
            vedtakstype = StønadstatistikkDto.Vedtakstype.SØKNAD,
            vedtaksresultat = StønadstatistikkDto.Vedtaksresultat.INNVILGET,
            vedtakFraOgMed = LocalDate.of(2025, 1, 1),
            vedtakTilOgMed = LocalDate.of(2025, 12, 31),
            opphorsgrunn = null,
            opphorsdato = null,
            årsakStans = null,
            behandlendeEnhetKode = "4815",
            stonadsklassifisering = null,
            sats = null,
            utbetales = null,
            fradragSum = null,
            uføregrad = null,
            fribeløpEps = null,
            alderspensjon = null,
            alderspensjonEps = null,
            arbeidsavklaringspenger = null,
            arbeidsavklaringspengerEps = null,
            arbeidsinntekt = null,
            arbeidsinntektEps = null,
            omstillingsstønad = null,
            omstillingsstønadEps = null,
            avtalefestetPensjon = null,
            avtalefestetPensjonEps = null,
            avtalefestetPensjonPrivat = null,
            avtalefestetPensjonPrivatEps = null,
            bidragEtterEkteskapsloven = null,
            bidragEtterEkteskapslovenEps = null,
            dagpenger = null,
            dagpengerEps = null,
            fosterhjemsgodtgjørelse = null,
            fosterhjemsgodtgjørelseEps = null,
            gjenlevendepensjon = null,
            gjenlevendepensjonEps = null,
            introduksjonsstønad = null,
            introduksjonsstønadEps = null,
            kapitalinntekt = null,
            kapitalinntektEps = null,
            kontantstøtte = null,
            kontantstøtteEps = null,
            kvalifiseringsstønad = null,
            kvalifiseringsstønadEps = null,
            navYtelserTilLivsopphold = null,
            navYtelserTilLivsoppholdEps = null,
            offentligPensjon = null,
            offentligPensjonEps = null,
            privatPensjon = null,
            privatPensjonEps = null,
            sosialstønad = null,
            sosialstønadEps = null,
            statensLånekasse = null,
            statensLånekasseEps = null,
            supplerendeStønad = null,
            supplerendeStønadEps = null,
            sykepenger = null,
            sykepengerEps = null,
            tiltakspenger = null,
            tiltakspengerEps = null,
            ventestønad = null,
            ventestønadEps = null,
            uføretrygd = null,
            uføretrygdEps = null,
            forventetInntekt = null,
            forventetInntektEps = null,
            avkortingUtenlandsopphold = null,
            avkortingUtenlandsoppholdEps = null,
            underMinstenivå = null,
            underMinstenivåEps = null,
            annet = null,
            annetEps = null,
        )
    }
}
