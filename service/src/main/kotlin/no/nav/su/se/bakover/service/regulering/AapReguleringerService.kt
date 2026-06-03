package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.BeregnAap
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.FradragSomMåReguleresManuelt
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.erAktivtVedtakPå
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

interface AapReguleringerService {
    fun hentReguleringer(parameter: HentReguleringerPesysParameter): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>
}

class AapReguleringerServiceImpl(
    private val aapApiInternClient: AapApiInternClient,
) : AapReguleringerService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentReguleringer(parameter: HentReguleringerPesysParameter): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
        return parameter.brukereMedEps.map { brukerMedEps ->
            val reguleringForBruker = if (Fradragstype.Arbeidsavklaringspenger in brukerMedEps.fradragstyperBruker) {
                hentRegulertAapBeløpForPerson(
                    fnr = brukerMedEps.fnr,
                    datoFørRegulering = parameter.månedFørRegulering,
                    saksnummer = brukerMedEps.saksnummer,
                )
            } else {
                null
            }
            val eps = brukerMedEps.eps
            val reguleringForEps = if (
                eps != null &&
                Fradragstype.Arbeidsavklaringspenger in brukerMedEps.fradragstyperEps
            ) {
                hentRegulertAapBeløpForPerson(
                    fnr = eps,
                    datoFørRegulering = parameter.månedFørRegulering,
                    saksnummer = brukerMedEps.saksnummer,
                )
            } else {
                null
            }

            // IngenGyldigAapPeriode rutes til manuell behandling (se manuellMarkør under), ikke til FEILET.
            // Alle andre AAP-feil er harde feil som skal føre til at saken feiler:
            // KunneIkkeHenteAap, FlereGyldigeAapPerioder, AapIkkeBekreftetRegulert, AapBeløpErIkkeØkning
            // og AapVedtaksdatoErFørReguleringtidspunkt.
            val feil = listOfNotNull(
                reguleringForBruker.hardFeil(),
                reguleringForEps.hardFeil(),
            )

            if (feil.isNotEmpty()) {
                HentingAvEksterneReguleringerFeiletForBruker(
                    fnr = brukerMedEps.fnr,
                    alleFeil = feil,
                ).left()
            } else {
                EksterntRegulerteBeløp(
                    brukerFnr = brukerMedEps.fnr,
                    beløpBruker = listOfNotNull(reguleringForBruker.høyreVerdi()),
                    beløpEps = listOfNotNull(reguleringForEps.høyreVerdi()),
                    fradragSomMåReguleresManuelt = listOfNotNull(
                        reguleringForBruker.manuellMarkør(FradragTilhører.BRUKER),
                        reguleringForEps.manuellMarkør(FradragTilhører.EPS),
                    ),
                ).right()
            }
        }
    }

    private fun hentRegulertAapBeløpForPerson(
        fnr: Fnr,
        saksnummer: Saksnummer,
        datoFørRegulering: LocalDate,
        reguleringsdato: LocalDate = datoFørRegulering.plusMonths(1),
    ): Either<FeilMedEksternRegulering, RegulertBeløp> = aapApiInternClient.hentMaksimumUtenUtbetaling(
        fnr = fnr,
        fraOgMedDato = datoFørRegulering,
        tilOgMedDato = reguleringsdato,
    ).fold(
        ifLeft = {
            log.warn("AAP-regulering: Klarte ikke hente maksimum for saksnummer {}", saksnummer)
            FeilMedEksternRegulering.KunneIkkeHenteAap.left()
        },
        ifRight = { response ->
            log.info("AAP-regulering: hentet maksimum mellom dato mai ${datoFørRegulering.year - 1} frem til og med desember ${datoFørRegulering.year} for sak=$saksnummer. antall perioder=${response.vedtak.size}")
            val vedtakFørRegulering = response.vedtak.gyldigPå(datoFørRegulering)
            val vedtakEtterRegulering = response.vedtak.gyldigPå(reguleringsdato)
            when {
                vedtakFørRegulering is Either.Left -> vedtakFørRegulering
                vedtakEtterRegulering is Either.Left -> vedtakEtterRegulering
                else -> {
                    val førRegulering = (vedtakFørRegulering as Either.Right).value
                    val etterRegulering = (vedtakEtterRegulering as Either.Right).value
                    if (førRegulering == null || etterRegulering == null) {
                        log.info("AAP-regulering: Fant ikke gyldig vedtak før/etter regulering for saksnummer: {}", saksnummer)
                        return@fold FeilMedEksternRegulering.IngenGyldigAapPeriode(
                            fnr = fnr,
                            førRegulering = førRegulering,
                            etterRegulering = etterRegulering,
                            vedtakFraRespons = response.vedtak,
                        ).left()
                    } else {
                        val vedtaksdato = etterRegulering.vedtaksdato
                        if (vedtaksdato == null || vedtaksdato.isBefore(reguleringsdato)) {
                            return@fold FeilMedEksternRegulering.AapVedtaksdatoErFørReguleringtidspunkt.left()
                        }

                        val beløpFør = BeregnAap.AapBeregning.fraMaksimumVedtak(førRegulering)
                        val beløpEtter = BeregnAap.AapBeregning.fraMaksimumVedtak(etterRegulering)
                        when {
                            beløpFør.sats == beløpEtter.sats -> {
                                log.info("AAP-regulering: Fant ikke beløpsendring mellom april og mai for fnr: {}", fnr)
                                FeilMedEksternRegulering.AapIkkeBekreftetRegulert.left()
                            }
                            beløpFør.sats < beløpEtter.sats -> tilRegulertAapBeløp(
                                fnr = fnr,
                                førRegulering = beløpFør,
                                etterRegulering = beløpEtter,
                            ).right()
                            else -> {
                                log.info("AAP-regulering: Fant ingen økning i beløpet i app, tipper regulering ikke er kjørt for fnr: {}", fnr)
                                FeilMedEksternRegulering.AapBeløpErIkkeØkning.left()
                            }
                        }
                    }
                }
            }
        },
    )
}

