package com.stupidbrains.sfx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.nativephp.mobile.bridge.BridgeFunction
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object SfxFunctions {
    private const val MAX_STREAMS = 4
    private const val LOAD_TIMEOUT_SECONDS = 5L

    // All bridge operations share this lock, including calls from embedded PHP webviews.
    // Callbacks use a separate lock and thread so preload can wait without blocking them.
    private val stateLock = ReentrantLock()
    private val completionLock = Any()
    private var pool: SoundPool? = null
    private var callbackThread: HandlerThread? = null
    private val soundIds = mutableMapOf<String, Int>()
    private val activeStreams = mutableMapOf<String, Int>()
    private val pendingLoads = mutableMapOf<Int, PendingLoad>()

    private class PendingLoad(val name: String, val latch: CountDownLatch) {
        var status: Int? = null // Guarded by completionLock.
    }

    private fun ensurePool(): SoundPool {
        pool?.let { return it }
        val thread = HandlerThread("nativephp-sfx-loads").apply { start() }
        val task = FutureTask {
            SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
                .also { soundPool ->
                    soundPool.setOnLoadCompleteListener { source, id, status ->
                        synchronized(completionLock) {
                            // Ignore callbacks from released pools and untracked/timed-out IDs.
                            if (source === pool) {
                                pendingLoads.remove(id)?.let {
                                    it.status = status
                                    it.latch.countDown()
                                }
                            }
                        }
                    }
                }
        }
        Handler(thread.looper).post(task)
        // Finish initialization even if interrupted so an allocated pool is never orphaned.
        var interrupted = false
        try {
            while (true) {
                try {
                    val created = task.get()
                    synchronized(completionLock) { pool = created }
                    callbackThread = thread
                    return created
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } catch (error: Exception) {
            thread.quitSafely()
            throw error
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun report(loaded: List<String>, failed: List<String>): Map<String, Any> =
        mapOf("success" to true, "loaded" to loaded, "failed" to failed)

    class Preload(@Suppress("UNUSED_PARAMETER") context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> = stateLock.withLock {
            val clips: Map<String, Any?> = when (val raw = parameters["clips"]) {
                is JSONObject -> raw.keys().asSequence().associateWith { raw.opt(it) }
                is Map<*, *> -> raw.entries.mapNotNull { (key, value) ->
                    (key as? String)?.let { it to value }
                }.toMap()
                else -> emptyMap()
            }
            val loaded = mutableListOf<String>()
            val failed = mutableListOf<String>()
            val valid = mutableMapOf<String, String>()
            for ((name, value) in clips) {
                val path = value as? String
                if (path.isNullOrBlank() || !File(path).isAbsolute || !File(path).isFile) {
                    failed.add(name)
                } else {
                    valid[name] = path
                }
            }
            if (valid.isEmpty()) return@withLock report(loaded, failed)

            val soundPool = ensurePool()
            val latch = CountDownLatch(valid.size)
            val batch = mutableMapOf<Int, PendingLoad>()
            try {
                for ((name, path) in valid) {
                    // Register while holding the callback lock: even an immediate callback
                    // cannot arrive between load() returning and registration.
                    synchronized(completionLock) {
                        val id = soundPool.load(path, 1)
                        if (id == 0) {
                            failed.add(name)
                            latch.countDown()
                        } else {
                            val pending = PendingLoad(name, latch)
                            batch[id] = pending
                            pendingLoads[id] = pending
                        }
                    }
                }
                try {
                    if (!latch.await(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        Log.e("Sfx.Preload", "Timed out waiting for clips")
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                synchronized(completionLock) {
                    for ((id, pending) in batch) {
                        pendingLoads.remove(id)
                        if (pending.status == 0) {
                            // Commit replacement only after successful decoding.
                            activeStreams.remove(pending.name)?.let { soundPool.stop(it) }
                            soundIds.put(pending.name, id)?.let { soundPool.unload(it) }
                            loaded.add(pending.name)
                        } else {
                            failed.add(pending.name)
                        }
                    }
                }
            } finally {
                synchronized(completionLock) {
                    for ((id, pending) in batch) {
                        pendingLoads.remove(id)
                        if (soundIds[pending.name] != id) soundPool.unload(id)
                    }
                }
            }
            report(loaded, failed)
        }
    }

    class Play(@Suppress("UNUSED_PARAMETER") context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> = stateLock.withLock {
            val name = parameters["name"] as? String ?: return@withLock mapOf("success" to false)
            val soundPool = pool ?: return@withLock mapOf("success" to false)
            val id = soundIds[name] ?: return@withLock mapOf("success" to false)
            activeStreams.remove(name)?.let { soundPool.stop(it) }
            val stream = soundPool.play(id, 1f, 1f, 1, 0, 1f)
            if (stream != 0) activeStreams[name] = stream
            mapOf("success" to (stream != 0))
        }
    }

    class Unload(@Suppress("UNUSED_PARAMETER") context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> = stateLock.withLock {
            val oldPool = synchronized(completionLock) {
                val old = pool
                pool = null
                pendingLoads.clear()
                old
            }
            oldPool?.release()
            callbackThread?.quitSafely()
            callbackThread = null
            soundIds.clear()
            activeStreams.clear()
            mapOf("success" to true)
        }
    }
}
