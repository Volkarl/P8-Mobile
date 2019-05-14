package dk.aau.aiqshow

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import dk.aau.iaqlibrary.MyBluetoothService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime


open class SuperFragment : Fragment() {
    protected lateinit var callback: DialogListener
    protected lateinit var activityCallback: InputListener
    protected val _gasArray = arrayOf("CO","CO2","NO2","Humidity","Temperature")
    protected val _compArray = arrayOf("=",">","<")
    protected val _alertArray = arrayOf("predicted", "immediate")
    protected val TAG : String = "DEBUG_FRAGMENTS"

    interface InputListener {
        fun onSend(text: String)
    }

    interface DialogListener {
        fun onEnd()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        try {
            activityCallback = context as InputListener
        } catch (e: ClassCastException) {
            throw ClassCastException(context?.toString()
                    + " must implement DialogListener")
        }

        try {
            callback = parentFragment as DialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException(parentFragment?.toString()
                    + " must implement InputListener")
        }
    }

}

class TimeIntervalFragment : SuperFragment() {

    private lateinit var _gas : Spinner
    private lateinit var _timePickerFrom : TimePickerDialog
    private lateinit var _timePickerTo : TimePickerDialog
    private val _dateListenerFrom = DateTimePicker()
    private val _dateListenerTo = DateTimePicker()

    private val timeNow = LocalDateTime.now()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.timeinterval_fragment, container, false)

        val gasArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_gasArray)
        gasArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _gas = view.findViewById(R.id.TimeIntervalFragmentGas)
        _gas.adapter = gasArrayAdapter

        val datePickerFrom = DatePickerDialog(context!!)
        datePickerFrom.setOnDateSetListener(_dateListenerFrom)
        _timePickerFrom = TimePickerDialog(context!!,1,_dateListenerFrom,timeNow.hour,timeNow.minute,true)
        _dateListenerFrom.stuff(_timePickerFrom)

        val datePickerTo = DatePickerDialog(context!!)
        datePickerTo.setOnDateSetListener(_dateListenerTo)
        _timePickerTo = TimePickerDialog(context!!,1,_dateListenerTo,timeNow.hour,timeNow.minute,true)
        _dateListenerTo.stuff(_timePickerTo)


        view.findViewById<Button>(R.id.TimeIntervalFragmentFrom).setOnClickListener { datePickerFrom.show() }
        view.findViewById<Button>(R.id.TimeIntervalFragmentTo).setOnClickListener { datePickerTo.show() }
        view.findViewById<Button>(R.id.TimeIntervalFragmentPosBut).setOnClickListener { send() }
        view.findViewById<Button>(R.id.TimeIntervalFragmentNegBut).setOnClickListener { callback.onEnd() }

        return view
    }


    private fun send() {
        val message: String = MyBluetoothService.getTimeInterval(_gas.selectedItem.toString(), _dateListenerFrom.getTime(),
            _dateListenerTo.getTime())
        activityCallback.onSend(message)
        callback.onEnd()
    }
}

class TimeFragment : SuperFragment() {
    private lateinit var _gas : Spinner
    private lateinit var _comp : Spinner
    private lateinit var _time : TimePickerDialog
    private val _dateListener = DateTimePicker()
    private val timeNow = LocalDateTime.now()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.time_fragment, container, false)

        val gasArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_gasArray)
        gasArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _gas = view.findViewById(R.id.TimeFragmentGas)
        _gas.adapter = gasArrayAdapter

        val comparatorArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_compArray)
        comparatorArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _comp = view.findViewById(R.id.TimeFragmentComparator)
        _comp.adapter = comparatorArrayAdapter

        val datePicker = DatePickerDialog(context!!)
        datePicker.setOnDateSetListener(_dateListener)
        _time = TimePickerDialog(context!!,1,_dateListener,timeNow.hour,timeNow.minute, true)
        _dateListener.stuff(_time)

        view.findViewById<Button>(R.id.TimeFragmentTime).setOnClickListener { datePicker.show() }
        view.findViewById<Button>(R.id.TimeFragmentPosBut).setOnClickListener { send() }
        view.findViewById<Button>(R.id.TimeFragmentNegBut).setOnClickListener { callback.onEnd() }

        return view
    }

    private fun send() {
        val message: String = MyBluetoothService.getTime(_gas.selectedItem.toString(),_comp.selectedItem.toString(),_dateListener.getTime())
        activityCallback.onSend(message)
        callback.onEnd()
    }

}

