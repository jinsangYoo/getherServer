package com.neowiz.android.bugs.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobParameters.STOP_REASON_APP_STANDBY
import android.app.job.JobParameters.STOP_REASON_BACKGROUND_RESTRICTION
import android.app.job.JobParameters.STOP_REASON_CANCELLED_BY_APP
import android.app.job.JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW
import android.app.job.JobParameters.STOP_REASON_CONSTRAINT_CHARGING
import android.app.job.JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY
import android.app.job.JobParameters.STOP_REASON_CONSTRAINT_DEVICE_IDLE
import android.app.job.JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW
import android.app.job.JobParameters.STOP_REASON_DEVICE_STATE
import android.app.job.JobParameters.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED
import android.app.job.JobParameters.STOP_REASON_PREEMPT
import android.app.job.JobParameters.STOP_REASON_QUOTA
import android.app.job.JobParameters.STOP_REASON_SYSTEM_PROCESSING
import android.app.job.JobParameters.STOP_REASON_TIMEOUT
import android.app.job.JobParameters.STOP_REASON_UNDEFINED
import android.app.job.JobParameters.STOP_REASON_USER
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.StatFs
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Configuration
import com.neowiz.android.bugs.INotificationChannel
import com.neowiz.android.bugs.R
import com.neowiz.android.bugs.api.appdata.BugsPreference
import com.neowiz.android.bugs.api.appdata.Log
import com.neowiz.android.bugs.api.appdata.LoginInfo
import com.neowiz.android.bugs.api.appdata.SAVE_QUALITY_AAC
import com.neowiz.android.bugs.api.appdata.getBitrate
import com.neowiz.android.bugs.api.appdata.getBitrateQuality
import com.neowiz.android.bugs.api.appdata.getDrmDirectory
import com.neowiz.android.bugs.api.appdata.getImageCacheDirectory
import com.neowiz.android.bugs.api.appdata.getNwiDrmFile
import com.neowiz.android.bugs.api.appdata.isEmptyString
import com.neowiz.android.bugs.api.appdata.isMusicCastPlaying
import com.neowiz.android.bugs.api.appdata.isRadioPlaying
import com.neowiz.android.bugs.api.appdata.makeDirs
import com.neowiz.android.bugs.api.appdata.urlEncode
import com.neowiz.android.bugs.api.base.BugsApi
import com.neowiz.android.bugs.api.base.CACHE_TYPE
import com.neowiz.android.bugs.api.base.checkResponse
import com.neowiz.android.bugs.api.base.safeExecute
import com.neowiz.android.bugs.api.db.BugsDb
import com.neowiz.android.bugs.api.db.TrackFactory
import com.neowiz.android.bugs.api.model.AlbumImageSize
import com.neowiz.android.bugs.api.model.ApiSave
import com.neowiz.android.bugs.api.model.base.AccountTp
import com.neowiz.android.bugs.api.model.meta.Track
import com.neowiz.android.bugs.api.util.Toast
import com.neowiz.android.bugs.api.util.cacheExecute
import com.neowiz.android.bugs.download.SaveService.Companion.DRMDOWNLOADSERVICE_STATUS
import com.neowiz.android.bugs.download.SaveService.Companion.PROGRESS_CHANGED
import com.neowiz.android.bugs.download.SaveService.Companion.SAVE_COMPLETE
import com.neowiz.android.bugs.download.SaveService.Companion.STATUS_CHANGED
import com.neowiz.android.bugs.download.SaveUseJobServiceHelper.Companion.PROGRESS
import com.neowiz.android.bugs.service.util.Decrypt
import com.neowiz.android.bugs.uibase.FROM_NOTIFICATION
import com.neowiz.android.bugs.uibase.KEY_FROM
import java.io.File
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.util.Random
import java.util.Timer
import kotlin.concurrent.schedule

class SaveUseJobService : JobService() {
    private val TAG = javaClass.simpleName

    private var mBugsDb: BugsDb? = null
    private var mStatFs: StatFs? = null
    //    private var mWifiLock: WifiManager.WifiLock? = null
    private var mDownloadRunnable: DownloadRunnable? = null
    private var mDownloader: Downloader? = null
    private var mDownloaderResource: Downloader? = null

    var isDownloading = false
        private set

