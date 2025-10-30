package tilbakekreving.domain

import dokument.domain.DokumentHendelseSerie
import dokument.domain.DokumentHendelser
import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.singleOrNullOrThrow
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelser
import tilbakekreving.domain.vedtak.VedtakTilbakekrevingsbehandling
import java.time.Clock
import java.util.UUID

/**
 * @param tilhørendeOgSorterteDokumentHendelser - Dokumenthendelsen må ha en tilbakekrevingshendelse i sin relaterteHendelser
 */
data class TilbakekrevingsbehandlingHendelser private constructor(
    private val sakId: UUID,
    private val saksnummer: Saksnummer,
    private val fnr: Fnr,
    private val sorterteHendelser: List<TilbakekrevingsbehandlingHendelse>,
    val kravgrunnlagPåSak: KravgrunnlagPåSakHendelser,
    private val tilhørendeOgSorterteDokumentHendelser: DokumentHendelser,
    private val clock: Clock,
) : List<TilbakekrevingsbehandlingHendelse> by sorterteHendelser {
    init {
        require(sorterteHendelser.sorted() == sorterteHendelser) {
            "TilbakekrevingsbehandlingHendelser må være sortert etter stigende versjon."
        }
        require(sorterteHendelser.distinctBy { it.hendelseId } == sorterteHendelser) {
            "TilbakekrevingsbehandlingHendelser kan ikke ha duplikat hendelseId."
        }
        require(sorterteHendelser.distinctBy { it.versjon } == sorterteHendelser) {
            "TilbakekrevingsbehandlingHendelser kan ikke ha duplikat versjon."
        }

        sorterteHendelser.groupBy { it.id }.forEach {
            require(it.value.first() is OpprettetTilbakekrevingsbehandlingHendelse || it.value.first() is OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse) {
                "Den første hendelsen må være en opprettet hendelse"
            }
            it.value.filter {
                it is OpprettetTilbakekrevingsbehandlingHendelse || it is OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse
            }.let {
                require(it.size == 1) {
                    "Den første hendelse (og kun den første) må være en OpprettetTilbakekrevingsbehandlingHendelse, men fant: ${it.size}"
                }
            }
        }

        sorterteHendelser.mapNotNull { it.tidligereHendelseId }.let {
            require(it.distinct() == it) {
                "En hendelse kan kun bli endret en gang. Oppdaget duplikate tidligereHendelseId: ${
                    it.groupBy { it }.filter { it.value.size > 1 }.values
                }"
            }
        }
        require(sorterteHendelser.map { it.sakId }.distinct().size <= 1) {
            "TilbakekrevingsbehandlingHendelser kan kun være knyttet til én sak, men var: ${
                sorterteHendelser.map { it.sakId }.distinct()
            }"
        }
        require(sorterteHendelser.map { it.entitetId }.distinct().size <= 1) {
            "TilbakekrevingsbehandlingHendelser kan kun være knyttet til én enitetId (samme som sakId), men var: ${
                sorterteHendelser.map { it.entitetId }.distinct()
            }"
        }
        require(
            tilhørendeOgSorterteDokumentHendelser.all { dokumentHendelse ->
                sorterteHendelser.any { opprettetHendelse ->
                    dokumentHendelse.relatertHendelse == opprettetHendelse.hendelseId
                }
            },
        ) { "DokumentHendelse må være relatert til minst 1 tilbakekrevingsbehandlingHendelse" }
    }

    val currentState: Tilbakekrevingsbehandlinger by lazy {
        if (sorterteHendelser.isEmpty()) {
            Tilbakekrevingsbehandlinger.empty(sakId)
        } else {
            toCurrentState().also {
                it.behandlinger.filterIsInstance<IverksattTilbakekrevingsbehandling>()
                    .also { iverksattTilbakekrevingsbehandlinger ->
                        iverksattTilbakekrevingsbehandlinger.map { it.kravgrunnlag.eksternKravgrunnlagId }.also {
                            if (it.distinct().size != it.size) {
                                throw IllegalStateException("Kun én iverksatt tilbakekrevingsbehandling kan referere til ett kravgrunnlag, men fant disse eksternKravgrunnlagIdene: $it")
                            }
                        }
                        iverksattTilbakekrevingsbehandlinger.map { it.kravgrunnlag.eksternVedtakId }.also {
                            if (it.distinct().size != it.size) {
                                throw IllegalStateException("Kun én iverksatt tilbakekrevingsbehandling kan referere til ett kravgrunnlag, men fant disse eksternVedtakId: $it")
                            }
                        }
                    }
            }
        }
    }

    private fun toCurrentState(): Tilbakekrevingsbehandlinger {
        return this.fold(mapOf<HendelseId, Tilbakekrevingsbehandling>()) { acc, hendelse ->
            val hendelseId = hendelse.hendelseId
            when (hendelse) {
                is OpprettetTilbakekrevingsbehandlingHendelse -> {
                    val kravgrunnlagsDetaljer =
                        this.kravgrunnlagPåSak.hentKravgrunnlagDetaljerPåSakHendelseForHendelseId(hendelse.kravgrunnlagPåSakHendelseId)!!
                    // opprettet er alltid den første hendelsen i serien, så derfor trenger vi ikke trekke fra tidligereHendelseId.
                    acc.plus(
                        hendelseId to hendelse.toDomain(
                            fnr = fnr,
                            kravgrunnlagPåSakHendelse = kravgrunnlagsDetaljer,
                            erKravgrunnlagUtdatert = this.kravgrunnlagPåSak.hentSisteKravgrunnagforEksternVedtakId(
                                kravgrunnlagsDetaljer.kravgrunnlag.eksternVedtakId,
                            ) != kravgrunnlagsDetaljer.kravgrunnlag,
                        ),
                    )
                }

                is OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse -> {
                    // TODO bjg holder dette eller vil det alltid varsle utdatert sevl etter nye hendelser?
                    val erKravgrunnlagUtdatert = this.kravgrunnlagPåSak.hentSisteKravgrunnlag()?.erÅpen() ?: false
                    acc.plus(
                        hendelseId to hendelse.toDomain(fnr, saksnummer, erKravgrunnlagUtdatert),
                    )
                }

                is VurdertTilbakekrevingsbehandlingHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is BrevTilbakekrevingsbehandlingHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is ForhåndsvarsletTilbakekrevingsbehandlingHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is TilAttesteringHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is IverksattHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is AvbruttHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is UnderkjentHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is NotatTilbakekrevingsbehandlingHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId]!!),
                ).minus(hendelse.tidligereHendelseId)

                is OppdatertKravgrunnlagPåTilbakekrevingHendelse -> acc.plus(
                    hendelseId to hendelse.applyToState(
                        behandling = acc[hendelse.tidligereHendelseId]!!,
                        kravgrunnlag = this.kravgrunnlagPåSak.hentKravgrunnlagDetaljerPåSakHendelseForHendelseId(
                            hendelse.kravgrunnlagPåSakHendelseId,
                        )!!.kravgrunnlag,
                    ),
                ).minus(hendelse.tidligereHendelseId)
            }
        }.values.toList().sortedBy { it.versjon }.let {
            Tilbakekrevingsbehandlinger(this.sakId, it)
        }
    }

    val currentStateVedtak: List<VedtakTilbakekrevingsbehandling> by lazy {
        if (sorterteHendelser.isEmpty()) {
            emptyList()
        } else {
            toCurrentStateVedtak()
        }
    }

    private fun toCurrentStateVedtak(): List<VedtakTilbakekrevingsbehandling> {
        return toCurrentState().behandlinger.filterIsInstance<IverksattTilbakekrevingsbehandling>().map { iverksatt ->
            val dokumenttilstand: Dokumenttilstand =
                tilhørendeOgSorterteDokumentHendelser.hentSerieForRelatertHendelse(iverksatt.hendelseId)
                    ?.dokumenttilstand() ?: iverksatt.vedtaksbrevvalg.tilDokumenttilstand()

            sorterteHendelser.single { it.hendelseId == iverksatt.hendelseId }.let {
                it as IverksattHendelse
            }.toVedtak(iverksatt = iverksatt, dokumenttilstand = dokumenttilstand)
        }
    }

    /**
     * @throws IllegalArgumentException dersom et av init-kravene feiler.
     */
    fun leggTil(nyHendelse: TilbakekrevingsbehandlingHendelse): TilbakekrevingsbehandlingHendelser {
        return this.copy(
            sorterteHendelser = sorterteHendelser + nyHendelse,
        )
    }

    fun hentDokumenterForHendelseId(hendelseId: HendelseId): DokumentHendelseSerie? {
        return tilhørendeOgSorterteDokumentHendelser.hentSerieForRelatertHendelse(hendelseId)
    }

    fun hentSerieFor(behandlingsid: TilbakekrevingsbehandlingId): TilbakekrevingbehandlingsSerie =
        TilbakekrevingbehandlingsSerie(
            sakId = sakId,
            behandlingsId = behandlingsid,
            hendelser = sorterteHendelser.filter { it.id == behandlingsid },
        )

    fun hentKravrunnlag(kravgrunnlagHendelseId: HendelseId): Kravgrunnlag? {
        return kravgrunnlagPåSak.hentKravgrunnlagDetaljerPåSakHendelseForHendelseId(kravgrunnlagHendelseId)?.kravgrunnlag
    }

    fun hentBehandlingForKravgrunnlag(kravgrunnlagHendelseId: HendelseId): Tilbakekrevingsbehandling? =
        this.currentState.behandlinger.singleOrNullOrThrow {
            it.kravgrunnlag?.hendelseId == kravgrunnlagHendelseId && it.erÅpen()
        }

    /**
     * Henter det siste utestående kravgrunnlaget, dersom det finnes et kravgrunnlag og det ikke er avsluttet.
     * Det er kun det siste mottatte kravgrunnlaget som kan være utestående.
     * Et kravgrunnlag er avsluttet dersom vi har iverksatt en tilbakekrevingsbehandling eller kravgrunnlaget har blitt avsluttet på annen måte (statuser fra oppdrag).
     * Merk at et kravgrunnlag vil være utestående helt til behandlingen er iverksatt eller det er overskrevet av en nyere status eller et nyere kravgrunnlag.
     *
     * NB: Oppdateringer kommer kun fra oppdrag, så derfor vil kun det siste kravgrunnlaget på saken være interessant/gyldig. MAO statuser blir kun endret fra oppdrag.
     * Implikasjonen av dette er at endringer på kravgrunnlag kommer kun via oppdrag, så vi kan ha mange kravgrunnlag for samme id men kun en åpen ad gangen.
     * @return null dersom det ikke finnet et kravgrunnlag eller kravgrunnlaget ikke er utestående.
     */
    fun hentUteståendeKravgrunnlag(): Kravgrunnlag? {
        val sisteKravgrunnlag = kravgrunnlagPåSak.hentSisteKravgrunnlag() ?: return null

        if (sisteKravgrunnlag.erAvsluttet()) return null

        if (this.currentState.behandlinger.filterIsInstance<IverksattTilbakekrevingsbehandling>()
                .any { it.kravgrunnlag == sisteKravgrunnlag }
        ) {
            return null
        }

        return sisteKravgrunnlag
    }

    companion object {

        fun empty(sakId: UUID, fnr: Fnr, saksnummer: Saksnummer, clock: Clock): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                fnr = fnr,
                saksnummer = saksnummer,
                clock = clock,
                sorterteHendelser = emptyList(),
                kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(emptyList()),
                tilhørendeOgSorterteDokumentHendelser = DokumentHendelser.empty(sakId),
            )
        }

        fun create(
            sakId: UUID,
            clock: Clock,
            fnr: Fnr,
            saksnummer: Saksnummer,
            hendelser: List<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: KravgrunnlagPåSakHendelser,
            dokumentHendelser: DokumentHendelser,
        ): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                fnr = fnr,
                saksnummer = saksnummer,
                clock = clock,
                sorterteHendelser = hendelser.sorted(),
                kravgrunnlagPåSak = kravgrunnlagPåSak,
                tilhørendeOgSorterteDokumentHendelser = dokumentHendelser,
            )
        }
    }
}
