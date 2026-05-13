package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.BeregnAap
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.erAktivtVedtakPå
import org.slf4j.LoggerFactory
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
        val reguleringstidspunkt = parameter.månedFørRegulering.plusMonths(1)
        val datoFørRegulering = parameter.månedFørRegulering

        return parameter.brukereMedEps.map { brukerMedEps ->
            val reguleringForBruker = if (Fradragstype.Arbeidsavklaringspenger in brukerMedEps.fradragstyperBruker) {
                hentRegulertAapBeløpForPerson(
                    fnr = brukerMedEps.fnr,
                    fraOgMedDato = parameter.månedFørRegulering,
                    datoFørRegulering = datoFørRegulering,
                    reguleringstidspunkt = reguleringstidspunkt,
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
                    fraOgMedDato = parameter.månedFørRegulering,
                    datoFørRegulering = datoFørRegulering,
                    reguleringstidspunkt = reguleringstidspunkt,
                )
            } else {
                null
            }

            val feil = listOfNotNull(
                reguleringForBruker.venstreVerdi(),
                reguleringForEps.venstreVerdi(),
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
                ).right()
            }
        }
    }

    private fun hentRegulertAapBeløpForPerson(
        fnr: Fnr,
        fraOgMedDato: LocalDate,
        datoFørRegulering: LocalDate,
        reguleringstidspunkt: LocalDate,
    ): Either<FeilMedEksternRegulering, RegulertBeløp> = aapApiInternClient.hentMaksimumUtenUtbetaling(
        fnr = fnr,
        fraOgMedDato = fraOgMedDato,
        tilOgMedDato = reguleringstidspunkt,
    ).fold(
        ifLeft = {
            log.warn("AAP-regulering: Klarte ikke hente maksimum for fnr {}", fnr)
            FeilMedEksternRegulering.KunneIkkeHenteAap.left()
        },
        ifRight = { response ->
            val vedtakFørRegulering = response.vedtak.gyldigPå(datoFørRegulering)
            val vedtakEtterRegulering = response.vedtak.gyldigPå(reguleringstidspunkt)
            when {
                vedtakFørRegulering is Either.Left -> vedtakFørRegulering
                vedtakEtterRegulering is Either.Left -> vedtakEtterRegulering
                else -> {
                    val førRegulering = (vedtakFørRegulering as Either.Right).value
                    val etterRegulering = (vedtakEtterRegulering as Either.Right).value
                    if (førRegulering == null || etterRegulering == null) {
                        log.info("AAP-regulering: Fant ikke gyldig vedtak før/etter regulering for fnr: {}", fnr)
                        return@fold FeilMedEksternRegulering.IngenGyldigAapPeriode.left()
                    } else {
                        if (etterRegulering.vedtaksdato?.month != reguleringstidspunkt.month) {
                            return@fold FeilMedEksternRegulering.AapVedtaksdatoErikkeSammeSomReguleringtidspunkt.left()
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
