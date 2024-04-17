package no.nav.su.se.bakover.database.tilbakekreving

import arrow.core.Either
import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.HistoriskSendtTilbakekrevingsvedtak
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.mapDbJsonToKravgrunnlag

/**
 * @param råttKravgrunnlagMapper brukes for de historiske tilfellene, der vi lagres RåttKravgrunnlag og ikke json-versjonen av Kravgrunnlag
 */
internal class TilbakekrevingUnderRevurderingPostgresRepo(
    private val råttKravgrunnlagMapper: MapRåttKravgrunnlag,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun hentTilbakekrevingsbehandling(
        revurderingId: RevurderingId,
        session: Session,
    ): HistoriskSendtTilbakekrevingsvedtak? {
        return """
            select * from revurdering_tilbakekreving where revurderingId = :revurderingId
        """.trimIndent()
            .hent(
                mapOf("revurderingId" to revurderingId.value),
                session,
            ) {
                it.toTilbakekrevingsbehandling()
            }
    }

    private fun Row.toTilbakekrevingsbehandling(): HistoriskSendtTilbakekrevingsvedtak {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val revurderingId = RevurderingId(uuid("revurderingId"))
        val sakId = uuid("sakId")
        val periode = Periode.create(
            fraOgMed = localDate("fraOgMed"),
            tilOgMed = localDate("tilOgMed"),
        )
        require(string("tilstand") == "sendt_tilbakekrevingsvedtak") {
            "Siden dette er en historisk tabell (read-only) har vi kontroll på at vi kun har denne typen tilstand igjen."
        }
        val avgjørelse = Avgjørelsestype.fromValue(string("avgjørelse"))
        val kravgrunnlag: Kravgrunnlag? = getKravgrunnlag(revurderingId)
        val kravgrunnlagMottatt = tidspunktOrNull("kravgrunnlagMottatt")
        val tilbakekrevingsvedtakForsendelse =
            stringOrNull("tilbakekrevingsvedtakForsendelse")?.let { forsendelseJson ->
                deserialize<RåTilbakekrevingsvedtakForsendelseDb>(forsendelseJson).let {
                    RåTilbakekrevingsvedtakForsendelse(
                        requestXml = it.requestXml,
                        tidspunkt = it.requestSendt,
                        responseXml = it.responseXml,
                    )
                }
            }

        return HistoriskSendtTilbakekrevingsvedtak(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
            avgjørelse = when (avgjørelse) {
                Avgjørelsestype.TILBAKEKREV -> HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.Tilbakekrev
                Avgjørelsestype.IKKE_TILBAKEKREV -> HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.IkkeTilbakekrev
            },
            kravgrunnlag = kravgrunnlag!!,
            kravgrunnlagMottatt = kravgrunnlagMottatt!!,
            tilbakekrevingsvedtakForsendelse = tilbakekrevingsvedtakForsendelse!!,

        )
    }

    private fun Row.getKravgrunnlag(revurderingId: RevurderingId): Kravgrunnlag? {
        return stringOrNull("kravgrunnlag")?.trim()?.let { dbJsonOrXml ->
            when {
                dbJsonOrXml.startsWith("<") -> {
                    Either.catch {
                        // Virker som mapperen kan kaste exceptions, så vi wrapper den for ikke å logge innholdet i XMLen.
                        råttKravgrunnlagMapper(RåttKravgrunnlag(dbJsonOrXml)).getOrElse { throw it }
                    }.getOrElse {
                        sikkerLogg.error(
                            "Klarte ikke mappe rått kravgrunnlag til domenemodellen for revurdering $revurderingId. Rått kravgrunnlag: $dbJsonOrXml",
                            it,
                        )
                        throw IllegalStateException(
                            "Klarte ikke mappe det rå kravgrunnlaget til domenemodellen for revurdering $revurderingId. Se sikkerlogg for det rå kravgrunnlaget og stack trace",
                        )
                    }
                }

                dbJsonOrXml.startsWith("{") -> {
                    mapDbJsonToKravgrunnlag(dbJsonOrXml).getOrElse {
                        sikkerLogg.error(
                            "Klarte ikke deseralisere til den nye kravgrunnlag-json-typen. For revurdering $revurderingId. Rått kravgrunnlag: $dbJsonOrXml",
                            it,
                        )
                        throw IllegalStateException("Klarte ikke deseralisere til den nye kravgrunnlag-json-typen. For revurdering $revurderingId. Se sikkerlogg for mer informasjon.")
                    }
                }

                else -> {
                    sikkerLogg.error(
                        "Ukjent format på revurdering_tilbakekreving.kravgrunnlag, forventet json eller xml, men var: $dbJsonOrXml",
                        RuntimeException("Trigger en exception for å få stacktrace"),
                    )
                    throw IllegalStateException(
                        "Ukjent format på revurdering_tilbakekreving.kravgrunnlag, forventet json eller xml. Se sikkerlogg for det rå kravgrunnlaget.",
                    )
                }
            }
        }
    }

    private enum class Avgjørelsestype(private val value: String) {
        TILBAKEKREV("tilbakekrev"),
        IKKE_TILBAKEKREV("ikke_tilbakekrev"),
        ;

        override fun toString() = value

        companion object {
            fun fromValue(value: String): Avgjørelsestype {
                return entries.firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent avgjørelsestype: $value")
            }
        }
    }
}
