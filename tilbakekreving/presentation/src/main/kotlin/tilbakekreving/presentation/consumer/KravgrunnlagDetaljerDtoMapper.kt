package tilbakekreving.presentation.consumer

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
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

private fun hentBeløp(tilbakekrevingsbeløp: List<Tilbakekrevingsbeløp>): Pair<Tilbakekrevingsbeløp, Tilbakekrevingsbeløp> {
    val ytelse = tilbakekrevingsbeløp.filter { it.typeKlasse == KlasseType.YTEL.name }
        .takeIf { it.size == 1 }
        ?: throw RuntimeException("Hadde flere linjer av klassetypen YTEL, kun en skal forekomme")

    val tilbakekrevingsbeløpForYtelse =
        ytelse.first { (it.kodeKlasse == KlasseKode.SUUFORE.name || it.kodeKlasse == KlasseKode.SUALDER.name) }

    val feilbeløp = tilbakekrevingsbeløp.filter { it.typeKlasse == KlasseType.FEIL.name }
        .takeIf { it.size == 1 }
        ?: throw RuntimeException("Hadde flere linjer av klassetypen FEIL, kun en skal forekomme")
    val tilbakekrevingsbeløpForFeilutbetaling = feilbeløp.filter { if (tilbakekrevingsbeløpForYtelse.kodeKlasse == KlasseKode.SUUFORE.name) it.kodeKlasse == KlasseKode.KL_KODE_FEIL_INNT.name else it.kodeKlasse == KlasseKode.KL_KODE_FEIL.name }
        .takeIf { it.size == 1 }
        ?: throw RuntimeException("Mismatch mellom kodeklassen i beløpet for ytelsen og selve feilen og kodeklassen til beløpet")

    /*
    Basert på case i prod hendelseid: b6aae587-a1aa-4945-8d45-f597bba4e975
    Feil fra infotrygd men kravet kommer mot sualder. Her ligger belopNy som en justeringskonto ikke på linjene for typeKlasse>YTEL
     */
    if (tilbakekrevingsbeløp.any { it.typeKlasse == KlasseType.JUST.name && it.kodeKlasse == KlasseKode.KL_KODE_JUST_PEN.name }) {
        val justeringskontoLinje = tilbakekrevingsbeløp.filter { it.typeKlasse == KlasseType.JUST.name && it.kodeKlasse == KlasseKode.KL_KODE_JUST_PEN.name }
            .takeIf { it.size == 1 }
            ?: throw RuntimeException("Forventet bare en justeringskonto kodeklasse, kan ikke ha flere per krav.")
        val tilbakekrevingForYtelseMedJusteringsbeløp = tilbakekrevingsbeløpForYtelse.copy(
            kodeKlasse = tilbakekrevingsbeløpForYtelse.kodeKlasse,
            typeKlasse = tilbakekrevingsbeløpForYtelse.typeKlasse,
            belopOpprUtbet = tilbakekrevingsbeløpForYtelse.belopOpprUtbet,
            belopNy = justeringskontoLinje.first().belopNy,
            belopTilbakekreves = tilbakekrevingsbeløpForYtelse.belopTilbakekreves,
            belopUinnkrevd = tilbakekrevingsbeløpForYtelse.belopUinnkrevd,
            skattProsent = tilbakekrevingsbeløpForYtelse.skattProsent,
        )
        return Pair(tilbakekrevingForYtelseMedJusteringsbeløp, tilbakekrevingsbeløpForFeilutbetaling.first())
    }
    return Pair(tilbakekrevingsbeløpForYtelse, tilbakekrevingsbeløpForFeilutbetaling.first())
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

                    val (tilbakekrevingsbeløpForYtelse, tilbakekrevingsbeløpForFeilutbetaling) = hentBeløp(tilbakekrevingsperiode.tilbakekrevingsbeløp)

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
