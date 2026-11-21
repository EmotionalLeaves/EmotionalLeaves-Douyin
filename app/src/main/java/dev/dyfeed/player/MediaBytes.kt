package dev.dyfeed.player

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.ConcurrentHashMap

/**
 * When bytes last arrived from the network for each video address. The player's "nothing from the
 * server" check asks this instead of guessing from the buffer, which stays empty on a slow phone
 * even while data is coming in (the test phone gave up on 33 videos that way, mirror after mirror).
 */
@OptIn(UnstableApi::class)
object MediaBytes : TransferListener {
    private val last = ConcurrentHashMap<String, Long>()

    private fun key(uri: Uri): String = (uri.host.orEmpty() + uri.path.orEmpty())

    /** Whether any bytes for [url] have arrived since [since] (elapsedRealtime). */
    fun arrivedSince(url: String, since: Long): Boolean =
        (last[key(Uri.parse(url))] ?: 0L) >= since

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (bytesTransferred <= 0) return
        if (last.size > MAX) last.clear()
        last[key(dataSpec.uri)] = SystemClock.elapsedRealtime()
    }

    private const val MAX = 500
}
