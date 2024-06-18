package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselMetaInfo
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.util.UUID

/**
 * Den vanlige flyten er:
 *  1. Opprett
 *  1. Forhåndsvarsle
 *  1. Vurder
 *  1. Fatt vedtaksbrev
 *  1. Send til attestering
 *  1. Iverksett eller underkjenn
 *
 */
sealed interface UnderBehandling :
    KanOppdatereKravgrunnlag,
    KanOppdatereVedtaksbrev,
    KanVurdere,
    KanForhåndsvarsle,
    KanOppdatereNotat,
    UnderBehandlingEllerTilAttestering {

    override val vurderingerMedKrav: VurderingerMedKrav?
    val erUnderkjent: Boolean

    override fun erÅpen() = true

    /**
     * Kan kun gå fra [OpprettetTilbakekrevingsbehandling] til [Påbegynt], men ikke tilbake til [OpprettetTilbakekrevingsbehandling].
     * Lovelige overganger til:
     *  * [AvbruttTilbakekrevingsbehandling]
     *  * [Påbegynt]
     *  * [Utfylt]
     *
     *  Forrige steg, kan bare være [OpprettetTilbakekrevingsbehandling] eller [UnderBehandling]
     */
    data class Påbegynt(
        val forrigeSteg: KanEndres,
        override val hendelseId: HendelseId,
        override val versjon: Hendelsesversjon,
        override val vurderingerMedKrav: VurderingerMedKrav? = forrigeSteg.vurderingerMedKrav,
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = forrigeSteg.forhåndsvarselsInfo,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = forrigeSteg.vedtaksbrevvalg,
        override val kravgrunnlag: Kravgrunnlag = forrigeSteg.kravgrunnlag,
        override val erKravgrunnlagUtdatert: Boolean = forrigeSteg.erKravgrunnlagUtdatert,
        override val notat: NonBlankString? = forrigeSteg.notat,
    ) : UnderBehandling, KanEndres by forrigeSteg {

        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

        override val erUnderkjent = attesteringer.erUnderkjent()

        override fun erÅpen() = true

        fun erVurdert(): Boolean = vurderingerMedKrav != null

        override fun oppdaterNotat(
            notat: NonBlankString?,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ): UnderBehandling = this.copy(hendelseId = hendelseId, versjon = versjon, notat = notat)

        /**
         * Siden vedtaksbrevet er avhengig av månedsperiodene krever vi at månedsvurderingene er utfylt først.
         * @throws IllegalStateException Dersom [vurderingerMedKrav] ikke er utfylt.
         */
        override fun oppdaterVedtaksbrev(
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ): Utfylt {
            return if (vurderingerMedKrav == null) {
                // TODO jah: Kan vurdere å gjøre Påbegynt til sealed og dele den opp i med og uten brev.
                //  Alternativt kan denne returnere UnderBehandling også aksepterer vi at vi at vi kan oppdatere vedtaksbrevinnholdet uten [vurderingerMedKrav]?
                throw IllegalStateException("Må gjøre månedsvurderingene før man tar stilling til vedtaksbrev")
            } else {
                Utfylt(
                    forrigeSteg = this,
                    vurderingerMedKrav = vurderingerMedKrav,
                    hendelseId = hendelseId,
                    vedtaksbrevvalg = vedtaksbrevvalg,
                    attesteringer = forrigeSteg.attesteringer,
                    forhåndsvarselsInfo = forhåndsvarselsInfo,
                    versjon = versjon,
                    notat = notat,
                )
            }
        }

        override fun leggTilForhåndsvarselDokumentId(
            dokumentId: UUID,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            hendelsesTidspunkt: Tidspunkt,
        ): Påbegynt {
            return this.copy(
                forhåndsvarselsInfo = this.forhåndsvarselsInfo.plus(
                    ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt),
                ),
                hendelseId = hendelseId,
                versjon = versjon,
            )
        }

        override fun leggTilVurderinger(
            månedsvurderinger: VurderingerMedKrav,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            vurderingerMedKrav = månedsvurderinger,
            hendelseId = hendelseId,
            versjon = versjon,
        )
    }

    /**
     * Når vi først er utfylt, kan vi ikke gå tilbake til påbegynt (med unntak av oppdatering av kravgrunnlag).
     * Lovelige overganger er:
     *   * [AvbruttTilbakekrevingsbehandling]
     *   * [TilbakekrevingsbehandlingTilAttestering]
     *   * [UnderBehandling.Påbegynt] (dersom kravgrunnlaget oppdateres og vi må vurdere på nytt)
     *
     *   @param forhåndsvarselsInfo Vi støtter og legge til nye forhåndsvarslinger selvom tilstanden er [Utfylt]
     *   @property erUnderkjent Dersom denne har vært til attestering, vil den implisitt være underkjent nå.
     */
    data class Utfylt(
        val forrigeSteg: UnderBehandlingEllerTilAttestering,
        override val hendelseId: HendelseId,
        override val versjon: Hendelsesversjon,
        override val vurderingerMedKrav: VurderingerMedKrav,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        override val attesteringer: Attesteringshistorikk = forrigeSteg.attesteringer,
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = forrigeSteg.forhåndsvarselsInfo,
        override val notat: NonBlankString? = forrigeSteg.notat,
    ) : UnderBehandling, KanEndres, UnderBehandlingEllerTilAttestering by forrigeSteg, ErUtfylt {

        constructor(
            forrigeSteg: TilbakekrevingsbehandlingTilAttestering,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) : this(
            forrigeSteg = forrigeSteg,
            hendelseId = hendelseId,
            versjon = versjon,
            vurderingerMedKrav = forrigeSteg.vurderingerMedKrav,
            vedtaksbrevvalg = forrigeSteg.vedtaksbrevvalg,
        )

        override val erUnderkjent = attesteringer.erUnderkjent()

        override fun oppdaterNotat(
            notat: NonBlankString?,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ): UnderBehandling = this.copy(hendelseId = hendelseId, versjon = versjon, notat = notat)

        override fun leggTilVurderinger(
            månedsvurderinger: VurderingerMedKrav,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            hendelseId = hendelseId,
            vurderingerMedKrav = månedsvurderinger,
            versjon = versjon,
        )

        override fun oppdaterVedtaksbrev(
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            hendelseId = hendelseId,
            vedtaksbrevvalg = vedtaksbrevvalg,
            versjon = versjon,
        )

        override fun leggTilForhåndsvarselDokumentId(
            dokumentId: UUID,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            hendelsesTidspunkt: Tidspunkt,
        ) = this.copy(
            hendelseId = hendelseId,
            forhåndsvarselsInfo = this.forhåndsvarselsInfo.plus(
                ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt),
            ),
            versjon = versjon,
        )

        override fun erÅpen() = true
    }
}
