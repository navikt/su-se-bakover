package no.nav.su.se.bakover.datapakker.stoenadstatistikk

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource
import kotlin.use

private fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): List<T> {
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}

fun hentData(dataSource: DataSource, måned: YearMonth): List<StønadstatistikkMånedDto> {
    return dataSource.connection.use {
        val session = sessionOf(dataSource)
        """
        SELECT *
        FROM stoenad_maaned_statistikk
        WHERE maaned = :maaned
        """.trimIndent()
            .hentListe(
                params = mapOf("maaned" to måned.atDay(1)),
                session = session,
            ) { row ->
                with(row) {
                    val id = uuid("id")
                    StønadstatistikkMånedDto(
                        id = id,
                        måned = måned,
                        funksjonellTid = string("funksjonell_tid"),
                        tekniskTid = string("teknisk_tid"),
                        sakId = UUID.fromString(string("sak_id")),
                        stonadstype = string("stonadstype"),
                        vedtaksdato = localDate("vedtaksdato"),
                        personnummer = string("personnummer"),
                        personNummerEps = stringOrNull("personnummer_eps"),
                        vedtakFraOgMed = localDate("vedtak_fra_og_med"),
                        vedtakTilOgMed = localDate("vedtak_til_og_med"),
                        vedtakstype = string("vedtakstype"),
                        vedtaksresultat = string("vedtaksresultat"),
                        opphorsgrunn = stringOrNull("opphorsgrunn"),
                        opphorsdato = localDateOrNull("opphorsdato"),
                        behandlendeEnhetKode = string("behandlende_enhet_kode"),
                        harUtenlandsOpphold = stringOrNull("har_utenlandsopphold"),
                        harFamiliegjenforening = stringOrNull("har_familiegjenforening"),
                        flyktningsstatus = stringOrNull("flyktningsstatus"),
                        stonadsklassifisering = stringOrNull("stonadsklassifisering"),
                        sats = longOrNull("sats"),
                        utbetales = longOrNull("utbetales"),
                        fradragSum = longOrNull("fradragSum"),
                        uføregrad = intOrNull("uforegrad"),
                        alderspensjon = intOrNull("alderspensjon"),
                        alderspensjonEps = intOrNull("alderspensjonEps"),
                        arbeidsavklaringspenger = intOrNull("arbeidsavklaringspenger"),
                        arbeidsavklaringspengerEps = intOrNull("arbeidsavklaringspengerEps"),
                        arbeidsinntekt = intOrNull("arbeidsinntekt"),
                        arbeidsinntektEps = intOrNull("arbeidsinntektEps"),
                        omstillingsstønad = intOrNull("omstillingsstonad"),
                        omstillingsstønadEps = intOrNull("omstillingsstonadEps"),
                        avtalefestetPensjon = intOrNull("avtalefestetPensjon"),
                        avtalefestetPensjonEps = intOrNull("avtalefestetPensjonEps"),
                        avtalefestetPensjonPrivat = intOrNull("avtalefestetPensjonPrivat"),
                        avtalefestetPensjonPrivatEps = intOrNull("avtalefestetPensjonPrivatEps"),
                        bidragEtterEkteskapsloven = intOrNull("bidragEtterEkteskapsloven"),
                        bidragEtterEkteskapslovenEps = intOrNull("bidragEtterEkteskapslovenEps"),
                        dagpenger = intOrNull("dagpenger"),
                        dagpengerEps = intOrNull("dagpengerEps"),
                        fosterhjemsgodtgjørelse = intOrNull("fosterhjemsgodtgjorelse"),
                        fosterhjemsgodtgjørelseEps = intOrNull("fosterhjemsgodtgjorelseEps"),
                        gjenlevendepensjon = intOrNull("gjenlevendepensjon"),
                        gjenlevendepensjonEps = intOrNull("gjenlevendepensjonEps"),
                        introduksjonsstønad = intOrNull("introduksjonsstonad"),
                        introduksjonsstønadEps = intOrNull("introduksjonsstonadEps"),
                        kapitalinntekt = intOrNull("kapitalinntekt"),
                        kapitalinntektEps = intOrNull("kapitalinntektEps"),
                        kontantstøtte = intOrNull("kontantstotte"),
                        kontantstøtteEps = intOrNull("kontantstotteEps"),
                        kvalifiseringsstønad = intOrNull("kvalifiseringsstonad"),
                        kvalifiseringsstønadEps = intOrNull("kvalifiseringsstonadEps"),
                        navYtelserTilLivsopphold = intOrNull("navYtelserTilLivsopphold"),
                        navYtelserTilLivsoppholdEps = intOrNull("navYtelserTilLivsoppholdEps"),
                        offentligPensjon = intOrNull("offentligPensjon"),
                        offentligPensjonEps = intOrNull("offentligPensjonEps"),
                        privatPensjon = intOrNull("privatPensjon"),
                        privatPensjonEps = intOrNull("privatPensjonEps"),
                        sosialstønad = intOrNull("sosialstonad"),
                        sosialstønadEps = intOrNull("sosialstonadEps"),
                        statensLånekasse = intOrNull("statensLaanekasse"),
                        statensLånekasseEps = intOrNull("statensLaanekasseEps"),
                        supplerendeStønad = intOrNull("supplerendeStonad"),
                        supplerendeStønadEps = intOrNull("supplerendeStonadEps"),
                        sykepenger = intOrNull("sykepenger"),
                        sykepengerEps = intOrNull("sykepengerEps"),
                        tiltakspenger = intOrNull("tiltakspenger"),
                        tiltakspengerEps = intOrNull("tiltakspengerEps"),
                        ventestønad = intOrNull("ventestonad"),
                        ventestønadEps = intOrNull("ventestonadEps"),
                        uføretrygd = intOrNull("uforetrygd"),
                        uføretrygdEps = intOrNull("uforetrygdEps"),
                        forventetInntekt = intOrNull("forventetInntekt"),
                        forventetInntektEps = intOrNull("forventetInntektEps"),
                        avkortingUtenlandsopphold = intOrNull("avkortingUtenlandsopphold"),
                        avkortingUtenlandsoppholdEps = intOrNull("avkortingUtenlandsoppholdEps"),
                        underMinstenivå = intOrNull("underMinstenivaa"),
                        underMinstenivåEps = intOrNull("underMinstenivaaEps"),
                        annet = intOrNull("annet"),
                        annetEps = intOrNull("annetEps"),
                    )
                }
            }
    }
}
