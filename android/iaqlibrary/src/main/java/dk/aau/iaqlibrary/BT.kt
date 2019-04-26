package dk.aau.iaqlibrary

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


private const val TAG = "BLUETOOTH_SERVICE_DEBUG"

// Defines several constants used when transmitting messages between the
// service and the UI.
const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1
const val MESSAGE_TOAST: Int = 2
const val MESSAGE_CONNECT: Int = 3
private val formatter = DateTimeFormatter.ofPattern("dd-mm-yyyy:hh.mm.ss")
// ... (Add other message types here as needed.)

class MyBluetoothService(
    // handler that gets info from Bluetooth service
    private val handler: Handler,
    device: BluetoothDevice) {

    private val mmSocket: BluetoothSocket =
        try {Log.i(TAG,device.name)
            device.createRfcommSocketToServiceRecord(UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"))}
        catch (e: Exception) { Log.e(TAG,e.toString())
            device.javaClass.getMethod("createRfcommSocket", (Int::class.javaPrimitiveType))
                .invoke(device,1) as BluetoothSocket }
        catch (e: Exception) {Log.e(TAG, e.toString()); throw Exception("No bluetooth connection could be created")}


    private inner class CommThread : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
                // Send the obtained bytes to the UI activity.
                val readMsg = handler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer.toString(Charset.defaultCharset()))
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = handler.obtainMessage(
                MESSAGE_WRITE, -1, -1, mmBuffer.toString(Charset.defaultCharset()))
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    private inner class ConnectThread : Thread() {

        override fun run() {
            //TODO: Discovery
            //BluetoothAdapter.getDefaultAdapter().cancelDiscovery()

            mmSocket.connect()

            /*mmSocket.use { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                //this@MyBluetoothService.CommThread().run()
            }*/
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    fun connect() {
        try {
            if (mmSocket.isConnected) {
                ConnectThread().start()}
            else{
                handler.obtainMessage(MESSAGE_CONNECT,-1,-1, "Cannot Connect").sendToTarget()
                throw Exception("Socket cannot be connected")
            }
            handler.obtainMessage(MESSAGE_CONNECT,-1,-1, "Connected!").sendToTarget()
            CommThread().start()
        }
        catch (e : Exception) {
            Log.e(TAG,e.message)
        }
    }

    fun disconnect() {
        try {
            CommThread().cancel()
            Thread.sleep(200)
            ConnectThread().cancel()
        }
        catch (e : Exception) {
            Log.e(TAG,e.message)
        }
    }

    private fun write(str: String) {
        try {
            val lex = LanguageLexer(CharStreams.fromString(str))
            val parser = LanguageParser(CommonTokenStream(lex))
            //TODO: Parser does not check the string
            if (true)
                CommThread().write(str.toByteArray())
            else
                throw Exception("Invalid String")
        }
        catch (e : Exception) {
            Log.e(TAG,e.message)
        }
    }

    fun GET(str: String) {
        write("GET $str")
    }

    fun GET_timeInterval(gasType: String, from: LocalDateTime, to: LocalDateTime) {
        val fromDate = from.format(formatter)
        val toDate = to.format(formatter)
        GET("$gasType time $fromDate to $toDate")
    }
    fun GET_time(gasType: String, compare: String, time: LocalDateTime = LocalDateTime.now()) {
        val timeDate = time.format(formatter)
        GET("$gasType time $compare $timeDate")
    }

    fun GET_value(gasType: String,compare: String , value: Float = 0f) {
        GET("$gasType value $compare $value")
    }

    fun GET_alerts(gasType: String, alertType: String) {
        GET("$gasType alerts = $alertType")
    }
    fun GET_status(gasType: String) {
        GET("$gasType status")
    }

    fun SET(str: String) {
        write("SET $str")
    }

    fun SET_Guidelines(guideline: String) {
        SET("guideline $guideline")
    }
}
