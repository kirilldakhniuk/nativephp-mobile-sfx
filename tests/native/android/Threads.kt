package android.os
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
class Looper(val executor: ExecutorService)
class HandlerThread(name: String) {
    val looper = Looper(Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } })
    fun start() {}
    fun quitSafely(): Boolean { looper.executor.shutdown(); return true }
}
class Handler(private val looper: Looper) {
    fun post(task: Runnable): Boolean { looper.executor.execute(task); return true }
}
