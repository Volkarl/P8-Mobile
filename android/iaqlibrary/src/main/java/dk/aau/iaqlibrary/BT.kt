package dk.aau.iaqlibrary

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


private const val TAG = "BLUETOOTH_SERVICE_DEBUG"
private val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy:HH.mm.ss")

class BluetoothService(private val handler: Handler, private val device: BluetoothDevice) {
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    lateinit var mmSocket : BluetoothSocket
    private val mmCommThread = CommunicationThread()
    private val mmConnThread = ConnectThread()

    private fun createBluetoothSocket(device: BluetoothDevice) : BluetoothSocket {
        try {
            val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
            return  m.invoke(device, uuid) as BluetoothSocket
        } catch (e: Exception) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e)
        }
        return  device.createRfcommSocketToServiceRecord(uuid)
    }

    private inner class CommunicationThread : Thread() {
        private lateinit var mmInStream: InputStream
        private lateinit var mmOutStream: OutputStream


        override fun run() {
            try {
                mmInStream = mmSocket.inputStream
                mmOutStream = mmSocket.outputStream
            } catch (e: IOException) {

            }

            var buffer: ByteArray  // buffer store for the stream
            var bytes: Int // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available()
                    if (bytes != 0) {
                        buffer = ByteArray(1024)
                        SystemClock.sleep(100) //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available() // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes) // record how many bytes we actually read

                        if (bytes != 0)
                            handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget()
                        else
                            handler.obtainMessage(MESSAGE_EMPTY, bytes, -1, buffer).sendToTarget()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    handler.obtainMessage(MESSAGE_ERROR, 10, -1, "Read Error".toByteArray()).sendToTarget()
                    break
                }

            }
        }

        /* Call this from the main activity to send data to the remote device */
        fun write(input: String) {
            val bytes = input.toByteArray()           //converts entered String into bytes
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
            }

        }

        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket.close()
                this.interrupt()
            } catch (e: IOException) {
            }

        }
    }

    private inner class ConnectThread : Thread() {

        override fun run() {
            var fail = false

            try {
                mmSocket = createBluetoothSocket(device)
            } catch (e: IOException) {
                fail = true
            }

            // Establish the Bluetooth socket connection.
            try {
                mmSocket.connect()
            } catch (e: IOException) {
                try {
                    fail = true
                    mmSocket.close()
                    handler.obtainMessage(MESSAGE_ERROR, 10, -1, "!Connected".toByteArray())
                        .sendToTarget()
                } catch (e2: IOException) {
                    //insert code to deal with this
                }

            }

            if (!fail) {
                mmCommThread.start()

                handler.obtainMessage(MESSAGE_CONNECT, 9, -1, "Connected".toByteArray())
                    .sendToTarget()
            }
        }
        fun cancel() {
            try {
                this.interrupt()
                mmCommThread.cancel()
            } catch (e: Exception) {
                Log.e(TAG,e.toString())
            }

        }
    }

    fun connect() {
        mmConnThread.start()
    }

    fun disconnect() {
        mmConnThread.cancel()
    }

    private fun write(str: String) {
        try { mmCommThread.write(str) }
        catch (e : Exception) { Log.e(TAG,e.message) }
    }

    fun get(vararg args: String) {
        if (args.isNotEmpty()) {
            val str = args.foldRight("") {currentValue, result -> "$currentValue & $result" }.dropLast(3)
            write("GET $str")
        }
        else throw IllegalArgumentException("args cannot be empty")
    }

    fun set(vararg args: String) {
        if (args.isNotEmpty()) {
            val str = args.foldRight("") {currentValue, result -> "$currentValue & $result" }.dropLast(3)
            write("SET $str")
        }
        else throw IllegalArgumentException("args cannot be empty")
    }

    companion object {
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
        const val MESSAGE_CONNECT: Int = 3
        const val MESSAGE_EMPTY: Int = 4
        const val MESSAGE_ERROR: Int = 5

        fun getTimeInterval(gasType: String, from: LocalDateTime, to: LocalDateTime) : String {
            val fromDate = from.format(formatter)
            val toDate = to.format(formatter)
            return ("$gasType time $fromDate to $toDate")
        }

        fun getTime(gasType: String, compare: String, time: LocalDateTime = LocalDateTime.now()) : String {
            val timeDate = time.format(formatter)
            return ("$gasType time $compare $timeDate")
        }

        fun getValue(gasType: String, compare: String = ">", value: Float = 0f) : String {
            return ("$gasType value $compare $value")
        }

        fun getAlerts(gasType: String, alertType: String = "predicted"): String {
            return ("$gasType alerts = $alertType")
        }

        fun getStatus(gasType: String): String {
            return ("$gasType status")
        }

        fun setGuidelines(guideline: String = "WHO") : String {
            return ("guideline $guideline")
        }

        fun dateTimeFormatter(time: LocalDateTime) : String {
            return time.format(formatter)
        }
    }
    }