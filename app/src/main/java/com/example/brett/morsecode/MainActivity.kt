package com.example.brett.morsecode

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.inputmethod.InputMethodManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONObject
import java.lang.Math.round
import java.util.*
import kotlin.concurrent.timerTask
import android.telephony.SmsManager
import android.view.*
import android.widget.EditText
import android.widget.Toast


val SAMPLE_RATE = 44100
var morsePitch = 0

class MainActivity : AppCompatActivity() {
    var prefs: SharedPreferences? = null
    var letToCodeDict: HashMap<String, String> = HashMap()
    var codeToLetDict: HashMap<String, String> = HashMap()
    val dotLength = 50
    val dashLength = dotLength * 3

    var dotSoundBuffer:ShortArray = kotlin.ShortArray(0)
    var dashSoundBuffer:ShortArray = kotlin.ShortArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        setupPermissions()
        prefs = getDefaultSharedPreferences(this.applicationContext)

        //..needed for scrolling
        mTextView.movementMethod = ScrollingMovementMethod();

        messageButton.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            // had it here
        }
        // has to go outside the {}
        buildDictsWithJSON(loadMorseJSON())

        showButton.setOnClickListener{ view ->
            showCodes()
            hideKeyboard()
        }


        translateButton.setOnClickListener{ view ->
            if(inputText.text.toString() == ""){
                appendTextAndScroll("")
            }
            else if(isMorse(inputText.text.toString())){
                appendTextAndScroll("Input: ${inputText.text.toString()}")
                appendTextAndScroll("Translation: ${translateMorseToText(inputText.text.toString())}")
                hideKeyboard()
            }
            else {
                appendTextAndScroll("Input: ${inputText.text.toString()}")
                appendTextAndScroll("Translation: ${translateTextToMorse(inputText.text.toString())}")
                hideKeyboard()
            }
        }

        clearButton.setOnClickListener{ view ->
            mTextView.setText("")
        }

        play_button.setOnClickListener{ view ->
            play_button.isClickable = false

                morsePitch = prefs!!.getString("morse_pitch", "550").toInt()
                genNewSineBuffer()

            if(isMorse(inputText.text.toString()) == false) {
                val textInBox = translateTextToMorse(inputText.text.toString())
                playString(textInBox, 0)
            }
            else if(isMorse(inputText.text.toString())){
                val morseInBox = inputText.text.toString()
                playString(morseInBox, 0)
            }
        }

        messageButton.setOnClickListener{ view ->
            getNumber()
        }

    }

    fun getNumber(){
        val dialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.number_retrieval, null)
        val et_number = dialogView.findViewById<EditText>(R.id.phone_number_box)
        dialog.setView(dialogView)
        dialog.setCancelable(false)

        dialog.setPositiveButton("Ok",{ dialogInterface: DialogInterface, i: Int -> })

        val getText = dialog.create()
        getText.show()

        getText.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener({
            val number = et_number.text.toString()
            val message = translateTextToMorse(inputText.text.toString())
            if(et_number.length() < 7){
                Toast.makeText(baseContext,"Not a valid phone number.", Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(baseContext, "Message sent to $number.", Toast.LENGTH_SHORT).show()
                getText.dismiss()
                sendSMS(number, message)
            }
        })
    }

    private fun appendTextAndScroll(text: String){
        if (mTextView != null){
            mTextView.append(text + "\n")
            val layout = mTextView.getLayout()
            if(layout != null){
                val scrollDelta = (layout.getLineBottom(mTextView.getLineCount() - 1) - mTextView.getScrollY()
                        - mTextView.getHeight())
                if(scrollDelta > 0)
                    mTextView.scrollBy(0, scrollDelta)
            }
        }
    }

    fun Activity.hideKeyboard(){
        hideKeyboard(if (currentFocus == null) View(this) else currentFocus)
    }

    fun Context.hideKeyboard(view: View){
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken,0)
    }

    fun translateTextToMorse(inputString: String) : String{

        var new_string = ""

        var i = 0
        while(i < inputString.length){
            new_string += letToCodeDict[inputString[i].toLowerCase().toString()]
            new_string += ' '
            i++
        }


        return new_string
    }

    fun translateMorseToText(inputString: String) : String{

        var new_string = ""
        var temp_string = ""
        var delim = ' '

        var i = 0

        while(i < inputString.length){
            if(inputString[i] != delim) {
                temp_string += inputString[i]
                i++
            }
            else if(inputString[i] == delim) {
                new_string += codeToLetDict[temp_string].toString()
                temp_string = ""
                i++
            }
        }
        new_string += codeToLetDict[temp_string].toString()

        return new_string
    }

    fun isMorse(inputString : String) : Boolean{
        var check = true
        var i = 0

        val checkDot = '.'
        val checkDash = '-'
        val checkSlash = '/'
        val checkSpace = ' '

        while(i < inputString.length) {
            if (inputString[i] == checkDot || inputString[i] == checkDash || inputString[i] == checkSlash || inputString[i] == checkSpace){
                i++
                continue
            }
            else{
                check = false
                break
            }
        }
        return check
    }

    fun loadMorseJSON() : JSONObject {
        val filePath = "morse.json"

        val jsonStr = application.assets.open(filePath).bufferedReader().use{
            it.readText()
        }

        val jsonObj = JSONObject(jsonStr.substring(jsonStr.indexOf("{"), jsonStr.lastIndexOf("}") + 1))

        return jsonObj
    }

    fun buildDictsWithJSON(jsonObj : JSONObject){

        for(k in jsonObj.keys()){
            val code = jsonObj[k]

            letToCodeDict.put(k, code.toString())
            codeToLetDict.put(code.toString(), k)

        }
    }

    fun showCodes(){
        appendTextAndScroll("HERE ARE THE CODES\n")
        for(k in letToCodeDict.keys.sorted())
            appendTextAndScroll("$k: ${letToCodeDict[k]}")
    }

    fun playString(s:String, i: Int) : Unit{
        if (i > s.length - 1) {
            play_button.isClickable = true
            return
        }

        //var mDelay: Long = 0
        // thenFun = lambda function that will
        // switch back to main thread and play the next char
        var thenFun: () -> Unit = { ->
            this@MainActivity.runOnUiThread(java.lang.Runnable{playString(s, i+1)})
        }

        var c = s[i]
        Log.d("log", "Processing pos: " + i + " char: {" + c + "]")
        if(c == '.') {
            playDot(thenFun)
        }
        else if(c == '-')
            playDash(thenFun)
        else if(c == '/')
            pause(6*dotLength, thenFun)
        else if(c == ' ')
            pause(2*dotLength, thenFun)
    }

    fun playDash(onDone: () -> Unit = {}){
        Log.d("DEBUG", "playDash")
        playSoundBuffer(dashSoundBuffer, {pause(dotLength, onDone)})
    }

    fun playDot(onDone: () -> Unit = {}){
        Log.d("DEBUG", "playDot")
        playSoundBuffer(dotSoundBuffer, {pause(dotLength, onDone)})
    }

    fun pause(durationMSec: Int, onDone : () -> Unit = {}){
        Log.d("DEBUG", "pause " + durationMSec)
        Timer().schedule( timerTask{onDone()}, durationMSec.toLong())
    }

    fun genNewSineBuffer(){
        dotSoundBuffer = genSineWaveSoundBuffer(morsePitch.toDouble(), dotLength)
        dashSoundBuffer = genSineWaveSoundBuffer(morsePitch.toDouble(), dashLength)
    }

    private fun genSineWaveSoundBuffer(frequency: Double, durationMSec: Int) : ShortArray{
        val duration:Int = round((durationMSec / 1000.0) * SAMPLE_RATE).toInt()

        var mSound : Double
        val mBuffer = ShortArray(duration)
        for(i in 0 until duration ) {
            mSound = Math.sin(2.0 * Math.PI * i.toDouble() / (SAMPLE_RATE / frequency))
            mBuffer[i] = (mSound * java.lang.Short.MAX_VALUE).toShort()
        }
        return mBuffer
    }

    private fun playSoundBuffer(mBuffer:ShortArray, onDone : () -> Unit = { /* noop */}){
        var minBufferSize = SAMPLE_RATE/10
        if(minBufferSize < mBuffer.size){
            minBufferSize = minBufferSize + minBufferSize * (Math.round(mBuffer.size.toFloat()) / minBufferSize.toFloat() ).toInt()
        }

        val nBuffer = ShortArray(minBufferSize)
        for(i in nBuffer.indices){
            if (i < mBuffer.size) nBuffer[i] = mBuffer[i]
            else nBuffer[i] = 0
        }

        val mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM)

        mAudioTrack.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume())
        mAudioTrack.setNotificationMarkerPosition(mBuffer.size)
        mAudioTrack.setPlaybackPositionUpdateListener(object: AudioTrack.OnPlaybackPositionUpdateListener{
            override fun onPeriodicNotification(track: AudioTrack) {
            }
            override fun onMarkerReached(track: AudioTrack) {
                Log.d("Log", "Audio track end of file reach...")
                mAudioTrack.stop()
                mAudioTrack.release()
                onDone()
            }
        })
        mAudioTrack.play()
        mAudioTrack.write(nBuffer, 0, minBufferSize)
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        val sm = SmsManager.getDefault()
        sm.sendTextMessage(phoneNumber, null, message, null, null)
    }

    private fun setupPermissions(){
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Permission to record denied")
            makeRequest()
        }
    }

    private fun makeRequest() {
        val REQUEST_CODE = 101
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQUEST_CODE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)

                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

