package android.media
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

class AudioAttributes {
    class Builder {
        fun setUsage(value: Int) = this
        fun setContentType(value: Int) = this
        fun build() = AudioAttributes()
    }
    companion object { const val USAGE_MEDIA = 1; const val CONTENT_TYPE_SONIFICATION = 4 }
}

// Controllable decoder: callbacks can be immediate, delayed, failed, or absent.
class SoundPool {
    companion object {
        val instances = CopyOnWriteArrayList<SoundPool>()
        @Volatile var started = CountDownLatch(1)
    }
    class Builder {
        fun setMaxStreams(value: Int) = this
        fun setAudioAttributes(value: AudioAttributes) = this
        fun build() = SoundPool().also { instances.add(it) }
    }
    private var listener: ((SoundPool, Int, Int) -> Unit)? = null
    val samples = mutableSetOf<Int>()
    val unloaded = mutableSetOf<Int>()
    var released = false
    var played = 0
    private var nextId = 0
    fun setOnLoadCompleteListener(callback: (SoundPool, Int, Int) -> Unit) { listener = callback }
    fun load(path: String, priority: Int): Int {
        check(!released)
        if (File(path).name == "zero") return 0
        val id = ++nextId
        samples.add(id)
        val name = File(path).name
        if (name != "timeout") {
            Thread {
                if (name == "slow") Thread.sleep(200)
                complete(id, if (name == "corrupt") 1 else 0)
            }.start()
        }
        started.countDown()
        return id
    }
    fun complete(id: Int, status: Int) { listener?.invoke(this, id, status) }
    fun unload(id: Int): Boolean { unloaded.add(id); return samples.remove(id) }
    fun stop(id: Int) {}
    fun play(id: Int, left: Float, right: Float, priority: Int, loop: Int, rate: Float): Int {
        check(!released && samples.contains(id)); played = id; return id
    }
    fun release() { check(!released); released = true; samples.clear() }
}
