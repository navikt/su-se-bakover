package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import org.slf4j.LoggerFactory
import java.time.LocalDate

interface AapReguleringerService {
    fun hentReguleringer(parameter: HentReguleringerPesysParameter): List<EksterntRegulerteBeløp>
}

class AapReguleringerServiceImpl(
    private val aapApiInternClient: AapApiInternClient,
) : AapReguleringerService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentReguleringer(parameter: HentReguleringerPesysParameter): List<EksterntRegulerteBeløp> {
        val reguleringstidspunkt = parameter.månedFørRegulering.plusMonths(1)
        val datoFørRegulering = reguleringstidspunkt.minusDays(1)

        return parameter.brukereMedEps.map { brukerMedEps ->
            EksterntRegulerteBeløp(
                fnr = brukerMedEps.fnr,
                beløpBruker = hentRegulertAapBeløpForPerson(
                    fnr = brukerMedEps.fnr,
                    skalHenteAap = brukerMedEps.harAapBruker,
                    fraOgMedDato = parameter.månedFørRegulering,
                    datoFørRegulering = datoFørRegulering,
                    reguleringstidspunkt = reguleringstidspunkt,
                )?.let(::listOf) ?: emptyList(),
                beløpEps = brukerMedEps.eps?.let { eps ->
                    hentRegulertAapBeløpForPerson(
                        fnr = eps,
                        skalHenteAap = brukerMedEps.harAapEps,
                        fraOgMedDato = parameter.månedFørRegulering,
                        datoFørRegulering = datoFørRegulering,
                        reguleringstidspunkt = reguleringstidspunkt,
                    )?.let(::listOf) ?: emptyList()
                } ?: emptyList(),
            )
        }
    }

    private fun hentRegulertAapBeløpForPerson(
        fnr: Fnr,
        skalHenteAap: Boolean,
        fraOgMedDato: LocalDate,
        datoFørRegulering: LocalDate,
        reguleringstidspunkt: LocalDate,
    ) = if (!skalHenteAap) {
        null
    } else {
        aapApiInternClient.hentMaksimum(
            fnr = fnr,
            fraOgMedDato = fraOgMedDato,
            tilOgMedDato = reguleringstidspunkt,
        ).fold(
            ifLeft = {
                log.warn("AAP-regulering: Klarte ikke hente maksimum for fnr {}", fnr)
                null
            },
            ifRight = { response ->
                val vedtakFørRegulering = response.vedtak.gyldigPå(datoFørRegulering)
                val vedtakEtterRegulering = response.vedtak.gyldigPå(reguleringstidspunkt)
                if (vedtakFørRegulering == null || vedtakEtterRegulering == null) {
                    log.info("AAP-regulering: Fant ikke entydig vedtak før/etter regulering for fnr {}", fnr)
                    null
                } else {
                    tilRegulertAapBeløp(
                        fnr = fnr,
                        førRegulering = vedtakFørRegulering,
                        etterRegulering = vedtakEtterRegulering,
                    )
                }
            },
        )
    }
}

internal fun List<MaksimumVedtakDto>.gyldigPå(dato: LocalDate): MaksimumVedtakDto? {
    return filter { vedtak ->
        val periode = vedtak.periode ?: return@filter false
        val fraOgMed = periode.fraOgMedDato ?: return@filter false
        val tilOgMed = periode.tilOgMedDato ?: return@filter false
        vedtak.opphorsAarsak == null && !dato.isBefore(fraOgMed) && !dato.isAfter(tilOgMed)
    }.singleOrNull()
}
