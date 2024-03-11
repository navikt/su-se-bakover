package no.nav.su.se.bakover.database.jobcontext

import no.nav.su.se.bakover.common.domain.job.JobContext

interface JobContextDb {
    fun id(): String
    fun toJson(): String
    fun toDomain(): JobContext
}
