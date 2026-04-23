package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.util.UUID

internal class FradragssjekkOppsummeringTest {

    @Test
    fun `oppsummerer saker som gir oppgavegrunnlag per sakstype og fradragstype inkludert dry run`() {
        val brukerFradrag = FradragstypeData(Fradragstype.Kategori.Alderspensjon)
        val epsFradrag = FradragstypeData(Fradragstype.Kategori.Arbeidsavklaringspenger)
        val saksresultater = listOf(
            opprettetSakResultat(
                sakId = UUID.randomUUID(),
                oppgaveGrunnlag = listOf(
                    oppgavegrunnlag(
                        fradragstype = brukerFradrag,
                    ),
                ),
            ),
            opprettetSakResultat(
                sakId = UUID.randomUUID(),
                oppgaveGrunnlag = listOf(
                    oppgavegrunnlag(
                        fradragstype = brukerFradrag,
                    ),
                    oppgavegrunnlag(
                        fradragstype = epsFradrag,
                    ),
                ),
            ),
            FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun(
                sakId = UUID.randomUUID(),
                sakstype = Sakstype.ALDER,
                sjekkPunkter = emptyList(),
                oppgaveGrunnlag = listOf(
                    oppgavegrunnlag(
                        fradragstype = epsFradrag,
                    ),
                ),
            ),
        )

        lagFradragssjekkOppsummering(saksresultater) shouldBe FradragssjekkOppsummering(
            nøkkeltall = mapOf(
                FradragssjekkSakStatus.OPPGAVE_OPPRETTET to 2,
                FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN to 1,
            ),
            antallOppgaver = 3,
            oppgaverPerSakstype = listOf(
                FradragssjekkSakstypeStatistikk(
                    sakstype = Sakstype.ALDER,
                    antallOppgaver = 3,
                    oppgaverPerFradrag = listOf(
                        FradragssjekkFradragStatistikk(
                            fradragstype = brukerFradrag.kategori.name,
                            beskrivelse = brukerFradrag.beskrivelse,
                            antallOppgaver = 2,
                        ),
                        FradragssjekkFradragStatistikk(
                            fradragstype = epsFradrag.kategori.name,
                            beskrivelse = epsFradrag.beskrivelse,
                            antallOppgaver = 2,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `skiller eksplisitt mellom opprettet oppgave og dry run i nøkkeltall`() {
        val fradrag = FradragstypeData(Fradragstype.Kategori.Alderspensjon)
        val saksresultater = listOf(
            opprettetSakResultat(
                sakId = UUID.randomUUID(),
                oppgaveGrunnlag = listOf(oppgavegrunnlag(fradragstype = fradrag)),
            ),
            FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun(
                sakId = UUID.randomUUID(),
                sakstype = Sakstype.ALDER,
                sjekkPunkter = emptyList(),
                oppgaveGrunnlag = listOf(oppgavegrunnlag(fradragstype = fradrag)),
            ),
        )

        lagFradragssjekkOppsummering(saksresultater) shouldBe FradragssjekkOppsummering(
            nøkkeltall = mapOf(
                FradragssjekkSakStatus.OPPGAVE_OPPRETTET to 1,
                FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN to 1,
            ),
            antallOppgaver = 2,
            oppgaverPerSakstype = listOf(
                FradragssjekkSakstypeStatistikk(
                    sakstype = Sakstype.ALDER,
                    antallOppgaver = 2,
                    oppgaverPerFradrag = listOf(
                        FradragssjekkFradragStatistikk(
                            fradragstype = fradrag.kategori.name,
                            beskrivelse = fradrag.beskrivelse,
                            antallOppgaver = 2,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun opprettetSakResultat(
        sakId: UUID,
        oppgaveGrunnlag: List<Fradragsfunn.Oppgavegrunnlag>,
    ) = FradragssjekkSakResultat.OppgaveOpprettet(
        sakId = sakId,
        sakstype = Sakstype.ALDER,
        sjekkPunkter = emptyList(),
        oppgaveGrunnlag = oppgaveGrunnlag,
        opprettetOppgave = OppgaveopprettelseResultat.Opprettet(
            oppgaveId = OppgaveId("12345"),
            sakId = sakId,
        ),
    )

    private fun oppgavegrunnlag(
        fradragstype: FradragstypeData,
    ) = Fradragsfunn.Oppgavegrunnlag(
        kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
        oppgavetekst = "Oppgavegrunnlag",
        fradragstype = fradragstype,
    )
}
