package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal data class SjekkPlan(
    val sak: SakInfo,
    val sjekkpunkter: List<Sjekkpunkt>,
)

internal data class Sjekkpunkt(
    val fnr: Fnr,
    val tilhører: FradragTilhører,
    val fradragstype: Fradragstype,
    val ytelse: EksternYtelse,
    val lokaltBeløp: Double?,
)

internal data class LøpendeSakForMåned(
    val sak: SakInfo,
    val gjeldendeMånedsutbetaling: Int,
)

internal data class SjekkgrunnlagForSak(
    val sjekkplan: SjekkPlan,
    val gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    val gjeldendeMånedsutbetaling: Int,
)

internal enum class EpsKategori {
    UNDER_SEKSTISYV,
    SEKSTISYV_ELLER_ELDRE,
}

internal enum class EksternYtelse(val ytelseNavn: String) {
    AAP("AAP"),
    PESYS_ALDER("Pesys alder"),
    PESYS_UFORE("Pesys uføre"),
}

internal sealed interface EksterntOppslag {
    data class Funnet(val beløp: Double) : EksterntOppslag
    data object IngenTreff : EksterntOppslag
    data class Feil(val grunn: String) : EksterntOppslag
}

/*
hvorfor ikke on demand slå opp? trenger vi det flere steder? sjekk opp
 */
internal data class EksterneOppslagsresultater(
    val aap: Map<Fnr, EksterntOppslag>,
    val pesysAlder: Map<Fnr, EksterntOppslag>,
    val pesysUføre: Map<Fnr, EksterntOppslag>,
) {
    fun finnYtelseForPerson(sjekkpunkt: Sjekkpunkt): EksterntOppslag {
        return when (sjekkpunkt.ytelse) {
            EksternYtelse.AAP -> aap[sjekkpunkt.fnr]
            EksternYtelse.PESYS_ALDER -> pesysAlder[sjekkpunkt.fnr]
            EksternYtelse.PESYS_UFORE -> pesysUføre[sjekkpunkt.fnr]
        } ?: throw ManglerLagretOppslagsresultatException(sjekkpunkt)
    }
}

internal class ManglerLagretOppslagsresultatException(
    sjekkpunkt: Sjekkpunkt,
) : IllegalStateException(
    "Mangler lagret oppslagsresultat for ytelse=${sjekkpunkt.ytelse}, fnr=${sjekkpunkt.fnr}",
)

internal data class FradragssjekkResultat(
    val saksresultater: List<FradragssjekkSakResultat> = emptyList(),
)

internal data class FradragssjekkKjøring(
    val id: UUID,
    val dato: LocalDate,
    val dryRun: Boolean,
    val status: FradragssjekkKjøringStatus,
    val opprettet: Instant,
    val ferdigstilt: Instant,
    val resultat: FradragssjekkResultat = FradragssjekkResultat(),
    val feilmelding: String? = null,
)

internal enum class FradragssjekkKjøringStatus {
    FULLFØRT,
    FEILET,
}

internal sealed interface FradragssjekkSakResultat {
    val sakId: UUID
    val sakstype: Sakstype
    val sjekkPunkter: List<Sjekkpunkt>

    val status: FradragssjekkSakStatus
        get() = when (this) {
            is IngenAvvik -> FradragssjekkSakStatus.INGEN_AVVIK
            is KunObservasjon -> FradragssjekkSakStatus.KUN_OBSERVASJON
            is EksternFeil -> FradragssjekkSakStatus.EKSTERN_FEIL
            is OppgaveIkkeOpprettetDryRun -> FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN
            is OppgaveOpprettet -> FradragssjekkSakStatus.OPPGAVE_OPPRETTET
            is OppgaveopprettelseFeilet -> FradragssjekkSakStatus.OPPGAVEOPPRETTELSE_FEILET
            is Invariantbrudd -> FradragssjekkSakStatus.INVARIANTBRUDD
        }

    fun harOpprettetOppgave(): Boolean = this is OppgaveOpprettet

