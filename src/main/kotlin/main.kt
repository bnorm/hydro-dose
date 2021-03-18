import com.pi4j.Pi4J
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
fun main() {
    val pi4j = Pi4J.newAutoContext()
    try {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            pi4j.describe().print(System.out)

            repeat(24) {
                println("Elapsed time: ${mark.elapsedNow()}")
                Thread.sleep(5_000)
            }
        }
    } finally {
        pi4j.shutdown()
    }
}
