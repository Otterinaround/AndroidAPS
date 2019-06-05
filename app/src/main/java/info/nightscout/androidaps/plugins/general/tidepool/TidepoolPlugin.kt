package info.nightscout.androidaps.plugins.general.tidepool

import android.text.Html
import com.squareup.otto.Subscribe
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.general.tidepool.comm.Session
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
import info.nightscout.androidaps.plugins.general.tidepool.utils.RateLimit
import info.nightscout.androidaps.receivers.ChargingStateReceiver
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.T
import org.slf4j.LoggerFactory
import java.util.*

object TidepoolPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginName(R.string.tidepool)
        .shortName(R.string.tidepool_shortname)
        .fragmentClass(TidepoolJavaFragment::class.java.name)
        .preferencesId(R.xml.pref_tidepool)
        .description(R.string.description_tidepool)
) {
    private val log = LoggerFactory.getLogger(L.TIDEPOOL)
    private var wifiConnected = false

    var session: Session? = null

    private val listLog = ArrayList<EventTidepoolStatus>()
    internal var textLog = Html.fromHtml("")

    var paused: Boolean = false
    internal var autoscroll: Boolean = false

    var status = ""
    override fun onStart() {
        MainApp.bus().register(this)
        super.onStart()
    }

    override fun onStop() {
        MainApp.bus().unregister(this)
        super.onStop()
    }

    fun doUpload() {
        if (session == null)
            session = TidepoolUploader.doLogin()
        else TidepoolUploader.doUpload(session!!)
    }

    @Suppress("UNUSED_PARAMETER")
    @Subscribe
    fun onStatusEvent(ev: EventNewBG) {
        if (enabled()
                && (!SP.getBoolean(R.string.key_tidepool_only_while_charging, false) || ChargingStateReceiver.isCharging())
                && (!SP.getBoolean(R.string.key_tidepool_only_while_unmetered, false) || wifiConnected)
                && RateLimit.ratelimit("tidepool-new-data-upload", T.mins(4).secs().toInt()))
            doUpload()
    }

    @Suppress("UNUSED_PARAMETER")
    @Subscribe
    fun onEventTidepoolDoUpload(ev: EventTidepoolDoUpload) {
        doUpload()
    }

    @Suppress("UNUSED_PARAMETER")
    @Subscribe
    fun onEventTidepoolResetData(ev: EventTidepoolResetData) {
        if (session == null)
            session = TidepoolUploader.doLogin()
        if (session != null) {
            TidepoolUploader.deleteDataSet(session!!)
            SP.putLong(R.string.key_tidepool_last_end, 0)
            TidepoolUploader.doLogin()
        }
    }

    @Subscribe
    fun onEventNetworkChange(ev: EventNetworkChange) {
        wifiConnected = ev.wifiConnected
    }

    fun enabled(): Boolean {
        return isEnabled(PluginType.GENERAL) && SP.getBoolean(R.string.key_cloud_storage_tidepool_enable, false)
    }

}