    data class IngenAvvik(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
    ) : FradragssjekkSakResultat

    data class KunObservasjon(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
        val observasjoner: List<Fradragsfunn.Observasjon> = emptyList(),
    ) : FradragssjekkSakResultat

    data class EksternFeil(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
        val eksterneFeil: List<EksternFeilPåSjekkpunkt> = emptyList(),
    ) : FradragssjekkSakResultat

    data class OppgaveIkkeOpprettetDryRun(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
        val oppgaveAvvik: List<Fradragsfunn.Oppgaveavvik> = emptyList(),
        val observasjoner: List<Fradragsfunn.Observasjon> = emptyList(),
    ) : FradragssjekkSakResultat

    data class OppgaveOpprettet(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
        val oppgaveAvvik: List<Fradragsfunn.Oppgaveavvik> = emptyList(),
        val observasjoner: List<Fradragsfunn.Observasjon> = emptyList(),
        val opprettetOppgave: OppgaveopprettelseResultat.Opprettet,
    ) : FradragssjekkSakResultat

    data class OppgaveopprettelseFeilet(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
        val oppgaveAvvik: List<Fradragsfunn.Oppgaveavvik> = emptyList(),
        val observasjoner: List<Fradragsfunn.Observasjon> = emptyList(),
        val mislykketOppgaveopprettelse: MislykketOppgaveopprettelse,
    ) : FradragssjekkSakResultat

    data class Invariantbrudd(
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val sjekkPunkter: List<Sjekkpunkt> = emptyList(),
        val feilmelding: String? = null,
    ) : FradragssjekkSakResultat
}

internal enum class FradragssjekkSakStatus {
    INGEN_AVVIK,
    KUN_OBSERVASJON,
    EKSTERN_FEIL,
    OPPGAVE_IKKE_OPPRETTET_DRY_RUN,
    OPPGAVE_OPPRETTET,
    OPPGAVEOPPRETTELSE_FEILET,
    INVARIANTBRUDD,
    ;

    fun harOpprettetOppgave(): Boolean = this == OPPGAVE_OPPRETTET
}

internal data class EksternFeilPåSjekkpunkt(
    val sjekkpunkt: List<Sjekkpunkt> = emptyList(),
    val grunn: String,
)

internal data class FradragstypeData(
    val kategori: Fradragstype.Kategori,
    val beskrivelse: String? = null,
) {
    fun tilDomain(): Fradragstype = Fradragstype.from(kategori = kategori, beskrivelse = beskrivelse)

    companion object {
        fun fraDomain(
            fradragstype: Fradragstype,
        ): FradragstypeData {
            return FradragstypeData(
                kategori = fradragstype.kategori,
                beskrivelse = (fradragstype as? Fradragstype.Annet)?.beskrivelse,
            )
        }
    }
}

internal sealed interface Avviksvurdering {
    data object IngenDiff : Avviksvurdering
    data class Diff(val avvik: List<Fradragsfunn>) : Avviksvurdering
}

internal sealed interface Fradragsfunn {
    data class Oppgaveavvik(
        val kode: OppgaveConfig.Fradragssjekk.AvvikKode,
        val oppgavetekst: String,
        val fradragstype: FradragstypeData? = null,
    ) : Fradragsfunn

    data class Observasjon(
        val kode: Observasjonskode,
        val loggtekst: String,
    ) : Fradragsfunn
}

internal enum class Observasjonskode {
    INSIGNIFIKANT_BELOEPSDIFFERANSE,
}

internal data class MislykketOppgaveopprettelse(
    val sakId: UUID,
    val avvikskoder: List<OppgaveConfig.Fradragssjekk.AvvikKode>,
)

internal sealed interface OppgaveopprettelseResultat {
    data class Opprettet(val oppgaveId: OppgaveId, val sakId: UUID) : OppgaveopprettelseResultat
    data class Feilet(val feil: MislykketOppgaveopprettelse) : OppgaveopprettelseResultat
}
