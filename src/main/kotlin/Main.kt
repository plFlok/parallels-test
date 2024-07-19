package org.example

import java.lang.Integer.min
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

const val THRESHOLD = 100
const val TEST1_EVENTS_COUNT = THRESHOLD / 2
const val TEST2_EVENTS_COUNT = THRESHOLD * 2

class Report(
    val clazz: String,
    val description: String,
    val totalTests: Int,
    val failedTests: Int
)

fun main() {
    val cpuCores = Runtime.getRuntime().availableProcessors()
    val threads = cpuCores
    val executor = Executors.newFixedThreadPool(threads) as ThreadPoolExecutor

    val reports = mutableListOf<Report>()
    reports.add(testLimiter("Эвентов меньше, чем threshold: $TEST1_EVENTS_COUNT при threshold $THRESHOLD", TryIncrement::class, THRESHOLD, executor, TEST1_EVENTS_COUNT, 1000))
    reports.add(testLimiter("Эвентов меньше, чем threshold: $TEST1_EVENTS_COUNT при threshold $THRESHOLD", IncrementAndAuthorize::class, THRESHOLD, executor, TEST1_EVENTS_COUNT, 1000))
    reports.add(testLimiter("Эвентов больше, чем threshold: $TEST2_EVENTS_COUNT при threshold $THRESHOLD", TryIncrement::class, THRESHOLD, executor, TEST2_EVENTS_COUNT, 1000))
    reports.add(testLimiter("Эвентов больше, чем threshold: $TEST2_EVENTS_COUNT при threshold $THRESHOLD", IncrementAndAuthorize::class, THRESHOLD, executor, TEST2_EVENTS_COUNT, 1000))

    executor.shutdown()


    println("\nОтчёт по всем тестам")
    reports.forEach{
        val emoji = if (it.failedTests == 0) { "✅" } else { "🚨" }
        println("Тест {${it.clazz}} \"${it.description}\".\nВсего тестов: ${it.totalTests}, заваленных: ${it.failedTests} $emoji")
    }

}

fun <T : Limiter> testLimiter(
    testDescriotion: String,
    clazz: KClass<T>,
    threshold: Int,
    executor: ExecutorService,
    parallelTasksCount: Int,
    testIterations: Int = 100
): Report {
    println("Test $clazz")
    var totalTests = 0
    var failedTests = 0
    repeat(testIterations) { iteration ->
        totalTests++
        val limiter = clazz.primaryConstructor!!.call(threshold)
        val results = mutableListOf<Future<Boolean>>()
        // latch - чтобы таски не начали выполняться, пока все не сформированы.
        // latch.countDown запустит их все ~разом
        val latch = CountDownLatch(1)
        val task = Callable {
            latch.await()
            limiter.hasAccess()
        }
        repeat(parallelTasksCount) {
            results.add(executor.submit(task))
        }
        latch.countDown()
        val allowedEvents = results.map { it.get() }.filter { it }.count()
        val requiredToAllowEvents = min(threshold, parallelTasksCount)
        if (allowedEvents != requiredToAllowEvents) {
            println(
                "Лимитер ${limiter::class} завалил тест #$iteration, ожидается $requiredToAllowEvents одобренных эвентов, но получили $allowedEvents"
            )
            failedTests++
        }
    }
    return Report(clazz.toString(), testDescriotion, totalTests, failedTests)
}
