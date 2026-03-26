package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
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

internal enum class EpsKategori {
    UNDER_SEKSTISYV,
    SEKSTISYV_ELLER_ELDRE,
}

internal enum class EksternYtelse(val kildeNavn: String) {
    AAP("AAP"),
    PESYS_ALDER("Pesys alder"),
    PESYS_UFORE("Pesys uføre"),
}

internal sealed interface EksterntOppslag {
    data class Funnet(val beløp: Double) : EksterntOppslag
    data object IngenTreff : EksterntOppslag
    data class Feil(val grunn: String) : EksterntOppslag
}

internal data class EksterneOppslag(
    val aap: Map<Fnr, EksterntOppslag>,
    val pesysAlder: Map<Fnr, EksterntOppslag>,
    val pesysUføre: Map<Fnr, EksterntOppslag>,
) {
    fun hentOppslag(sjekkpunkt: Sjekkpunkt): EksterntOppslag? {
        return when (sjekkpunkt.ytelse) {
            EksternYtelse.AAP -> aap[sjekkpunkt.fnr]
            EksternYtelse.PESYS_ALDER -> pesysAlder[sjekkpunkt.fnr]
            EksternYtelse.PESYS_UFORE -> pesysUføre[sjekkpunkt.fnr]
        }
    }
}

internal data class FradragssjekkResultat(
    val vurderteSaker: Int = 0,
    val sakerMedAvvik: Int = 0,
    val opprettedeOppgaver: Int = 0,
    val hoppetOverPåGrunnAvEksternFeil: Int = 0,
    val mislykkedeOppgaveopprettelser: List<MislykketOppgaveopprettelse> = emptyList(),
    val sakerInsignifikantDifferanseForOppgave: List<Fradragsfunn.Observasjon> = emptyList(),
) {
    operator fun plus(other: FradragssjekkResultat): FradragssjekkResultat {
        return FradragssjekkResultat(
            vurderteSaker = vurderteSaker + other.vurderteSaker,
            sakerMedAvvik = sakerMedAvvik + other.sakerMedAvvik,
            opprettedeOppgaver = opprettedeOppgaver + other.opprettedeOppgaver,
            hoppetOverPåGrunnAvEksternFeil = hoppetOverPåGrunnAvEksternFeil + other.hoppetOverPåGrunnAvEksternFeil,
            mislykkedeOppgaveopprettelser = mislykkedeOppgaveopprettelser + other.mislykkedeOppgaveopprettelser,
        )
    }

    fun registrerSakMedAvvik(): FradragssjekkResultat = copy(sakerMedAvvik = sakerMedAvvik + 1)

    fun registrerOpprettetOppgave(): FradragssjekkResultat = copy(opprettedeOppgaver = opprettedeOppgaver + 1)

    fun registrerHoppetOverPåGrunnAvEksternFeil(): FradragssjekkResultat {
        return copy(hoppetOverPåGrunnAvEksternFeil = hoppetOverPåGrunnAvEksternFeil + 1)
    }

    fun registrerMislykketOppgaveopprettelse(
        feil: MislykketOppgaveopprettelse,
    ): FradragssjekkResultat {
        return copy(mislykkedeOppgaveopprettelser = mislykkedeOppgaveopprettelser + feil)
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
    data object Opprettet : OppgaveopprettelseResultat
    data class Feilet(val feil: MislykketOppgaveopprettelse) : OppgaveopprettelseResultat
}
