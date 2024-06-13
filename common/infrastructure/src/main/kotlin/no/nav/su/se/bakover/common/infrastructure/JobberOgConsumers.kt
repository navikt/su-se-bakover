package no.nav.su.se.bakover.common.infrastructure

import no.nav.su.se.bakover.common.infrastructure.consumer.StoppableConsumer
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob

data class JobberOgConsumers(
    private val jobs: Iterable<StoppableJob>,
    private val consumers: Iterable<StoppableConsumer>,
) {
    fun stop() {
        jobs.forEach { it.stop() }
        consumers.forEach { it.stop() }
    }

    operator fun plus(other: JobberOgConsumers): JobberOgConsumers {
        return JobberOgConsumers(
            jobs = jobs + other.jobs,
            consumers = consumers + other.consumers,
        )
    }
}
