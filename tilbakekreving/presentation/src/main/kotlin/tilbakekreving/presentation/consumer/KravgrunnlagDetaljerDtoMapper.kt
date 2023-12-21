package tilbakekreving.presentation.consumer

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNone
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.vedtak.Revurderingsvedtak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse
import økonomi.domain.utbetaling.Utbetaling
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

internal fun KravgrunnlagRootDto.toHendelse(
    hentSak: (Saksnummer) -> Either<Throwable, Sak>,
    hendelsesTidspunkt: Tidspunkt,
    tidligereHendelseId: HendelseId,
): Either<Throwable, Pair<Sak, KravgrunnlagDetaljerPåSakHendelse>> {
    val hendelseId = HendelseId.generer()
    val kravgrunnlag = this.toDomain(hendelseId).getOrElse {
        return it.left()
    }

    return Either.catch {
        this.kravgrunnlagDto.let {
            val saksnummer = Saksnummer.parse(it.fagsystemId)
            val sak = hentSak(saksnummer).getOrElse {
                return it.left()
            }
            val revurdering = hentUtbetaling(
                sak = sak,
                utbetalingId = kravgrunnlag.utbetalingId,
                hendelseId = tidligereHendelseId,
            ).getOrElse {
                return it.left()
            }
            sak to KravgrunnlagDetaljerPåSakHendelse(
                hendelseId = hendelseId,
                versjon = sak.versjon.inc(),
                sakId = sak.id,
                hendelsestidspunkt = hendelsesTidspunkt,
                tidligereHendelseId = tidligereHendelseId,
                kravgrunnlag = kravgrunnlag,
                revurderingId = revurdering?.id,
            )
        }
    }
}

internal fun KravgrunnlagRootDto.toDomain(
    hendelseId: HendelseId,
): Either<Throwable, Kravgrunnlag> {
    return Either.catch {
        this.kravgrunnlagDto.let { kravgrunnlagDto ->
            Kravgrunnlag(
                saksnummer = Saksnummer(kravgrunnlagDto.fagsystemId.toLong()),
                eksternKravgrunnlagId = kravgrunnlagDto.kravgrunnlagId,
                eksternVedtakId = kravgrunnlagDto.vedtakId,
                status = kravgrunnlagDto.kodeStatusKrav.toKravgrunnlagstatus(),
                eksternKontrollfelt = kravgrunnlagDto.kontrollfelt,
                behandler = kravgrunnlagDto.saksbehId,
                utbetalingId = UUID30.fromString(kravgrunnlagDto.utbetalingId),
                grunnlagsperioder = kravgrunnlagDto.tilbakekrevingsperioder.map { tilbakekrevingsperiode ->
                    require(tilbakekrevingsperiode.tilbakekrevingsbeløp.size == 2) {
                        "Forventer at det er to tilbakekrevingsbeløp per måned, en for ytelse og en for feilutbetaling. Hvis dette oppstår må man forstå det rå kravgrunnlaget på nytt."
                    }

                    val tilbakekrevingsbeløpForYtelse =
                        tilbakekrevingsperiode.tilbakekrevingsbeløp.single { it.typeKlasse == "YTEL" && it.kodeKlasse == "SUUFORE" }
                    val tilbakekrevingsbeløpForFeilutbetaling = tilbakekrevingsperiode.tilbakekrevingsbeløp
                        .single { it.typeKlasse == "FEIL" && it.kodeKlasse == "KL_KODE_FEIL_INNT" }
                    val bruttoFeilutbetaling =
                        BigDecimal(tilbakekrevingsbeløpForYtelse.belopTilbakekreves).intValueExact()

                    // --- tilbakekrevingsbeløpForYtelse ---
                    require(BigDecimal(tilbakekrevingsbeløpForYtelse.belopUinnkrevd).compareTo(BigDecimal.ZERO) == 0) {
                        "Forventer at kravgrunnlaget sitt belopUinnkrevd alltid er 0, men var ${tilbakekrevingsbeløpForYtelse.belopUinnkrevd}"
                    }
                    // --- tilbakekrevingsbeløpForFeilutbetaling ---
                    require(BigDecimal(tilbakekrevingsbeløpForFeilutbetaling.skattProsent).compareTo(BigDecimal.ZERO) == 0) {
                        "Forventer at skatteprosenten alltid er 0 for FEIL i kravgrunnlag fra oppdrag, men var ${tilbakekrevingsbeløpForFeilutbetaling.skattProsent}"
                    }
                    require(BigDecimal(tilbakekrevingsbeløpForFeilutbetaling.belopOpprUtbet).compareTo(BigDecimal.ZERO) == 0) {
                        "Forventer at belopOpprUtbet alltid er 0 for FEIL i kravgrunnlag fra oppdrag, men var ${tilbakekrevingsbeløpForFeilutbetaling.belopOpprUtbet}"
                    }
                    require(BigDecimal(tilbakekrevingsbeløpForFeilutbetaling.belopTilbakekreves).compareTo(BigDecimal.ZERO) == 0) {
                        "Forventer at belopOpprUtbet alltid er 0 for FEIL i kravgrunnlag fra oppdrag, men var ${tilbakekrevingsbeløpForFeilutbetaling.belopTilbakekreves}"
                    }
                    require(BigDecimal(tilbakekrevingsbeløpForFeilutbetaling.belopUinnkrevd).compareTo(BigDecimal.ZERO) == 0) {
                        "Forventer at belopOpprUtbet alltid er 0 for FEIL i kravgrunnlag fra oppdrag, men var ${tilbakekrevingsbeløpForFeilutbetaling.belopUinnkrevd}"
                    }
                    require(BigDecimal(tilbakekrevingsbeløpForFeilutbetaling.belopNy).intValueExact() == bruttoFeilutbetaling) {
                        "Forventer at belopNy(${tilbakekrevingsbeløpForFeilutbetaling.belopNy}) i feilutbetalingsdelen alltid er lik bruttoFeilutbetaling($bruttoFeilutbetaling) i ytelsesdelen i kravgrunnlag fra oppdrag."
                    }

                    Kravgrunnlag.Grunnlagsperiode(
                        periode = Måned.fra(
                            LocalDate.parse(tilbakekrevingsperiode.periode.fraOgMed),
                            LocalDate.parse(tilbakekrevingsperiode.periode.tilOgMed),
                        ),
                        betaltSkattForYtelsesgruppen = BigDecimal(tilbakekrevingsperiode.skattebeløpPerMåned).intValueExact(),
                        bruttoTidligereUtbetalt = BigDecimal(tilbakekrevingsbeløpForYtelse.belopOpprUtbet).intValueExact(),
                        bruttoNyUtbetaling = BigDecimal(tilbakekrevingsbeløpForYtelse.belopNy).intValueExact(),
                        bruttoFeilutbetaling = bruttoFeilutbetaling,
                        skatteProsent = BigDecimal(tilbakekrevingsbeløpForYtelse.skattProsent),
                    )
                },
                eksternTidspunkt = Tidspunkt.create(kontrollfeltFormatter.parse(kravgrunnlagDto.kontrollfelt, Instant::from)),
                hendelseId = hendelseId,
            )
        }
    }
}