    private var mDownList =
        LinkedHashMap<Long, DwTrackMeta>()

    var nowTrackID: Long = 0
    var progress = 0
    var nowTrackLoudness : String? = null

    private var mTotalLength: Long = 0

    private val DEF_DW_OK = 10

    private var mCurrentRealQuality: String? = null
    //    private var mConnectMgr: ConnectivityManager? = null
    lateinit var mBugsPref: BugsPreference
    // "3G 설정이 false 이고 네트웍이 3g 이면 다운받지 않는다."
    private val is3gDownlaod: Boolean
        get() {
            val isOnlyWifi = mBugsPref.useWifi
            if (!isOnlyWifi)
                return true

//            val network = mConnectMgr!!.activeNetworkInfo
//            return if (network == null || !network.isConnected) {
//                false
//            } else network.type == ConnectivityManager.TYPE_WIFI
            return true
        }

    val handlerThread = HandlerThread("MyDownloadThread").apply {
        start()
    }
    val handler = Handler(handlerThread.looper)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartJob(params: JobParameters?): Boolean {
        if (is3gDownlaod) {
            getDownList()
            Log.d(TAG, "isValidEnvironment(): ${isValidEnvironment()}")
            if (isValidEnvironment()) {
                val name = "Download Data"
                val importance = NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel(INotificationChannel.DRM_DOWNLOAD_CHANNEL_ID, name, importance)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)

//                postNotification(params = params)
//                postNotificationaaa(params = params)
                postNotification2(params = params)
                mDownloadRunnable?.start(params)
                notifyChange(STATUS_CHANGED)
                return true
            }
            else {
                return false
            }
        }
        else {
            return false
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            params?.let {
                val reason = when (it.getStopReason()) {
                    STOP_REASON_UNDEFINED -> "STOP_REASON_UNDEFINED"
                    STOP_REASON_CANCELLED_BY_APP -> "STOP_REASON_CANCELLED_BY_APP"
                    STOP_REASON_PREEMPT -> "STOP_REASON_PREEMPT"
                    STOP_REASON_TIMEOUT -> "STOP_REASON_TIMEOUT"
                    STOP_REASON_DEVICE_STATE -> "STOP_REASON_DEVICE_STATE"
                    STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW"
                    STOP_REASON_CONSTRAINT_CHARGING -> "STOP_REASON_CONSTRAINT_CHARGING"
                    STOP_REASON_CONSTRAINT_CONNECTIVITY -> "STOP_REASON_CONSTRAINT_CONNECTIVITY"
                    STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "STOP_REASON_CONSTRAINT_DEVICE_IDLE"
                    STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW"
                    STOP_REASON_QUOTA -> "STOP_REASON_QUOTA"
                    STOP_REASON_BACKGROUND_RESTRICTION -> "STOP_REASON_BACKGROUND_RESTRICTION"
                    STOP_REASON_APP_STANDBY -> "STOP_REASON_APP_STANDBY"
                    STOP_REASON_USER -> "STOP_REASON_USER"
                    STOP_REASON_SYSTEM_PROCESSING -> "STOP_REASON_SYSTEM_PROCESSING"
                    STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED"
                    else -> "unknown"
                }
                Log.e(TAG, "getStopReason(): ${reason}")
            }
        }
        mDownloader?.stopDRM()
        mDownloaderResource?.stop()
        stopAction()

