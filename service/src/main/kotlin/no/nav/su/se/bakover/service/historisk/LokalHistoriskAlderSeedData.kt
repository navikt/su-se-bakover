package no.nav.su.se.bakover.service.historisk

import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersberegning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersvedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBeløp
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBeslutning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskDato
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKlassifiseringsnivå
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløp
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOpphør
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOpphørsgrunn
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskPeriode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSaksreferanse
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSakstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsklassifisering
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSuDetalj
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Syntetiske testdata for lokal utvikling. Personidenter, saksreferanser, oppdrags-ID-er,
 * vedtaks-ID-er og saksbehandleridenter tilhører ikke faktiske personer eller saker.
 */
internal object LokalHistoriskAlderSeedData {
    val stønader: List<HistoriskAldersstønad> = listOf(
        HistoriskAldersstønad(
            stønadId = HistoriskStønadId(2364368L),
            personLøpenummer = "3054",
            personident = "23063732958",
            startdato = dato("2012-04-01"),
            oppdragId = "TEST-OPPDRAG-1",
            opphør = null,
            vedtak = listOf(
                vedtak(
                    vedtakId = 3937536L,
                    stønadId = 2364368L,
                    sakstypeRaw = "S",
                    sakstype = HistoriskSakstype.SØKNAD,
                    resultatRaw = "DI",
                    resultat = HistoriskResultat.DELVIS_INNVILGET,
                    fraOgMed = "2012-04-01",
                    tilOgMed = "2012-06-30",
                    registrertTidspunkt = "2012-02-23T12:53:45.019242",
                    registrertAv = "ISA0389",
                    bosituasjonRaw = "EU",
                    bosituasjon = HistoriskBosituasjon.EPS_UNDER_67,
                    årligYtelsesbeløp = 197052L,
                    revurderingsdato = null,
                    endringskoder = emptyList(),
                    saksreferanse = saksreferanse("0301", "A", "1001", "0389"),
                    sendtTilOs = "2012-02-23T13:00:00",
                    mottattFraOs = "2012-02-23T13:00:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "1",
                        fraOgMed = "2012-04-01",
                        tilOgMed = "2012-06-30",
                        sats = 16421L,
                        fradrag = 12315L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
                vedtak(
                    vedtakId = 4137818L,
                    stønadId = 2364368L,
                    sakstypeRaw = "GO",
                    sakstype = HistoriskSakstype.MANUELL_G_REGULERING,
                    resultatRaw = "FI",
                    resultat = HistoriskResultat.FORTSATT_INNVILGET,
                    fraOgMed = "2012-05-01",
                    tilOgMed = "2013-01-31",
                    registrertTidspunkt = "2012-07-03T17:58:01.586964",
                    registrertAv = "IRB0389",
                    bosituasjonRaw = "EU",
                    bosituasjon = HistoriskBosituasjon.EPS_UNDER_67,
                    årligYtelsesbeløp = 203268L,
                    revurderingsdato = null,
                    endringskoder = listOf("G"),
                    saksreferanse = saksreferanse("0301", "A", "1001", "0389"),
                    sendtTilOs = "2012-07-03T18:00:00",
                    mottattFraOs = "2012-07-03T18:00:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "2",
                        fraOgMed = "2012-05-01",
                        tilOgMed = "2013-01-31",
                        sats = 16939L,
                        fradrag = 12704L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
                vedtak(
                    vedtakId = 4655362L,
                    stønadId = 2364368L,
                    sakstypeRaw = "R",
                    sakstype = HistoriskSakstype.REVURDERING,
                    resultatRaw = "FI",
                    resultat = HistoriskResultat.FORTSATT_INNVILGET,
                    fraOgMed = "2013-02-01",
                    tilOgMed = "2013-03-31",
                    registrertTidspunkt = "2013-01-09T10:24:12.624437",
                    registrertAv = "IRB0389",
                    bosituasjonRaw = "EU",
                    bosituasjon = HistoriskBosituasjon.EPS_UNDER_67,
                    årligYtelsesbeløp = 203268L,
                    revurderingsdato = "2013-02-01",
                    endringskoder = listOf("E"),
                    saksreferanse = saksreferanse("0301", "A", "1001", "0389"),
                    sendtTilOs = "2013-01-09T10:30:00",
                    mottattFraOs = "2013-01-09T10:30:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "3",
                        fraOgMed = "2013-02-01",
                        tilOgMed = null,
                        sats = 16939L,
                        fradrag = 12704L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
            ),
        ),
        HistoriskAldersstønad(
            stønadId = HistoriskStønadId(2878225L),
            personLøpenummer = "2963",
            personident = "25123238519",
            startdato = dato("2013-12-01"),
            oppdragId = "TEST-OPPDRAG-2",
            opphør = opphør(
                kodeRaw = "AÅ",
                opphørsgrunn = HistoriskOpphørsgrunn.ANNEN_ÅRSAK,
                opphørsdato = "2014-01-31",
            ),
            vedtak = listOf(
                vedtak(
                    vedtakId = 5530869L,
                    stønadId = 2878225L,
                    sakstypeRaw = "S",
                    sakstype = HistoriskSakstype.SØKNAD,
                    resultatRaw = "DI",
                    resultat = HistoriskResultat.DELVIS_INNVILGET,
                    fraOgMed = "2013-12-01",
                    tilOgMed = "2014-11-30",
                    registrertTidspunkt = "2014-03-19T16:10:05.557536",
                    registrertAv = "KHH1189",
                    bosituasjonRaw = "EO",
                    bosituasjon = HistoriskBosituasjon.EPS_OVER_67,
                    årligYtelsesbeløp = 155376L,
                    revurderingsdato = null,
                    endringskoder = listOf("F"),
                    saksreferanse = saksreferanse("1201", "B", "2001", "1189"),
                    sendtTilOs = "2014-03-19T16:20:00",
                    mottattFraOs = "2014-03-19T16:20:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "1",
                        fraOgMed = "2013-12-01",
                        tilOgMed = null,
                        sats = 12948L,
                        fradrag = 4899L,
                        fradragskoder = listOf("FTRE", "FTRM"),
                    ),
                ),
            ),
        ),
        HistoriskAldersstønad(
            stønadId = HistoriskStønadId(2878227L),
            personLøpenummer = "2963",
            personident = "25123238519",
            startdato = dato("2014-02-01"),
            oppdragId = "TEST-OPPDRAG-3",
            opphør = opphør(
                kodeRaw = "HI",
                opphørsgrunn = HistoriskOpphørsgrunn.HØY_INNTEKT,
                opphørsdato = "2014-10-31",
            ),
            vedtak = listOf(
                vedtak(
                    vedtakId = 5530873L,
                    stønadId = 2878227L,
                    sakstypeRaw = "S",
                    sakstype = HistoriskSakstype.SØKNAD,
                    resultatRaw = "DI",
                    resultat = HistoriskResultat.DELVIS_INNVILGET,
                    fraOgMed = "2014-02-01",
                    tilOgMed = "2014-06-30",
                    registrertTidspunkt = "2014-03-19T16:14:21.017245",
                    registrertAv = "KHH1189",
                    bosituasjonRaw = "EN",
                    bosituasjon = HistoriskBosituasjon.ENSLIG,
                    årligYtelsesbeløp = 167964L,
                    revurderingsdato = null,
                    endringskoder = listOf("F"),
                    saksreferanse = saksreferanse("1201", "B", "2002", "1189"),
                    sendtTilOs = "2014-03-19T16:25:00",
                    mottattFraOs = "2014-03-19T16:25:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "1",
                        fraOgMed = "2014-02-01",
                        tilOgMed = "2014-06-30",
                        sats = 13997L,
                        fradrag = 4899L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
                vedtak(
                    vedtakId = 5892712L,
                    stønadId = 2878227L,
                    sakstypeRaw = "GO",
                    sakstype = HistoriskSakstype.MANUELL_G_REGULERING,
                    resultatRaw = "FI",
                    resultat = HistoriskResultat.FORTSATT_INNVILGET,
                    fraOgMed = "2014-05-01",
                    tilOgMed = "2014-09-30",
                    registrertTidspunkt = "2014-09-30T15:39:51.019561",
                    registrertAv = "KHA4411",
                    bosituasjonRaw = "EN",
                    bosituasjon = HistoriskBosituasjon.ENSLIG,
                    årligYtelsesbeløp = 173280L,
                    revurderingsdato = null,
                    endringskoder = listOf("G"),
                    saksreferanse = saksreferanse("1201", "B", "2002", "4411"),
                    sendtTilOs = "2014-09-30T15:45:00",
                    mottattFraOs = "2014-09-30T15:45:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "3",
                        fraOgMed = "2014-05-01",
                        tilOgMed = "2014-09-30",
                        sats = 14440L,
                        fradrag = 5054L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
                vedtak(
                    vedtakId = 5729543L,
                    stønadId = 2878227L,
                    sakstypeRaw = "R",
                    sakstype = HistoriskSakstype.REVURDERING,
                    resultatRaw = "FI",
                    resultat = HistoriskResultat.FORTSATT_INNVILGET,
                    fraOgMed = "2014-07-01",
                    tilOgMed = "2014-09-30",
                    registrertTidspunkt = "2014-07-09T10:21:41.618977",
                    registrertAv = "LKB4411",
                    bosituasjonRaw = "EN",
                    bosituasjon = HistoriskBosituasjon.ENSLIG,
                    årligYtelsesbeløp = 173280L,
                    revurderingsdato = "2014-07-01",
                    endringskoder = listOf("E"),
                    saksreferanse = saksreferanse("1201", "B", "2002", "4411"),
                    sendtTilOs = "2014-07-09T10:30:00",
                    mottattFraOs = "2014-07-09T10:30:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "2",
                        fraOgMed = "2014-07-01",
                        tilOgMed = "2014-09-30",
                        sats = 14440L,
                        fradrag = 5054L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
                vedtak(
                    vedtakId = 5892713L,
                    stønadId = 2878227L,
                    sakstypeRaw = "R",
                    sakstype = HistoriskSakstype.REVURDERING,
                    resultatRaw = "FI",
                    resultat = HistoriskResultat.FORTSATT_INNVILGET,
                    fraOgMed = "2014-10-01",
                    tilOgMed = "2015-01-31",
                    registrertTidspunkt = "2014-09-30T15:41:32.282943",
                    registrertAv = "KHA4411",
                    bosituasjonRaw = "EN",
                    bosituasjon = HistoriskBosituasjon.ENSLIG,
                    årligYtelsesbeløp = 173280L,
                    revurderingsdato = "2014-10-01",
                    endringskoder = listOf("S"),
                    saksreferanse = saksreferanse("1201", "B", "2002", "4411"),
                    sendtTilOs = "2014-09-30T15:50:00",
                    mottattFraOs = "2014-09-30T15:50:02",
                    månedsbeløp = månedsbeløp(
                        linjeId = "4",
                        fraOgMed = "2014-10-01",
                        tilOgMed = null,
                        sats = 14440L,
                        fradrag = 5054L,
                        fradragskoder = listOf("FTRM"),
                    ),
                ),
            ),
        ),
    )

    private fun vedtak(
        vedtakId: Long,
        stønadId: Long,
        sakstypeRaw: String,
        sakstype: HistoriskSakstype,
        resultatRaw: String,
        resultat: HistoriskResultat,
        fraOgMed: String,
        tilOgMed: String?,
        registrertTidspunkt: String,
        registrertAv: String,
        bosituasjonRaw: String,
        bosituasjon: HistoriskBosituasjon,
        årligYtelsesbeløp: Long,
        revurderingsdato: String?,
        endringskoder: List<String>,
        saksreferanse: HistoriskSaksreferanse,
        sendtTilOs: String,
        mottattFraOs: String,
        månedsbeløp: HistoriskMånedsbeløp,
    ): HistoriskAldersvedtak = HistoriskAldersvedtak(
        vedtakId = HistoriskVedtakId(vedtakId),
        stønadId = HistoriskStønadId(stønadId),
        sakstype = HistoriskKode(sakstypeRaw, sakstype),
        resultat = HistoriskKode(resultatRaw, resultat),
        periode = periode(fraOgMed, tilOgMed),
        mottattDato = null,
        registrertTidspunkt = registrertTidspunkt,
        registrertAv = registrertAv,
        saksreferanse = saksreferanse,
        beregningstype = null,
        nøkkelDl1 = null,
        klassifiseringer = listOf(
            HistoriskStønadsklassifisering(
                nivå = HistoriskKlassifiseringsnivå("02", "Bosituasjon"),
                kode = bosituasjonRaw,
                bosituasjon = bosituasjon,
            ),
        ),
        roller = emptyList(),
        beregning = HistoriskAldersberegning(
            suDetaljer = listOf(
                HistoriskSuDetalj(
                    årligYtelsesbeløp = beløp(årligYtelsesbeløp),
                    revurderingsdato = revurderingsdato?.let(::dato),
                    registrertTidspunkt = registrertTidspunkt,
                ),
            ),
            inntekter = emptyList(),
            delytelser = emptyList(),
            månedsbeløp = listOf(månedsbeløp),
        ),
        endringskoder = endringskoder,
        beslutninger = listOf(
            HistoriskBeslutning(
                beslutningId = "LOKAL-$vedtakId",
                førsteSaksbehandler = registrertAv,
                førsteGodkjenning = "J",
                førsteRegistreringstidspunkt = registrertTidspunkt,
                andreSaksbehandler = null,
                andreGodkjenning = null,
                andreRegistreringstidspunkt = null,
                sendtTilOs = sendtTilOs,
                mottattFraOs = mottattFraOs,
                godkjentAvOs = "J",
            ),
        ),
    )

    private fun månedsbeløp(
        linjeId: String,
        fraOgMed: String,
        tilOgMed: String?,
        sats: Long,
        fradrag: Long,
        fradragskoder: List<String>,
    ) = HistoriskMånedsbeløp(
        periode = periode(fraOgMed, tilOgMed),
        sats = BigDecimal.valueOf(sats),
        fradrag = BigDecimal.valueOf(fradrag),
        linjeId = linjeId,
        fradragskoder = fradragskoder,
    )

    private fun opphør(
        kodeRaw: String,
        opphørsgrunn: HistoriskOpphørsgrunn,
        opphørsdato: String,
    ) = HistoriskOpphør(
        kode = HistoriskKode(kodeRaw, opphørsgrunn),
        dato = dato(opphørsdato),
        registrertTidspunkt = "${opphørsdato}T12:00:00",
    )

    private fun saksreferanse(
        kontornummer: String,
        saksblokk: String,
        saksnummer: String,
        behandlendeKontor: String,
    ) = HistoriskSaksreferanse(
        kontornummer = kontornummer,
        saksblokk = saksblokk,
        saksnummer = saksnummer,
        behandlendeKontor = behandlendeKontor,
    )

    private fun periode(
        fraOgMed: String,
        tilOgMed: String?,
    ) = HistoriskPeriode(
        fraOgMed = dato(fraOgMed),
        tilOgMed = tilOgMed?.let(::dato),
    )

    private fun dato(verdi: String) = HistoriskDato(
        råverdi = verdi,
        dato = LocalDate.parse(verdi),
    )

    private fun beløp(verdi: Long) = HistoriskBeløp(
        råverdi = verdi.toString(),
        beløp = BigDecimal.valueOf(verdi),
    )
}