fun hentUtbetaling(
    sak: Sak,
    utbetalingId: UUID30,
    hendelseId: HendelseId,
): Either<Throwable, IverksattRevurdering?> {
    val saksnummer = sak.saksnummer
    val utbetaling =
        sak.utbetalinger.filter { it.id == utbetalingId }.toNonEmptyListOrNone()
            .getOrElse {
                return IllegalStateException("Kunne ikke prosessere kravgrunnlag: Fant ikke utbetaling med id $utbetalingId på sak $saksnummer og hendelse $hendelseId. Kanskje den ikke er opprettet enda? Prøver igjen ved neste kjøring.").left()
            }.single()
    return when (utbetaling) {
        is Utbetaling.SimulertUtbetaling,
        is Utbetaling.UtbetalingForSimulering,
        is Utbetaling.OversendtUtbetaling.UtenKvittering,
        -> return IllegalStateException("Kunne ikke prosessere kravgrunnlag: Utbetalingen skal ikke være i tilstanden ${utbetaling::class.simpleName} for utbetalingId ${utbetaling.id} og hendelse $hendelseId").left()

        is Utbetaling.OversendtUtbetaling.MedKvittering -> {
            finnRevurderingKnyttetTilKravgrunnlag(
                sak = sak,
                utbetalingId = utbetaling.id,
            ).getOrElse { return it.left() }.right()
        }
    }
}

private fun finnRevurderingKnyttetTilKravgrunnlag(
    sak: Sak,
    utbetalingId: UUID30,
): Either<Throwable, IverksattRevurdering?> {
    val avventerKravgrunnlag = sak.revurderinger
        .filterIsInstance<IverksattRevurdering>()
        .filter { it.tilbakekrevingsbehandling is TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag }

    return when (avventerKravgrunnlag.size) {
        0 -> null.right()
        1 -> {
            val revurdering = avventerKravgrunnlag.single()
            val matcherUtbetalingId = sak.vedtakListe
                .filterIsInstance<Revurderingsvedtak>()
                .any { it.behandling.id == revurdering.id && it.utbetalingId == utbetalingId }

            if (matcherUtbetalingId) {
                revurdering.right()
            } else {
                IllegalStateException(
                    "Mottok et kravgrunnlag med utbetalingId $utbetalingId som ikke matcher revurderingId ${revurdering.id}(avventer kravgrunnlag) på sak ${sak.id}. Denne kommer sannsynligvis til å feile igjen.",
                    RuntimeException("Trigger stacktrace"),
                ).left()
            }
        }

        else -> {
            IllegalStateException(
                "Fant flere revurderinger med tilbakekrevingsbehandling av typen Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag på sak ${sak.id}. Denne kommer sannsynligvis til å feile igjen.",
                RuntimeException("Trigger stacktrace"),
            ).left()
        }
    }
}