        return false
    }

    private val mDelayedStopHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == DEF_DW_OK) {
                val track = msg.obj as Track
                SaveMultiArtistTask(this@SaveUseJobService).execute(track) // 아티스트 추가
                return
            }

            if (isDownloading) {
                return
            }
        }
    }

    private fun isValidEnvironment(): Boolean {
        /**
         * 대상 곡이 있는지 체크
         */
        if (mDownList.size < 1) {
            stopAction()
            return false
        }

        /**
         * 로그인 유무 체크
         */
        if (!LoginInfo.isLogin) {
            stopAction()
            showToast(getString(R.string.save_error_login))
            return false
        }

        /**
         * 3G 다운로드 여부.
         */
        if (!is3gDownlaod) {
            stopAction()
            showToast(getString(R.string.save_3guse_stop))
            return false
        }

        // SD카드 인식유무 체크
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            stopAction()
            showToast(getString(R.string.save_error_no_sdcard))
            return false
        }

        return true
    }

    /**
     * DRM 파일 다운로드
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Synchronized
    private fun download(params: JobParameters?) {
        var meta: DwTrackMeta?
        var quality = 0
        var saveStatus = 0
        isDownloading = true // 다운로드 중이다.
        progress = 0

        val keys = mDownList.keys
        val it = keys.iterator()
        if (it.hasNext()) {
            nowTrackID = it.next()
            meta = mDownList[nowTrackID]
            quality = meta!!.mQuality
            saveStatus = meta.mStatus
        }

        val track = mBugsDb?.getSaveTrack(nowTrackID)
        if (track == null) { // 오류로 인한 중단일때
            stopAction()
            showToast(getString(R.string.save_error_data))
            return
        }

        notifyChange(SaveService.TRACK_CHANGED)

//        postNotification(
//            params = params,
//            contentText = track.trackTitle + " - " + TrackFactory.getArtistsName(track.artists)
//        )
        postDelayedNotification(
            params = params,
            contentText = track.trackTitle + " - " + TrackFactory.getArtistsName(track.artists),
            index = Random().nextInt(1000).toLong()
        )
        Thread.sleep(1000L)

        var downResult = false
        var currentQuality: String? = null
        var currentTrackLoudness : String ? = null
        if (isDownloading) {
            downResult = downloadDrmFile(this, nowTrackID, quality)
            currentQuality = setCurrentRealQuality()
            currentTrackLoudness = setCurrentTrackLoudness()
        }

        if (!downResult) {
            if (mDownloader!!.errorCode == HttpURLConnection.HTTP_NOT_FOUND) { // 권리 없는 곡
                Log.e(TAG, "404 에러. 권리 없으니 이 트랙 디비에서 지우기$nowTrackID")
                showToast(getString(R.string.save_error_no_save))
                BugsDb.getInstance(this)!!.deleteSaveTracks(nowTrackID)

                // 음질변경 시도라도 404면 없는 파일이니까 삭제하도록.
            } else if (saveStatus != BugsDb.SaveTracks.STATE_CHANGE) {
                updateStateFail(nowTrackID) // "음질변경인 경우 오류가 발생해도 기존 데이터를 그대로 유지한다."
            }

        } else {
            if (isDownloading) {
                val msg = mDelayedStopHandler.obtainMessage(DEF_DW_OK, track)
                mDelayedStopHandler.sendMessage(msg)

                if (sendChargeLog(track.trackId)) {
                    updateStateEnd(nowTrackID, mTotalLength, quality, currentQuality, currentTrackLoudness)
                    if (isFromPlayerSaving(nowTrackID)) {
                        updatePlayListDbFrom(nowTrackID) // 플레이어에서 저장하는 트랙의 프롬을 업뎃.
                    }

                    updatePlayListDbLoudness(nowTrackID, currentTrackLoudness) // 저장할때 재생목록에 저장한 곡이 있으면(위에서 프롬을 업데이트하고 그 여부를 확인할 수 있다) 그 트랙도 라우드니스 업뎃
                } else {
                    updateStateFail(nowTrackID)
                    try {
                        val drmFile = getNwiDrmFile(this, nowTrackID)
                        if (drmFile.exists()) {
                            val ret = drmFile.delete()
                            Log.d(TAG, "drm log fail file delete $ret")
                        }
                    } catch (ignore: Exception) {
                        Log.e(TAG, "ignore ", ignore)
                    }
                }

                if (isDownloading) {
                    try {
                        downloadAlbumArt(track) // 앨범 아트
                    } catch (ignore: Exception) {
                        Log.e(TAG, "drm album download error ", ignore)
                    }
                }

                if (isDownloading) {
                    downloadLyrics(nowTrackID) // 가사
                }

            } else {
                updateStateFail(nowTrackID)
            }
        }

        // 저장 성공 or 실패하고 난 후에 브로드캐스트를 쏴서 (ManagerSaveActivity에서 다시한번 저장할 목록들을 불러온다.)
        notifyChange(SaveService.TRACK_ENDED, nowTrackID, getBitrateQuality(currentQuality?: ""))

        if (!isDownloading) {
            notifyChange(SaveService.SAVE_COMPLETE)
            return
        }

        if (mDownList.size > 0)
            mDownList.remove(nowTrackID)

        if (mDownList.size == 0) {
            val cnt = mBugsDb?.querySaveTracksFailCount()
            cnt?.let {
                if (it > 0) {
                    // SD카드 용량체크 - 기본 용량 이상이 있어야 한다.
                    if (!isAvailSize(STORAGE_AVAILABLE_SIZE)) { // " 용량문제로 취소되는 경우 - 목록을 날리는게 필요한다. "
                        stopAction()
                        showToast(getString(R.string.save_error_storage_size))
                        return
                    } else {
                        showToast(getString(R.string.save_error_fail, "" + cnt))
                    }
                } else {
                    showToast(getString(R.string.toast_save_complete))
                }

                stopAction()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Synchronized
    private fun download2(params: JobParameters?) {
        var meta: DwTrackMeta?
        var quality = 0
        var saveStatus = 0
        isDownloading = true // 다운로드 중이다.
        progress = 0

        val keys = mDownList.keys
        val it = keys.iterator()
        if (it.hasNext()) {
            nowTrackID = it.next()
            meta = mDownList[nowTrackID]
            quality = meta!!.mQuality
            saveStatus = meta.mStatus
        }

        val track = mBugsDb?.getSaveTrack(nowTrackID)
        if (track == null) { // 오류로 인한 중단일때
            stopAction()
            showToast(getString(R.string.save_error_data))
            return
        }

        notifyChange(SaveService.TRACK_CHANGED)

        postNotification(
            params = params,
            contentText = track.trackTitle + " - " + TrackFactory.getArtistsName(track.artists)
        )

//        mWifiLock?.acquire()

        var downResult = false
        var currentQuality: String? = null
        var currentTrackLoudness : String ? = null
        if (isDownloading) {
            downResult = downloadDrmFile(this, nowTrackID, quality)
            currentQuality = setCurrentRealQuality()
            currentTrackLoudness = setCurrentTrackLoudness()
        }

        if (!downResult) {
            if (mDownloader!!.errorCode == HttpURLConnection.HTTP_NOT_FOUND) { // 권리 없는 곡
                Log.e(TAG, "404 에러. 권리 없으니 이 트랙 디비에서 지우기$nowTrackID")
                showToast(getString(R.string.save_error_no_save))
                BugsDb.getInstance(this)!!.deleteSaveTracks(nowTrackID)

                // 음질변경 시도라도 404면 없는 파일이니까 삭제하도록.
            } else if (saveStatus != BugsDb.SaveTracks.STATE_CHANGE) {
                updateStateFail(nowTrackID) // "음질변경인 경우 오류가 발생해도 기존 데이터를 그대로 유지한다."
            }

        } else {
            if (isDownloading) {
                val msg = mDelayedStopHandler.obtainMessage(DEF_DW_OK, track)
                mDelayedStopHandler.sendMessage(msg)

                if (sendChargeLog(track.trackId)) {
                    updateStateEnd(nowTrackID, mTotalLength, quality, currentQuality, currentTrackLoudness)
                    if (isFromPlayerSaving(nowTrackID)) {
                        updatePlayListDbFrom(nowTrackID) // 플레이어에서 저장하는 트랙의 프롬을 업뎃.
                    }

                    updatePlayListDbLoudness(nowTrackID, currentTrackLoudness) // 저장할때 재생목록에 저장한 곡이 있으면(위에서 프롬을 업데이트하고 그 여부를 확인할 수 있다) 그 트랙도 라우드니스 업뎃
                } else {
                    updateStateFail(nowTrackID)
                    try {
                        val drmFile = getNwiDrmFile(this, nowTrackID)
                        if (drmFile.exists()) {
                            val ret = drmFile.delete()
                            Log.d(TAG, "drm log fail file delete $ret")
                        }
                    } catch (ignore: Exception) {
                        Log.e(TAG, "ignore ", ignore)
                    }
                }

                if (isDownloading) {
                    try {
                        downloadAlbumArt(track) // 앨범 아트
                    } catch (ignore: Exception) {
                        Log.e(TAG, "drm album download error ", ignore)
                    }
                }

                if (isDownloading) {
                    downloadLyrics(nowTrackID) // 가사
                }

            } else {
                updateStateFail(nowTrackID)
            }
        }

        // 저장 성공 or 실패하고 난 후에 브로드캐스트를 쏴서 (ManagerSaveActivity에서 다시한번 저장할 목록들을 불러온다.)
        notifyChange(SaveService.TRACK_ENDED, nowTrackID, getBitrateQuality(currentQuality?: ""))

        if (!isDownloading) {
            notifyChange(SaveService.SAVE_COMPLETE)
            return
        }

        if (mDownList.size > 0)
            mDownList.remove(nowTrackID)

        if (mDownList.size == 0) {
            val cnt = mBugsDb?.querySaveTracksFailCount()
            cnt?.let {
                if (it > 0) {
                    // SD카드 용량체크 - 기본 용량 이상이 있어야 한다.
                    if (!isAvailSize(STORAGE_AVAILABLE_SIZE)) { // " 용량문제로 취소되는 경우 - 목록을 날리는게 필요한다. "
                        stopAction()
                        showToast(getString(R.string.save_error_storage_size))
                        return
                    } else {
                        showToast(getString(R.string.save_error_fail, "" + cnt))
                    }
                } else {
                    showToast(getString(R.string.toast_save_complete))
                }

                stopAction()
            }
        }
    }

    private var waitingNotify = false

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun postDelayedNotification(params: JobParameters?, contentText: String = "", delayMillis: Long = 1000L, index: Long = 0L) {
        Log.d(TAG, "in postDelayedNotification::index: ${index}, contentText: ${contentText}")
        if (!waitingNotify) {
            waitingNotify = true
            Timer().schedule(delayMillis) {
                handler.post {
                    val _contentText = contentText
                    val _index = index
                    Log.d(TAG, "in handler.post::_index: ${_index}, _contentText: ${_contentText}")
//                    postNotificationaaa(params, contentText)
//                    postNotification2(params, contentText, index)
                    postNotification2(params, _contentText, _index)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun postNotification2(params: JobParameters?, contentText: String = "", progress: Long = 0L) {
        waitingNotify = false
        Log.d(TAG, "postNotification, progress: ${progress}, ${progress * 100}")
        val contentText = if (progress < 1000) {
            "P: ${progress}, ${contentText}"
        } else {
            "Completed!"
        }
        val notification = Notification.Builder(applicationContext, INotificationChannel.DRM_DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading your file")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentText(contentText)
            .build()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this)
                .notify(
                    DRMDOWNLOADSERVICE_STATUS,
                    notification,
                )
        }

        params?.let {
            // Set notification to job.
            setNotification(
                params,
                DRMDOWNLOADSERVICE_STATUS,
                notification,
                JOB_END_NOTIFICATION_POLICY_DETACH,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun postNotificationaaa(params: JobParameters?, contentText: String = "") {
        Log.d(TAG, "contentText: ${contentText}")
        waitingNotify = false
        val notification = Notification.Builder(applicationContext, INotificationChannel.DRM_DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading your file")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentText(contentText)
            .build()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this)
                .notify(
                    DRMDOWNLOADSERVICE_STATUS,
                    notification,
                )
        }

        params?.let {
            // Set notification to job.
            setNotification(
                params,
                DRMDOWNLOADSERVICE_STATUS,
                notification,
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun postNotification(params: JobParameters?, contentText: String = "") {
        Log.d(TAG, "contentText: ${contentText}")
        waitingNotify = false
        val contentTitle = "기기에 저장 중입니다."
        val intent = Intent("com.neowiz.android.bugs.SAVELIST").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(KEY_FROM, FROM_NOTIFICATION)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, INotificationChannel.DRM_DOWNLOAD_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)

        val status = notification.build()
        status.flags = status.flags or Notification.FLAG_ONGOING_EVENT

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Manifest.permission.POST_NOTIFICATIONS == PERMISSION_GRANTED")
            NotificationManagerCompat
                .from(this)
                .notify(
                    DRMDOWNLOADSERVICE_STATUS,
                    status,
                )
        }

        params?.let {
            // Set notification to job.
            setNotification(
                params,
                DRMDOWNLOADSERVICE_STATUS,
                status,
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }
    }

    private fun gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        val msg = mDelayedStopHandler.obtainMessage()
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY.toLong())

//        stopForeground(true)
        NotificationManagerCompat.from(this).cancel(DRMDOWNLOADSERVICE_STATUS)
//        mWifiLock!!.release()
    }

    @Synchronized
    private fun isFromPlayerSaving(trackId: Long): Boolean {
        val playType = BugsPreference.getInstance(this).playServiceType
        if (isRadioPlaying(playType) || isMusicCastPlaying(playType)) {
            return false
        }

        val cursor = mBugsDb!!.queryPlayListFromPlayerSaving(trackId, playType)
        var from: String? = null
        while (cursor.moveToNext()) {
            val index = cursor.getColumnIndex(BugsDb.PlaylistTracks.FROM)
            if (index >= 0) {
                from = cursor.getString(index)
            }
        }

        cursor.close()
        return from != null && TrackFactory.DEF_FROM_PLAYER_SAVING == from
    }


    private fun updatePlayListDbFrom(trackId: Long) {
        BugsDb.getInstance(this)!!.updatePlaylistFrom(trackId, TrackFactory.DEF_FROM_SAVED_LIBRARY)
    }

    private fun updatePlayListDbLoudness(trackId: Long, loudness: String?) {
        BugsDb.getInstance(this)!!.updateLoudnessPlayList(trackId, loudness)
    }

    private fun downloadDrmFile(context: Context, trackID: Long, saveQuality: Int): Boolean {
        // "NWI DRM"
        return mDownloader?.getForceNwiDrm(getNwiDrmFileDownloadUrl(trackID, saveQuality), getNwiDrmFile(context, trackID), false) ?: false
    }

    // NWI DRM API
    private fun getNwiDrmFileDownloadUrl(trackID: Long, saveQuality: Int): String? {

        if (TextUtils.isEmpty(LoginInfo.sMsrl))
            return null

        val bitrate = getBitrate(saveQuality)

        var url: String? = null

        val response = BugsApi.getService(this).save(trackID, bitrate).safeExecute()
        response?.let {
            checkResponse(response,
                {
                    val res = response.body()
                    if (res is ApiSave) {
                        res.saveUrl?.let {
                            try {
                                url = Decrypt().decryptApi(it.url, trackID)
                            } catch (e: Exception) {
                                Log.e(TAG, e.message, e)
                            }
                            mCurrentRealQuality = it.bitrate
                            nowTrackLoudness = it.loudness
                        }
                    }
                },
                {})
        }

        return  url
    }

    private fun setCurrentRealQuality(): String? {
        val quality = mCurrentRealQuality
        mCurrentRealQuality = null
        return quality
    }

    private fun setCurrentTrackLoudness(): String? {
        val loudness = nowTrackLoudness
        nowTrackLoudness = null
        return loudness
    }

    /**
     * 앨범 커버이미지 다운로드
     */
    private fun downloadAlbumArt(track: Track) {
        downloadAlbumArt(track.getAlbumUrl(AlbumImageSize.ALBUM1000))
        downloadAlbumArt(track.getAlbumUrl(AlbumImageSize.ALBUM500))
        downloadAlbumArt(track.getAlbumUrl(AlbumImageSize.ALBUM350))
        downloadAlbumArt(track.getAlbumUrl(AlbumImageSize.ALBUM200))
        downloadAlbumArt(track.getAlbumUrl(AlbumImageSize.ALBUM140))
        downloadAlbumArt(track.getAlbumUrl(AlbumImageSize.ALBUM75))

        progress += 5
        notifyChange(PROGRESS_CHANGED)
    }

    private fun downloadAlbumArt(albumImgUrl: String) {
        if (TextUtils.isEmpty(albumImgUrl)) {
            Log.e(TAG, "albumImgUrl is null $albumImgUrl")
            return
        }

        val saveImage = getDefaultFile(getImageCacheDirectory(this), albumImgUrl)
//        Log.d(TAG, "albumImgUrl : $albumImgUrl")
        mDownloaderResource!![albumImgUrl, saveImage]
    }

    private fun getDefaultFile(dir: File, strUrl: String): File {
        return File(dir, urlEncode(strUrl).replace(".", "_"))
    }

    /**
     * 가사 파일 다운로드
     */
    private fun downloadLyrics(trackID: Long) {
        BugsApi.getService(this).getTrackLyrics(trackID).cacheExecute(this, CACHE_TYPE.API_FIRST)
        progress += 5
        notifyChange(PROGRESS_CHANGED)
    }

    /**
     * SaveTracks DB업데이트 - 성공
     */
    private fun updateStateEnd(trackID: Long, contentLength: Long, quality: Int, strRealQuality: String?, trackLoudness: String?): Int {

        val result: Int = if (isEmptyString(strRealQuality)) {
            mBugsDb!!.updateSaveTrackStateEnd(trackID, contentLength, trackLoudness)
        } else {
            val realQuality = getBitrateQuality(strRealQuality!!)

            Log.d(TAG, "meta quality = $quality, real quality = $realQuality")
            if (quality != realQuality) {
                mBugsDb!!.updateSaveTrackStateEnd(trackID, contentLength, realQuality, trackLoudness)
            } else {
                mBugsDb!!.updateSaveTrackStateEnd(trackID, contentLength, trackLoudness)
            }
        }
        return result
    }

    private fun updateStateFail(trackID: Long): Int {
        Log.d(TAG, "updateStateFail = $trackID")
        return mBugsDb!!.updateSaveTrackStateFail(trackID)
    }

    /**
     * 사이즈 체크.
     *
     * @param needSize
     * @return
     */
    // TODO MR : 여유공간 (https://developer.android.com/training/data-storage/app-specific?hl=ko#query-free-space)
    private fun isAvailSize(needSize: Long): Boolean {
        return if (mStatFs == null && !statFs()) {
            false
        } else com.neowiz.android.bugs.api.appdata.isAvailSize(
            getDrmDirectory(this).absolutePath,
            mStatFs!!,
            needSize
        )
    }

    private fun statFs(): Boolean {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val saveDir = getDrmDirectory(this)
            if (saveDir.exists().not()) {
                makeDirs(saveDir)
            }

            try {
                mStatFs = StatFs(saveDir.absolutePath)
            } catch (e: Exception) {
                return false
            }

            return true
        }

        return false
    }

    private fun notifyChange(what: String, trackId: Long = 0, quality: Int = SAVE_QUALITY_AAC) {
        if (SaveService.TRACK_CHANGED == what) {
            progress = 0
        }

        val i = Intent(what)
        if (trackId > 0) {
            i.putExtra(SaveService.TRACK_ID, trackId)
            i.putExtra(SaveService.QUALITY, quality)
            i.putExtra(PROGRESS, progress)
        }
        sendBroadcast(i)
    }

    /**
     * 다운로드를 중지 시킨다.
     */
    fun stopAction() {
        mDownloader?.stop()
        isDownloading = false
        mDownloadRunnable?.stop()
        nowTrackID = 0
        mDownList.clear()
        notifyChange(SAVE_COMPLETE)

        gotoIdleState()
    }

    /**
     * 다운로드 받는다.
     */
    private inner class DownloadRunnable : Runnable {

        private var mThread: Thread? = null
        private var mIsLive = false
        private var _params: JobParameters? = null

        fun start(params: JobParameters?) {
            _params = params
            if (!mIsLive) {
                mIsLive = true
                mThread = Thread(this, "download thread name: " + sTestName++)
                mThread?.start()
            }
        }

        override fun run() {
            while (mIsLive) {
                Log.d(TAG, "run download")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        download(_params)
                    }
                } catch (e: InterruptedIOException) {
                    Log.e(TAG, e.message)
                }
            }
        }

        fun stop() {
            mIsLive = false
            try {
                Log.d(TAG, "stop download")
                jobFinished(_params, false)
                mThread?.interrupt()
            } catch (e: Exception) {
//                e.printStackTrace()
            }
        }
    }

    inner class DwTrackMeta(var mQuality: Int, var mStatus: Int, var mIsRestored: Int)

    /**
     * DB에서 다운받지 않은 곡과 실패한 곡을 뽑아온다.
     */
    private fun getDownList() {
        var trackID: Long
        var quality: Int
        var status: Int
        var isRestored: Int

        val cursor = mBugsDb!!.querySaveTracks(
            arrayOf(
                BugsDb.SaveTracks.TRACK_ID,
                BugsDb.SaveTracks.MTYPE,
                BugsDb.SaveTracks.STATE,
                BugsDb.SaveTracks.OLD_MTYPE
            ),
            BugsDb.SaveTracks.STATE + " =  ? OR " + BugsDb.SaveTracks.STATE + "= ?  OR " + BugsDb.SaveTracks.STATE + " = ?  ",
            arrayOf(
                "" + BugsDb.SaveTracks.STATE_READY,
                "" + BugsDb.SaveTracks.STATE_CHANGE,
                "" + BugsDb.SaveTracks.STATE_FAIL
            ),
            BugsDb.SaveTracks.REG_DATE + " asc "
        )

        Log.d(TAG, "SAVE 할 곡들을 DB 에서 가져온다. " + cursor.count)
        while (cursor.moveToNext()) {
            trackID = cursor.getLong(0)
            quality = cursor.getInt(1)
            status = cursor.getInt(2)
            isRestored = cursor.getInt(3)

            val meta = DwTrackMeta(quality, status, isRestored)

            if (!mDownList.containsKey(trackID)) {
                mDownList[trackID] = meta
            }
        }

        cursor.close()
    }

    private fun sendChargeLog(trackID: Long): Boolean {

        val accountTp = if(LoginInfo.sisOfflinePlay) AccountTp.OFFLINE else AccountTp.SAVE

        var result = false
        //device name: android 는 device id 가 바꾸지 않으므로 의미 없음. 2019.2.11 영훈님 확인. 그냥 모델명을 주자.
        val response = BugsApi.getLogService(this).saveLog(trackID, getModelName(), accountTp).safeExecute()
        response?.let {
            checkResponse(response,
                {
                    val res = response.body()
                    res?.let {
                        result = true
                    }
                },
                {})
        }

        Log.d(TAG, "send save log ret : $result")
        return result
    }

    private fun getModelName(): String {
        // 잉카 DRM SDK사용 부분 때문에 문자열에 공백이 존재하면 안된다. 공백 치환.
        val model = Build.MODEL
        return model.replace(" ", "_")
    }

    private fun showToast(str: String) {
        Handler(Looper.getMainLooper()).postDelayed({Toast.showToast(this@SaveUseJobService, str)}, 0)
    }

    private val mMediaEjectReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (isDownloading) {
                stopAction()
            }
        }
    }

    companion object {
        private val STORAGE_AVAILABLE_SIZE = (30 * 1024 * 1024).toLong()
        private val IDLE_DELAY = 300

        private var sTestName = 1
        private val JobId = 1000
    }

    override fun onCreate() {
        super.onCreate()

        val builder = Configuration.Builder()
        builder.setJobSchedulerJobIdRange(0, JobId)

        mBugsDb = BugsDb.getInstance(this)
        mBugsPref = BugsPreference.getInstance(this)

        mDownloaderResource = Downloader(this)

        mDownloader = Downloader(this)
        mDownloader!!.setDownloadListener(object: Downloader.DownloadListener {
            override fun onProgress(current: Long, total: Long) {
                val currentProgress = (current * 90 / total).toInt() // " 다른것도 있어서 파일다운로드는 다받으면 90%로 본다. "

                if (progress != currentProgress) {
                    progress = currentProgress
                    mTotalLength = total

                    if (progress % 5 == 0 && isDownloading) { // " 5%단위로  보낸다. "
                        notifyChange(PROGRESS_CHANGED)
                    }
                }
            }
        })

        mDownloader!!.setErrorToastListener(object: Downloader.ErrorToastListener {
            override fun onError(msg: String) {
                showToast(msg)
            }
        })

//        mConnectMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

//        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
//        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, javaClass.name)
//        mWifiLock!!.setReferenceCounted(false)

        statFs()

        mDownloadRunnable = DownloadRunnable()

        val mediaFilter = IntentFilter(Intent.ACTION_MEDIA_EJECT)
        mediaFilter.addDataScheme("file")

        registerReceiver(mMediaEjectReceiver, mediaFilter, RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
//        mWifiLock?.release()
        mDownloadRunnable?.stop()

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(mMediaEjectReceiver)
        super.onDestroy()
    }
}