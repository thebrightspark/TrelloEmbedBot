package brightspark.trelloembedbot

import com.google.common.cache.CacheBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

@Component
class RequestLimiter(
        @Value("\${rateLimit.maxPermits:100}")
        private val maxPermits: Int,
        @Value("\${rateLimit.timePeriod:10}")
        private val timePeriod: Long,
        @Value("\${rateLimit.timeUnit:seconds}")
        private val timeUnitName: String) {

    private val timeUnit = TimeUnit.valueOf(timeUnitName.toUpperCase(Locale.ROOT))
    private var id: Long = 0
    private val assignedPermits = CacheBuilder.newBuilder().expireAfterWrite(timePeriod, timeUnit).build<Long, Long>()

    fun acquire() : Long {
        var nextFreeTime = 0L
        val curTime = System.currentTimeMillis()
        val timeLessPeriod = curTime - timeUnit.toMillis(timePeriod)
        val count = AtomicInteger(0)
        val permits = assignedPermits.asMap().values.filter { it >= timeLessPeriod && count.incrementAndGet() > 0 }.toList()
        if (count.get() >= maxPermits)
            //Hit max permits - return the time to wait until next free permit
            nextFreeTime = max(nextFreeTime, permits[0] - timeLessPeriod) + 1

        if (nextFreeTime == 0L)
            //Below max permits - allow
            assignedPermits.put(++id, curTime)
        return nextFreeTime
    }
}