package no.nav.su.se.bakover.test.kravgrunnlag

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.genererKravgrunnlagFraSimulering
import no.nav.su.se.bakover.test.getFileSourceContent
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vedtakRevurdering
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * @param clock Ignoreres dersom [hendelsestidspunkt] er satt.
 */
fun råttKravgrunnlagHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    clock: Clock = fixedClock,
    hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
    råttKravgrunnlag: RåttKravgrunnlag = råttKravgrunnlagTomt(),
): RåttKravgrunnlagHendelse {
    return RåttKravgrunnlagHendelse(
        hendelseId = hendelseId,
        hendelsestidspunkt = hendelsestidspunkt,
        råttKravgrunnlag = råttKravgrunnlag,
    )
}

/**
 * Denne kan ikke parses
 */
fun råttKravgrunnlagTomt(
    xml: String = "<xml></xml>",
): RåttKravgrunnlag {
    return RåttKravgrunnlag(xml)
}

/**
 * Kan parses, men henger nødvendigvis sammen med resten av testdataene
 */
fun råttKravgrunnlagMedData(
    kravgrunnlagXml: String = "<detaljertKravgrunnlagMelding><detaljertKravgrunnlag><kravgrunnlagId>123456</kravgrunnlagId><vedtakId>654321</vedtakId><kodeStatusKrav>NY</kodeStatusKrav><kodeFagomraade>SUUFORE</kodeFagomraade><fagsystemId>10002099</fagsystemId><datoVedtakFagsystem/><vedtakIdOmgjort/><vedtakGjelderId>18506438140</vedtakGjelderId><typeGjelderId>PERSON</typeGjelderId><utbetalesTilId>18506438140</utbetalesTilId><typeUtbetId>PERSON</typeUtbetId><kodeHjemmel>ANNET</kodeHjemmel><renterBeregnes>N</renterBeregnes><enhetAnsvarlig>4815</enhetAnsvarlig><enhetBosted>8020</enhetBosted><enhetBehandl>4815</enhetBehandl><kontrollfelt>2023-09-19-10.01.03.842916</kontrollfelt><saksbehId>K231B433</saksbehId><referanse>ef8ac92a-ba30-414e-85f4-d1227c</referanse><tilbakekrevingsPeriode><periode><fom>2023-06-01</fom><tom>2023-06-30</tom></periode><belopSkattMnd>4395</belopSkattMnd><tilbakekrevingsBelop><kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse><typeKlasse>FEIL</typeKlasse><belopOpprUtbet>0</belopOpprUtbet><belopNy>2643</belopNy><belopTilbakekreves>0</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>0</skattProsent></tilbakekrevingsBelop><tilbakekrevingsBelop><kodeKlasse>SUUFORE</kodeKlasse><typeKlasse>YTEL</typeKlasse><belopOpprUtbet>16181</belopOpprUtbet><belopNy>13538</belopNy><belopTilbakekreves>2643</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>43.9983</skattProsent></tilbakekrevingsBelop></tilbakekrevingsPeriode><tilbakekrevingsPeriode><periode><fom>2023-07-01</fom><tom>2023-07-31</tom></periode><belopSkattMnd>4395</belopSkattMnd><tilbakekrevingsBelop><kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse><typeKlasse>FEIL</typeKlasse><belopOpprUtbet>0</belopOpprUtbet><belopNy>2643</belopNy><belopTilbakekreves>0</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>0</skattProsent></tilbakekrevingsBelop><tilbakekrevingsBelop><kodeKlasse>SUUFORE</kodeKlasse><typeKlasse>YTEL</typeKlasse><belopOpprUtbet>16181</belopOpprUtbet><belopNy>13538</belopNy><belopTilbakekreves>2643</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>43.9983</skattProsent></tilbakekrevingsBelop></tilbakekrevingsPeriode><tilbakekrevingsPeriode><periode><fom>2023-08-01</fom><tom>2023-08-31</tom></periode><belopSkattMnd>4395</belopSkattMnd><tilbakekrevingsBelop><kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse><typeKlasse>FEIL</typeKlasse><belopOpprUtbet>0</belopOpprUtbet><belopNy>2643</belopNy><belopTilbakekreves>0</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>0</skattProsent></tilbakekrevingsBelop><tilbakekrevingsBelop><kodeKlasse>SUUFORE</kodeKlasse><typeKlasse>YTEL</typeKlasse><belopOpprUtbet>16181</belopOpprUtbet><belopNy>13538</belopNy><belopTilbakekreves>2643</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>43.9983</skattProsent></tilbakekrevingsBelop></tilbakekrevingsPeriode></detaljertKravgrunnlag></detaljertKravgrunnlagMelding>",
): RåttKravgrunnlag = RåttKravgrunnlag(kravgrunnlagXml)

