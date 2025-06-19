package tilbakekreving.presentation.consumer

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.presentation.consumer.KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
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
            sak to KravgrunnlagDetaljerPåSakHendelse(
                hendelseId = hendelseId,
                versjon = sak.versjon.inc(),
                sakId = sak.id,
                hendelsestidspunkt = hendelsesTidspunkt,
                tidligereHendelseId = tidligereHendelseId,
                kravgrunnlag = kravgrunnlag,
                // Vi støtter ikke lenger tilbakekreving under revurdering, så denne ville alltid blitt null fra nå.
                revurderingId = null,
            )
        }
    }
}

private fun singleMedErrorlogging(listeAvbeløp: List<Tilbakekrevingsbeløp>, predicate: (Tilbakekrevingsbeløp) -> Boolean): Tilbakekrevingsbeløp {
    try {
        return listeAvbeløp.single { predicate(it) }
    } catch (e: IllegalArgumentException) {
        sikkerLogg.error("Fant flere som matchet predikatet for tilbakekrevingslista: $listeAvbeløp", e)
        throw RuntimeException("Fant flere som matchet predikatet, se sikkerlogg", e)
    } catch (e: NoSuchElementException) {
        sikkerLogg.error("Fant ingen som matchet predikatet for tilbakekrevingslista: $listeAvbeløp", e)
        throw RuntimeException("Fant ingen som matchet predikatet, se sikkerlogg", e)
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

                    val tilbakekrevingsbeløpForYtelse = singleMedErrorlogging(tilbakekrevingsperiode.tilbakekrevingsbeløp, { it.typeKlasse == KlasseType.YTEL.name && (it.kodeKlasse == KlasseKode.SUUFORE.name || it.kodeKlasse == KlasseKode.SUALDER.name) })
                    val tilbakekrevingsbeløpForFeilutbetaling = singleMedErrorlogging(tilbakekrevingsperiode.tilbakekrevingsbeløp, { it.typeKlasse == KlasseType.FEIL.name && it.kodeKlasse == KlasseKode.KL_KODE_FEIL_INNT.name })
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
                eksternTidspunkt = Tidspunkt.create(
                    kontrollfeltFormatter.parse(
                        kravgrunnlagDto.kontrollfelt,
                        Instant::from,
                    ),
                ),
                hendelseId = hendelseId,
            )
        }
    }
}
