package org.commonvoice.saverio

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_speak.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonvoice.saverio.ui.VariableLanguageActivity
import org.commonvoice.saverio.ui.dialogs.NoClipsSentencesAvailableDialog
import org.commonvoice.saverio.ui.dialogs.SpeakReportDialogFragment
import org.commonvoice.saverio.utils.onClick
import org.commonvoice.saverio_lib.api.network.ConnectionManager
import org.commonvoice.saverio_lib.models.Sentence
import org.commonvoice.saverio_lib.preferences.StatsPrefManager
import org.commonvoice.saverio_lib.viewmodels.SpeakViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.stateViewModel

class SpeakActivity : VariableLanguageActivity(R.layout.activity_speak) {

    companion object {
        private const val RECORD_REQUEST_CODE = 101
    }

    private val speakViewModel: SpeakViewModel by stateViewModel()

    private val connectionManager: ConnectionManager by inject()
    private val statsPrefManager: StatsPrefManager by inject()

    private var numberSentThisSession: Int = 0
    private var isAudioBarVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupInitialUIState()

        setupUI()
    }

    private fun checkOfflineMode(available: Boolean) {
        if (!speakViewModel.showingHidingOfflineIcon && (speakViewModel.offlineModeIconVisible == available)) {
            speakViewModel.showingHidingOfflineIcon = true
            if (!available) {
                startAnimation(imageOfflineModeSpeak, R.anim.zoom_in_speak_listen)
                speakViewModel.offlineModeIconVisible = true
                if (mainPrefManager.showOfflineModeMessage) {
                    showMessageDialog("", "", 10)
                }
            } else {
                startAnimation(imageOfflineModeSpeak, R.anim.zoom_out_speak_listen)
                speakViewModel.offlineModeIconVisible = false
            }
            speakViewModel.showingHidingOfflineIcon = false
            this.imageOfflineModeSpeak.isGone = available
        }
    }

    public fun setShowOfflineModeMessage(value: Boolean = true) {
        mainPrefManager.showOfflineModeMessage = value
    }

    override fun onBackPressed() {
        textMessageAlertSpeak.setText(R.string.txt_closing)
        buttonStartStopSpeak.setBackgroundResource(R.drawable.speak_cv)
        textSentenceSpeak.text = "···"
        textSentenceSpeak.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.title_very_big)
        )
        buttonRecordOrListenAgain.isGone = true
        buttonReportSpeak.isGone = true
        buttonSkipSpeak.isEnabled = false
        buttonStartStopSpeak.isEnabled = false
        buttonSendSpeak.isGone = true
        speakSectionAudioBar.isGone = true

        speakViewModel.stop(true)

        super.onBackPressed()
    }

    private fun setupUI() {
        imageOfflineModeSpeak.onClick {
            lifecycleScope.launch {
                val count = speakViewModel.getSentencesCount()
                withContext(Dispatchers.Main) {
                    NoClipsSentencesAvailableDialog(this@SpeakActivity, true, count).show()
                }
            }
        }

        connectionManager.liveInternetAvailability.observe(this, Observer { available ->
            checkOfflineMode(available)
        })

        speakViewModel.hasFinishedSentences.observe(this, Observer {
            if (it && !connectionManager.isInternetAvailable) {
                NoClipsSentencesAvailableDialog(this, true, 0).show {
                    onBackPressed()
                }
            }
        })

        speakViewModel.currentSentence.observe(this, Observer { sentence ->
            setupUIStateStandby(sentence)
        })

        if (mainPrefManager.areGesturesEnabled) {
            setupGestures()
        }

        speakViewModel.state.observe(this, Observer {
            checkState(it)
        })

        statsPrefManager.dailyGoal.observe(this, Observer {
            if ((this.numberSentThisSession > 0) && it.checkDailyGoal()) {
                this.stopAndRefresh()
                showMessageDialog(
                    "",
                    getString(R.string.daily_goal_achieved_message).replace(
                        "{{*{{n_clips}}*}}",
                        "${it.validations}"
                    ).replace(
                        "{{*{{n_sentences}}*}}",
                        "${it.recordings}"
                    )
                )
            }
        })

        setTheme(this)
    }

    private fun checkState(status: SpeakViewModel.Companion.State?) {
        when (status) {
            SpeakViewModel.Companion.State.STANDBY -> {
                loadUIStateLoading()
                speakViewModel.loadNewSentence()
            }
            SpeakViewModel.Companion.State.RECORDING -> {
                loadUIStateRecording()
                this.isAudioBarVisible = true
                animateAudioBar()
            }
            SpeakViewModel.Companion.State.RECORDED -> {
                loadUIStateRecorded()
            }
            SpeakViewModel.Companion.State.LISTENING -> {
                loadUIStateListening()
            }
            SpeakViewModel.Companion.State.LISTENED -> {
                loadUIStateListened()
            }
            SpeakViewModel.Companion.State.RECORDING_ERROR -> {
                this.stopAndRefresh()
                showMessageDialog("", getString(R.string.messageDialogGenericError), type = 7)
                speakViewModel.currentSentence.value?.let { sentence ->
                    setupUIStateStandby(sentence)
                }
            }
            SpeakViewModel.Companion.State.RECORDING_TOO_SHORT -> {
                showMessageDialog("", getString(R.string.txt_recording_too_short), type = 7)
                speakViewModel.currentSentence.value?.let { sentence ->
                    setupUIStateStandby(sentence)
                }
            }
            SpeakViewModel.Companion.State.RECORDING_TOO_LONG -> {
                showMessageDialog("", getString(R.string.txt_recording_too_long), type = 7)
                speakViewModel.currentSentence.value?.let { sentence ->
                    setupUIStateStandby(sentence)
                }
            }
        }
    }

    private fun showMessageDialog(title: String, text: String, type: Int = 0) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        //val width = metrics.widthPixels
        val height = metrics.heightPixels
        val msg = MessageDialog(this, type, title, text, details = "", height = height)
        msg.setSpeakActivity(this)
        msg.show()
    }

    private fun setupGestures() {
        nestedScrollSpeak.setOnTouchListener(object : OnSwipeTouchListener(this@SpeakActivity) {
            override fun onSwipeLeft() {
                speakViewModel.skipSentence()
            }

            override fun onSwipeRight() {
                onBackPressed()
            }

            override fun onSwipeTop() {
                if (mainPrefManager.deviceOrientation == ORIENTATION_PORTRAIT) {
                    openReportDialog()
                }
            }
        })
    }

    fun setTheme(view: Context) {
        val theme: DarkLightTheme = DarkLightTheme()

        val isDark = theme.getTheme(view)
        theme.setElement(isDark, layoutSpeak)
        theme.setElement(isDark, view, buttonSendSpeak)
        theme.setElement(isDark, view, 1, speakSectionBottom)
        theme.setElement(
            isDark,
            view,
            textMessageAlertSpeak,
            R.color.colorAlertMessage,
            R.color.colorAlertMessageDT
        )
        theme.setElement(isDark, view, buttonReportSpeak, background = false)
        theme.setElement(isDark, view, buttonSkipSpeak)
    }

    private fun openReportDialog() {
        if (!buttonReportSpeak.isGone) {
            this.stopAndRefresh()

            SpeakReportDialogFragment().show(supportFragmentManager, "SPEAK_REPORT")
        }
    }

    private fun stopAndRefresh() {
        speakViewModel.stop()
        speakViewModel.currentSentence.value?.let { sentence ->
            setupUIStateStandby(sentence)
        }
        hideAudioBar()
    }

    private fun setupInitialUIState() {
        buttonSkipSpeak.onClick {
            speakViewModel.skipSentence()
        }

        buttonReportSpeak.onClick {
            openReportDialog()
        }

        buttonRecordOrListenAgain.onClick {
            speakViewModel.startListening()
        }

        buttonSendSpeak.onClick {
            speakViewModel.sendRecording()
            this.numberSentThisSession++
        }

        startAnimation(buttonStartStopSpeak, R.anim.zoom_in_speak_listen)
        startAnimation(buttonSkipSpeak, R.anim.zoom_in_speak_listen)
    }

    private fun loadUIStateLoading() {
        textMessageAlertSpeak.setText(R.string.txt_loading_sentence)
        textSentenceSpeak.text = "···"

        buttonRecordOrListenAgain.isGone = true
        buttonReportSpeak.isGone = true
        buttonSendSpeak.isGone = true
        buttonSkipSpeak.isEnabled = false
        buttonStartStopSpeak.isEnabled = false
    }

    private fun setupUIStateStandby(sentence: Sentence) {
        buttonSkipSpeak.isEnabled = true
        buttonStartStopSpeak.isEnabled = true

        buttonReportSpeak.isGone = false

        buttonSendSpeak.isGone = true

        buttonRecordOrListenAgain.isGone = true
        buttonStartStopSpeak.setBackgroundResource(R.drawable.speak_cv)

        hideAudioBar()

        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_speak_1)
        textSentenceSpeak.text = sentence.sentenceText
        when (textSentenceSpeak.text.length) {
            in 0..10 -> {
                textSentenceSpeak.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.title_very_big)
                )
            }
            in 11..20 -> {
                textSentenceSpeak.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.title_big)
                )
            }
            in 21..40 -> {
                textSentenceSpeak.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.title_medium)
                )
            }
            in 41..70 -> {
                textSentenceSpeak.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.title_normal)
                )
            }
            else -> {
                textSentenceSpeak.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.title_small)
                )
            }
        }

        buttonStartStopSpeak.onClick {
            checkPermission()
            speakViewModel.startRecording()
        }
    }

    private fun loadUIStateRecording() {
        buttonRecordOrListenAgain.isGone = true
        buttonStartStopSpeak.setBackgroundResource(R.drawable.stop_cv)

        buttonSendSpeak.isGone = true
        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_speak_2)
        speakViewModel.isFirstTimeListening = true

        buttonStartStopSpeak.onClick {
            checkPermission()
            speakViewModel.stopRecording()
        }
    }

    private fun loadUIStateRecorded() {
        buttonRecordOrListenAgain.isGone = false
        startAnimation(buttonRecordOrListenAgain, R.anim.zoom_in_speak_listen)
        buttonRecordOrListenAgain.setBackgroundResource(R.drawable.speak2_cv)

        buttonStartStopSpeak.setBackgroundResource(R.drawable.listen2_cv)
        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_listen_1)

        buttonStartStopSpeak.onClick {
            speakViewModel.startListening()
        }

        buttonRecordOrListenAgain.onClick {
            checkPermission()
            speakViewModel.redoRecording()
        }
    }

    private fun loadUIStateListening() {
        buttonRecordOrListenAgain.isGone = true
        buttonStartStopSpeak.setBackgroundResource(R.drawable.stop_cv)
        textMessageAlertSpeak.setText(R.string.txt_press_icon_below_listen_2)

        buttonStartStopSpeak.onClick {
            speakViewModel.stopListening()
        }
    }

    private fun loadUIStateListened() {
        buttonSendSpeak.isGone = false

        if (speakViewModel.isFirstTimeListening) {
            startAnimation(buttonSendSpeak, R.anim.zoom_in_speak_listen)
            speakViewModel.isFirstTimeListening = false
        }

        textMessageAlertSpeak.setText(R.string.txt_recorded_correct_or_wrong)
        buttonRecordOrListenAgain.isGone = false
        startAnimation(buttonRecordOrListenAgain, R.anim.zoom_in_speak_listen)
        buttonRecordOrListenAgain.setBackgroundResource(R.drawable.listen2_cv)
        buttonStartStopSpeak.setBackgroundResource(R.drawable.speak2_cv)


        buttonStartStopSpeak.onClick {
            checkPermission()
            speakViewModel.redoRecording()
        }

        buttonRecordOrListenAgain.onClick {
            speakViewModel.startListening()
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            RECORD_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    onBackPressed()
                }
            }
        }
    }

    private fun animateAudioBar() {
        if (mainPrefManager.areAnimationsEnabled) {
            speakSectionAudioBar.children.forEach {
                animateAudioBar(it)
            }
        }
    }

    private fun hideAudioBar() {
        if (mainPrefManager.areAnimationsEnabled) {
            if (imageAudioBarCenter.isVisible && this.isAudioBarVisible) {
                this.isAudioBarVisible = false
                speakSectionAudioBar.children.forEach {
                    animateAudioBar(it)
                }
            }
        }
    }

    private fun animateAudioBar(view: View) {
        if (speakViewModel.state.value == SpeakViewModel.Companion.State.RECORDING && this.isAudioBarVisible) {
            animationAudioBar(view, view.height, (30..350).random())
            view.isVisible = true
        } else if (!this.isAudioBarVisible) {
            //animationAudioBar(view, view.height, 0)
            //view.isVisible = true
            view.isVisible = false
        } else {
            view.isVisible = false
        }
    }

    private fun animationAudioBar(view: View, min: Int, max: Int) {
        val animation: ValueAnimator =
            ValueAnimator.ofInt(min, max)
        animation.duration = 300
        animation.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            view.layoutParams.height = value
            view.requestLayout()
        }
        animation.doOnEnd {
            if (this.isAudioBarVisible) {
                animateAudioBar(view)
            }
        }
        animation.start()
    }

}