class ValueFragment : SuperFragment() {
    private lateinit var _gas : Spinner
    private lateinit var _comp : Spinner
    private lateinit var _value : EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.value_fragment, container, false)

        _value = view.findViewById(R.id.ValueFragmentValue)

        val gasArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_gasArray)
        gasArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _gas = view.findViewById(R.id.ValueFragmentGas)
        _gas.adapter = gasArrayAdapter

        val comparatorArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_compArray)
        comparatorArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _comp = view.findViewById(R.id.ValueFragmentComparator)
        _comp.adapter = comparatorArrayAdapter


        view.findViewById<Button>(R.id.ValueFragmentPosBut).setOnClickListener { send() }
        view.findViewById<Button>(R.id.ValueFragmentNegBut).setOnClickListener { callback.onEnd() }

        return view
    }

    private fun send() {
        val gas = _gas.selectedItem.toString()
        val comp = _comp.selectedItem.toString()
        val value: Float = if (_value.text.toString().isNotEmpty()) _value.text.toString().toFloat() else 0F

        val message: String = MyBluetoothService.getValue(gas,comp,value)

        activityCallback.onSend(message)
        callback.onEnd()
    }

}

class AlertFragment : SuperFragment() {
    private lateinit var _gas : Spinner
    private lateinit var _alert : Spinner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.alert_fragment, container, false)

        val gasArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_gasArray)
        gasArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _gas = view.findViewById(R.id.AlertFragmentGas)
        _gas.adapter = gasArrayAdapter

        val alertArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_alertArray)
        alertArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _alert = view.findViewById(R.id.AlertFragmentAlerts)
        _alert.adapter = alertArrayAdapter


        view.findViewById<Button>(R.id.AlertFragmentPosBut).setOnClickListener { send() }
        view.findViewById<Button>(R.id.AlertFragmentNegBut).setOnClickListener { callback.onEnd() }

        return view
    }

    private fun send() {
        val message: String = MyBluetoothService.getAlerts(
            _gas.selectedItem.toString(),
            _alert.selectedItem.toString())

        activityCallback.onSend(message)
        callback.onEnd()
    }

}

class StatusFragment : SuperFragment() {
    private lateinit var _gas : Spinner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.status_fragment, container, false)

        val gasArrayAdapter = ArrayAdapter<String>(context!!,android.R.layout.simple_spinner_item,_gasArray)
        gasArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _gas = view.findViewById(R.id.StatusFragmentGas)
        _gas.adapter = gasArrayAdapter

        view.findViewById<Button>(R.id.StatusFragmentPosBut).setOnClickListener { send() }
        view.findViewById<Button>(R.id.StatusFragmentNegBut).setOnClickListener { callback.onEnd() }

        return view
    }

    private fun send() {
        val message: String = MyBluetoothService.getStatus(_gas.selectedItem.toString())

        activityCallback.onSend(message)
        callback.onEnd()
    }

}

class DateTimePicker : DialogFragment(), DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    private var _year : Int = 1970
    private var _month : Int = 1
    private var _day : Int = 1
    private var _hour : Int = 1
    private var _minute : Int = 1
    private lateinit var _tp : TimePickerDialog

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        _year = year
        _month = month
        _day = dayOfMonth
        _tp.show()
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        _hour = hourOfDay
        _minute = minute
    }

    fun getTime() : LocalDateTime {
        return LocalDateTime.of(LocalDate.of(_year,_month,_day), LocalTime.of(_hour,_minute))
    }

    fun stuff(tp : TimePickerDialog) {
        _tp = tp
    }
}