fun kravgrunnlag(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    kravgrunnlagId: String = "123-456",
    vedtakId: String = "789-101",
    eksternTidspunkt: Tidspunkt = fixedTidspunkt,
    status: Kravgrunnlagstatus = Kravgrunnlagstatus.Manuell,
    behandler: String = saksbehandler.toString(),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    grunnlagsperioder: Nel<Kravgrunnlag.Grunnlagsperiode> = nonEmptyListOf(
        grunnlagsperiode(),
    ),
    kravgrunnlagPåSakHendelseId: HendelseId = HendelseId.generer(),
): Kravgrunnlag {
    return Kravgrunnlag(
        saksnummer = saksnummer,
        eksternKravgrunnlagId = kravgrunnlagId,
        eksternVedtakId = vedtakId,
        eksternKontrollfelt = eksternTidspunkt.toOppdragTimestamp(),
        status = status,
        behandler = behandler,
        utbetalingId = utbetalingId,
        grunnlagsperioder = grunnlagsperioder,
        eksternTidspunkt = eksternTidspunkt,
        hendelseId = kravgrunnlagPåSakHendelseId,
    )
}

/**
 * @param clock Ignoreres dersom [hendelsestidspunkt] er satt.
 */
fun kravgrunnlagPåSakHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    versjon: Hendelsesversjon = Hendelsesversjon.ny(),
    sakId: UUID = UUID.randomUUID(),
    clock: Clock = fixedClock,
    hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
    tidligereHendelseId: HendelseId = HendelseId.generer(),
    eksternKravgrunnlagId: String = UUID.randomUUID().toString(),
    eksternVedtakId: String = UUID.randomUUID().toString(),
    eksternTidspunkt: Tidspunkt = Tidspunkt.now(clock),
    status: Kravgrunnlagstatus = Kravgrunnlagstatus.Nytt,
    behandler: String = attestant.navIdent,
    utbetalingId: UUID30 = UUID30.randomUUID(),
    grunnlagsmåneder: Nel<Kravgrunnlag.Grunnlagsperiode> = nonEmptyListOf(
        grunnlagsperiode(),
    ),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    kravgrunnlag: Kravgrunnlag = Kravgrunnlag(
        eksternKravgrunnlagId = eksternKravgrunnlagId,
        eksternVedtakId = eksternVedtakId,
        eksternKontrollfelt = eksternTidspunkt.toOppdragTimestamp(),
        status = status,
        behandler = behandler,
        utbetalingId = utbetalingId,
        grunnlagsperioder = grunnlagsmåneder,
        saksnummer = saksnummer,
        eksternTidspunkt = eksternTidspunkt,
        hendelseId = hendelseId,
    ),
    revurderingId: UUID? = null,
): KravgrunnlagDetaljerPåSakHendelse {
    return KravgrunnlagDetaljerPåSakHendelse(
        hendelseId = hendelseId,
        versjon = versjon,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        tidligereHendelseId = tidligereHendelseId,
        kravgrunnlag = kravgrunnlag,
        revurderingId = revurderingId,
    )
}

fun grunnlagsperiode(
    periode: Måned = januar(2021),
    betaltSkattForYtelsesgruppen: Int = 1000,
    bruttoTidligereUtbetalt: Int = 2000,
    bruttoNyUtbetaling: Int = 1000,
    bruttoFeilutbetaling: Int = 1000,
    skatteProsent: BigDecimal = BigDecimal("50.0000"),
): Kravgrunnlag.Grunnlagsperiode {
    return Kravgrunnlag.Grunnlagsperiode(
        periode = periode,
        betaltSkattForYtelsesgruppen = betaltSkattForYtelsesgruppen,
        bruttoTidligereUtbetalt = bruttoTidligereUtbetalt,
        bruttoNyUtbetaling = bruttoNyUtbetaling,
        bruttoFeilutbetaling = bruttoFeilutbetaling,
        skatteProsent = skatteProsent,
    )
}

/**
 * Her er det viktig at det er sammenheng mellom perioden og clock. Dvs. clock må være etter første måned for å få en feilutbetaling+kravgrunnlag
 */
fun sakMedUteståendeKravgrunnlag(
    clock: Clock = fixedClockAt(1.februar(2021)),
    periode: Periode = år(2021),
): Sak {
    return vedtakRevurdering(
        clock = clock,
        stønadsperiode = Stønadsperiode.create(periode),
        revurderingsperiode = periode,
    ).let { (sak, vedtak) ->
        val revurdering = vedtak.behandling as IverksattRevurdering
        require(LocalDate.now(clock) >= periode.fraOgMed.plusMonths(1))
        sak.copy(
            uteståendeKravgrunnlag = genererKravgrunnlagFraSimulering(
                saksnummer = revurdering.saksnummer,
                simulering = vedtak.simulering,
                utbetalingId = vedtak.utbetalingId!!,
                clock = clock,
                // TODO jah: Her bør vi knytte den til en ekte hendelse på saken og bare ninja-injecte den i tilstanden.
                kravgrunnlagPåSakHendelseId = HendelseId.generer(),
            ),
        )
    }
}

val kravgrunnlagEndringXml: String by lazy { getFileSourceContent("kravgrunnlag/kravgrunnlag_endring.xml") }
val kravgrunnlagOpphørXml: String by lazy { getFileSourceContent("kravgrunnlag/kravgrunnlag_opphør.xml") }
