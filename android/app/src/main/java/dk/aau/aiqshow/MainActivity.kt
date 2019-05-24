package dk.aau.aiqshow

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import dk.aau.iaqlibrary.*
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference
import android.support.v4.app.FragmentManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.recycler.view.*
import org.json.JSONArray
import org.json.JSONObject
import dk.aau.iaqlibrary.BluetoothService.Companion as comp
import java.nio.charset.Charset
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt


private const val TAG = "MAIN_ACTIVITY_DEBUG"
const val PREFERENCES = "prefs"

fun Boolean.toInt() = if (this) 1 else 0

fun Int.toBool() = this > 0

data class DataReading(val gasType: String, val value: Float, val time: LocalDateTime) {
    override fun toString(): String {
        return "Gas: $gasType, Value: ${value.roundToInt()}, Time: $time"
    }
}

data class Configuration(val mac: String, val subbed: Boolean, val guideline: String) {
    override fun toString(): String {
        return "CFG/[[\"$mac\",\"${subbed.toInt()}\",\"$guideline\"]]"
    }
}

class MainActivity : AppCompatActivity(), SuperFragment.InputListener {

    private var prefs : SharedPreferences? = null
    private val mmBTAdapter : BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val mmWeakRef = WeakReference(this)
    private lateinit var mmHandler : MyHandler
    private val mmDeviceAddress : String = "B8:27:EB:4C:0D:D9"
    private val mmManager: FragmentManager = supportFragmentManager
    private lateinit var mmDevice : BluetoothDevice
    private lateinit var mmBTService : BluetoothService
    private lateinit var recyclerView: RecyclerView



    private class MyHandler(private val ref: WeakReference<MainActivity>) : Handler() {
        private var data: String = ""
        private var size: Int = 0
        val prefs: SharedPreferences? = ref.get()!!.getSharedPreferences(PREFERENCES,0)
        override fun handleMessage(msg: Message) {
            val thing = (msg.obj as ByteArray).toString(Charset.defaultCharset()).take(msg.arg1)

            when {
                msg.what == comp.MESSAGE_READ -> {Log.i("$TAG READ",thing); messageRead(msg)}
                msg.what == comp.MESSAGE_WRITE -> Log.i("$TAG WRITE",thing)
                msg.what == comp.MESSAGE_CONNECT -> {Log.i("$TAG CONNECTION",thing); connect(msg.arg2)
                    ref.get()!!.mainText.text = thing}
                msg.what == comp.MESSAGE_EMPTY -> Log.i("$TAG EMPTY", thing)
                msg.what == comp.MESSAGE_ERROR -> {Log.i("$TAG ERROR", thing)
                    if (msg.arg2 == comp.ERROR_CONNECT)
                        ref.get()!!.mainText.text = "Connection Error"
                }
                else -> Log.i(TAG, thing)
            }
        }

        private fun messageRead(msg : Message) {

            val thing = (msg.obj as ByteArray).toString(Charset.defaultCharset()).take(msg.arg1)

            when (msg.arg2) {
                comp.CONTENT_ACKNOWLEDGE -> ref.get()!!.mmBTService.get(comp.getConfig())
                comp.CONTENT_ALERT -> Log.i(TAG,"LULULULULUL")
                comp.CONTENT_CONFIG -> config(configJSON(JSONArray(thing)))
                comp.CONTENT_DATA -> data(thing)
                else -> throw Exception()
            }
        }

        private fun config(thing: Configuration) {
            if (thing.subbed) {
                prefs!!.edit().putBoolean("alert",true).apply()
            }
            else {
                prefs!!.edit().putBoolean("alert",false).apply()
            }
        }

        private fun data(thing: String) {
            val len = thing.length
            data += thing
            if (len > size)
                size = len
            if (thing[len-1] == ']' && thing[len-2] == ']') {
                ref.get()!!.mainText.text = "Data Received"
                Log.i("$TAG SIZE",size.toString() + " " + data.length.toString())
                val list = dataJSON(JSONArray(data))

                ref.get()!!.recyclerView.adapter = DataAdapter(list.toTypedArray())
                ref.get()!!.recyclerView.adapter?.notifyDataSetChanged()
                data = ""
            }
        }

        fun dataJSON(array: JSONArray) : List<DataReading> {
            val list: MutableList<DataReading> = mutableListOf()
            var i = 0
            while (!array.isNull(i)) {
                val type = array.getJSONArray(i).getString(0)
                val value = array.getJSONArray(i).getDouble(1)
                val time = array.getJSONArray(i).getString(2)
                list.add(DataReading(type,value.toFloat(), LocalDateTime.parse("${time.substring(0..9)}T${time.substring(11)}")))
                i++
            }
            return list.toList()
        }

        fun configJSON(array:JSONArray) : Configuration {

            val mac = array.getJSONArray(0).getString(0)
            val sub = array.getJSONArray(0).getInt(1).toBool()
            val guideline = array.getJSONArray(0).getString(2)

            return Configuration(mac,sub,guideline)
        }

        fun alertJSON(json : JSONObject) : String {
            throw NotImplementedError()
        }


        private fun connect(type: Int) {
            if (type == 1) { // connected
                ref.get()!!.buttonDisconnect.isEnabled = true
                ref.get()!!.buttonWrite.isEnabled = true
                ref.get()!!.buttonConnect.isEnabled = false
            } else {
                ref.get()!!.buttonDisconnect.isEnabled = false
                ref.get()!!.buttonWrite.isEnabled = false
                ref.get()!!.buttonConnect.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        mmHandler = MyHandler(mmWeakRef)

        if (!mmBTAdapter.isEnabled){
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent,1)
        }


        mmDevice = mmBTAdapter.getRemoteDevice(mmDeviceAddress)
        val pairedDevices: Set<BluetoothDevice>? = mmBTAdapter.bondedDevices
        val device = pairedDevices?.find { it.name.contains("Beacon 1") } ?: mmDevice

        if (pairedDevices != null) {
            for((i, x) in pairedDevices.withIndex()) {
                Log.i(TAG, "${x.name} $i")
            }
        }

        if (!prefs!!.getBoolean("alert",false)) {
            prefs!!.edit().putBoolean("alert",false).apply()
        }

        recyclerView = findViewById<RecyclerView>(R.id.Recycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = DataAdapter(arrayOf())
        }


        buttonConnect.setOnClickListener {
            mmBTService = BluetoothService(mmHandler, device)
            mmBTService.connect()
        }

        buttonDisconnect.setOnClickListener {
            mmBTService.disconnect()
        }

        val rndm = Random(45354345)
        stuffButton.setOnClickListener {
            mmBTService.get(comp.getValue("CO2",">", rndm.nextInt((0..7000)).toFloat()))
        }

        buttonWrite.setOnClickListener {
            val dialog = InputDialog()
            dialog.show(mmManager, "InputDialog")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mmBTService.disconnect()
    }

    override fun onGET(text: String) {
        mainText.text = text
        mmBTService.get(text)
    }
    override fun onSET(text: String) {
        mainText.text = text
        mmBTService.set(text)
    }

}

class DataAdapter(private val dataSet: Array<DataReading>) : RecyclerView.Adapter<DataAdapter.MyViewHolder>() {

    inner class MyViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): MyViewHolder {
        val view = LayoutInflater.from(p0.context).inflate(R.layout.recycler, p0, false)

        return MyViewHolder(view)
    }

    override fun getItemCount(): Int = dataSet.size

    override fun onBindViewHolder(p0: MyViewHolder, p1: Int) {
        p0.view.textView.text = dataSet[p1].toString()
    }



}