package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.beregning.BeregningPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import java.util.UUID
import javax.sql.DataSource

internal class TestDataHelper(
    dataSource: DataSource = EmbeddedDatabase.instance()
) {
    private val utbetalingRepo = UtbetalingPostgresRepo(dataSource)
    private val hendelsesloggRepo = HendelsesloggPostgresRepo(dataSource)
    private val beregningRepo = BeregningPostgresRepo(dataSource)
    private val søknadRepo = SøknadPostgresRepo(dataSource)
    private val behandlingRepo = BehandlingPostgresRepo(dataSource)
    private val sakRepo = SakPostgresRepo(dataSource)

    fun insertSak(fnr: Fnr): Sak = SakFactory().nySak(fnr).also { sakRepo.opprettSak(it) }
    fun insertSøknad(sakId: UUID): Søknad = Søknad(
        sakId = sakId,
        id = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build()
    ).also { søknadRepo.opprettSøknad(it) }

    fun insertBehandling(sakId: UUID, søknad: Søknad): NySøknadsbehandling = NySøknadsbehandling(
        sakId = sakId,
        søknadId = søknad.id
    ).also {
        behandlingRepo.opprettSøknadsbehandling(it)
    }

    fun insertBeregning(behandlingId: UUID) = beregningRepo.opprettBeregningForBehandling(
        behandlingId = behandlingId,
        beregning = Beregning(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
    )

    fun hentUtbetaling(utbetalingId: UUID30) = utbetalingRepo.hentUtbetaling(utbetalingId)

    fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg) = hendelsesloggRepo.oppdaterHendelseslogg(hendelseslogg)
}
