import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
fun main() {
    val mark = TimeSource.Monotonic.markNow()
    while (true) {
        println("Elapsed time: ${mark.elapsedNow()}")
        Thread.sleep(5_000)
    }
}
