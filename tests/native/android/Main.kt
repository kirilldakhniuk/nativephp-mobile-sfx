import android.content.Context
import android.media.SoundPool
import com.stupidbrains.sfx.SfxFunctions
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val context = Context()
    val preload = SfxFunctions.Preload(context)
    val play = SfxFunctions.Play(context)
    val unload = SfxFunctions.Unload(context)
    fun path(name: String) = File(args[0], name).apply { writeText("fixture") }.absolutePath
    fun load(name: String, path: Any?): Map<String, Any> =
        preload.execute(mapOf("clips" to JSONObject(mapOf(name to path))))
    fun plays(name: String) = play.execute(mapOf("name" to name))["success"] == true
    fun failed(result: Map<String, Any>, name: String) { check(result["failed"] == listOf(name)) { result } }

    check(load("cue", path("good"))["loaded"] == listOf("cue"))
    val pool = SoundPool.instances.last()
    check(plays("cue"))
    val original = pool.played
    for (invalid in listOf("", "   ", null, 42, "relative.wav", "/missing.wav", args[0])) {
        failed(load("cue", invalid), "cue")
        check(plays("cue") && pool.played == original)
    }
    failed(load("cue", path("corrupt")), "cue")
    check(plays("cue") && pool.played == original)
    check(pool.unloaded.contains(2))
    failed(load("cue", path("zero")), "cue")
    check(plays("cue") && pool.played == original)
    failed(load("cue", path("timeout")), "cue")
    val timedOut = 3
    check(pool.unloaded.contains(timedOut))
    check(plays("cue") && pool.played == original)

    val workers = Executors.newFixedThreadPool(4)
    try {
        SoundPool.started = CountDownLatch(1)
        val replacement = workers.submit<Map<String, Any>> { load("cue", path("slow")) }
        check(SoundPool.started.await(2, TimeUnit.SECONDS))
        pool.complete(timedOut, 0) // Must not satisfy the new batch's latch.
        check(replacement.get(2, TimeUnit.SECONDS)["loaded"] == listOf("cue"))
        check(plays("cue") && pool.played != original)
        check(pool.unloaded.contains(original))

        SoundPool.started = CountDownLatch(1)
        val loading = workers.submit<Map<String, Any>> { load("other", path("slow")) }
        check(SoundPool.started.await(2, TimeUnit.SECONDS))
        val releasing = workers.submit<Map<String, Any>> { unload.execute(emptyMap()) }
        check(loading.get(2, TimeUnit.SECONDS)["loaded"] == listOf("other"))
        check(releasing.get(2, TimeUnit.SECONDS)["success"] == true)
        check(pool.released && !plays("cue"))

        val loads = (1..20).map { n -> workers.submit<Map<String, Any>> { load("clip$n", path("good$n")) } }
        loads.forEach { check((it.get(3, TimeUnit.SECONDS)["failed"] as List<*>).isEmpty()) }
        (1..20).forEach { check(plays("clip$it")) }
        check(SoundPool.instances.size == 2)
        unload.execute(emptyMap())
    } finally { workers.shutdownNow() }
    println("Android regression checks passed: validation, replacement, timeout cleanup, late callbacks, concurrent load/play/unload")
}
