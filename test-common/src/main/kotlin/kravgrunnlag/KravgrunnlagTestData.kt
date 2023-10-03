package no.nav.su.se.bakover.test.kravgrunnlag

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.hendelse.hendelseMetadata
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * @param clock Ignoreres dersom [hendelsestidspunkt] er satt.
 */
fun råttKravgrunnlagHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    clock: Clock = fixedClock,
    hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
    meta: HendelseMetadata = hendelseMetadata(),
    råttKravgrunnlag: RåttKravgrunnlag = råttKravgrunnlag(),
): RåttKravgrunnlagHendelse {
    return RåttKravgrunnlagHendelse(
        hendelseId = hendelseId,
        hendelsestidspunkt = hendelsestidspunkt,
        meta = meta,
        råttKravgrunnlag = råttKravgrunnlag,
    )
}

fun råttKravgrunnlag(
    xml: String = "<xml></xml>",
): RåttKravgrunnlag {
    return RåttKravgrunnlag(xml)
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
    meta: HendelseMetadata = hendelseMetadata(),
    tidligereHendelseId: HendelseId = HendelseId.generer(),
    eksternKravgrunnlagId: String = UUID.randomUUID().toString(),
    eksternVedtakId: String = UUID.randomUUID().toString(),
    eksternKontrollfelt: String = UUID.randomUUID().toString(),
    status: Kravgrunnlag.KravgrunnlagStatus = Kravgrunnlag.KravgrunnlagStatus.Nytt,
    behandler: String = attestant.navIdent,
    utbetalingId: UUID30 = UUID30.randomUUID(),
    grunnlagsmåneder: Nel<Kravgrunnlag.Grunnlagsperiode> = nonEmptyListOf(
        grunnlagsmåned(),
    ),
    revurderingId: UUID? = null,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
): KravgrunnlagPåSakHendelse {
    return KravgrunnlagPåSakHendelse(
        hendelseId = hendelseId,
        versjon = versjon,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        meta = meta,
        tidligereHendelseId = tidligereHendelseId,
        kravgrunnlag = Kravgrunnlag(
            kravgrunnlagId = eksternKravgrunnlagId,
            vedtakId = eksternVedtakId,
            kontrollfelt = eksternKontrollfelt,
            status = status,
            behandler = NavIdentBruker.Saksbehandler(behandler),
            utbetalingId = utbetalingId,
            grunnlagsperioder = grunnlagsmåneder,
            saksnummer = saksnummer,
        ),
        revurderingId = revurderingId,
    )
}

fun grunnlagsmåned(
    måned: Måned = januar(2021),
    betaltSkattForYtelsesgruppen: BigDecimal = BigDecimal("1000.00"),
    grunnlagsbeløps: NonEmptyList<Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp> = nonEmptyListOf(
        grunnlagsbeløpFeil(),
        grunnlagsbeløpYtel(),
    ),
): Kravgrunnlag.Grunnlagsperiode {
    return Kravgrunnlag.Grunnlagsperiode(
        periode = måned,
        beløpSkattMnd = betaltSkattForYtelsesgruppen,
        grunnlagsbeløp = grunnlagsbeløps,
    )
}

fun grunnlagsbeløpYtel(
    kode: KlasseKode = KlasseKode.SUUFORE,
    type: KlasseType = KlasseType.YTEL,
    beløpTidligereUtbetaling: BigDecimal = BigDecimal("2000.00"),
    beløpNyUtbetaling: BigDecimal = BigDecimal("1000.00"),
    beløpSkalTilbakekreves: BigDecimal = BigDecimal("1000.00"),
    beløpSkalIkkeTilbakekreves: BigDecimal = BigDecimal("0.00"),
    skatteProsent: BigDecimal = BigDecimal("50.0000"),
): Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp {
    require(beløpTidligereUtbetaling - beløpNyUtbetaling == beløpSkalTilbakekreves + beløpSkalIkkeTilbakekreves) {
        "beløpTidligereUtbetaling $beløpTidligereUtbetaling - beløpNyUtbetaling $beløpNyUtbetaling må være lik beløpSkalTilbakekreves $beløpSkalTilbakekreves + beløpSkalIkkeTilbakekreves $beløpSkalIkkeTilbakekreves"
    }
    require(skatteProsent >= BigDecimal.ZERO && skatteProsent <= BigDecimal("100.0000"))
    return Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
        kode = kode,
        type = type,
        beløpTidligereUtbetaling = beløpTidligereUtbetaling,
        beløpNyUtbetaling = beløpNyUtbetaling,
        beløpSkalTilbakekreves = beløpSkalTilbakekreves,
        beløpSkalIkkeTilbakekreves = beløpSkalIkkeTilbakekreves,
        skatteProsent = skatteProsent,
    )
}

fun grunnlagsbeløpFeil(
    kode: KlasseKode = KlasseKode.KL_KODE_FEIL_INNT,
    type: KlasseType = KlasseType.FEIL,
    beløpNyUtbetaling: BigDecimal = BigDecimal("1000"),
): Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp {
    require(beløpNyUtbetaling >= BigDecimal.ZERO)
    return Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
        kode = kode,
        type = type,
        beløpTidligereUtbetaling = BigDecimal.ZERO,
        beløpNyUtbetaling = beløpNyUtbetaling,
        beløpSkalTilbakekreves = BigDecimal.ZERO,
        beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
        skatteProsent = BigDecimal.ZERO,
    )
}