internal fun List<MaksimumVedtakDto>.gyldigPå(dato: LocalDate): Either<FeilMedEksternRegulering, MaksimumVedtakDto?> {
    val gyldigeVedtak = filter { it.erAktivtVedtakPå(dato) }

    return when (gyldigeVedtak.size) {
        0 -> null.right()
        1 -> gyldigeVedtak.single().right()
        else -> {
            LoggerFactory.getLogger("Regulering").info("AAP-regulering: Fant flere gyldige vedtak på dato {}", dato)
            FeilMedEksternRegulering.FlereGyldigeAapPerioder.left()
        }
    }
}

private fun <L, R> Either<L, R>?.høyreVerdi(): R? = when (this) {
    is Either.Right -> value
    else -> null
}

private fun <L, R> Either<L, R>?.venstreVerdi(): L? = when (this) {
    is Either.Left -> value
    else -> null
}

/**
 * Harde feil som skal føre til at saken feiler (FEILET). [FeilMedEksternRegulering.IngenGyldigAapPeriode]
 * regnes ikke som en hard feil — den rutes i stedet til manuell behandling via [manuellMarkør].
 */
private fun Either<FeilMedEksternRegulering, RegulertBeløp>?.hardFeil(): FeilMedEksternRegulering? =
    venstreVerdi()?.takeUnless { it is FeilMedEksternRegulering.IngenGyldigAapPeriode }

/**
 * Lager en markør om at AAP for denne personen må reguleres manuelt fordi det ikke fantes en gyldig
 * AAP-periode på reguleringstidspunktet (kun stans eller opphørt).
 */
private fun Either<FeilMedEksternRegulering, RegulertBeløp>?.manuellMarkør(
    tilhører: FradragTilhører,
): FradragSomMåReguleresManuelt? =
    if (venstreVerdi() is FeilMedEksternRegulering.IngenGyldigAapPeriode) {
        FradragSomMåReguleresManuelt(tilhører)
    } else {
        null
    }
