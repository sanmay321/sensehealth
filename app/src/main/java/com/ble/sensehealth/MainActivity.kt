package com.ble.sensehealth

import kotlin.random.Random
import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ble.sensehealth.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.opencsv.CSVWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.IOException
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sin


class MainActivity : AppCompatActivity(), AdapterView.OnItemClickListener {
    val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val TIME_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID
    private val DEVICE_ADDRESS =   "A8:42:E3:A8:9F:A2" // Replace with your device's address
    lateinit var  device: BluetoothDevice
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected = false
    private lateinit var inputStream: InputStream
    private val discoveredDeviceNames = mutableListOf<String>()
    private val discoveredDeviceAdress = mutableListOf<String>()
    private lateinit var enableBtLauncher: ActivityResultLauncher<Intent>
    var clicked = false
    var Startclicked = true
    var StartclickedOnce = true
    var connected = false
    private var isDataProcessingActive = true

    //    private val discoveredDeviceAddresses = mutableListOf<String>()
    private lateinit var deviceListAdapter: DeviceListAdapter

    var randomFloatList = mutableListOf<Triple<Float, Float, Float>>()
    private val client = OkHttpClient()
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler()
    lateinit var  textView: TextView
    private val TAG = "MainActivity"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private lateinit var lineChart: LineChart
    private lateinit var lineDataSet: LineDataSet
    private lateinit var lineDataSet1: LineDataSet
    private lateinit var lineData: LineData
    //    private lateinit var lineData1: LineData
    private var xValue = 0f
    private var xValue1 = 0f
    private var xValue2 = 0f
    private val MAX_DATA_POINTS = 2000
    private var myThread: Thread? = null
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        if(sharedPreferences.getBoolean("changed", false)==false){
            editor.putInt("SamplingRate", 3200);
            editor.putInt("Brightness", 30);
            editor.putInt("SampleAverage", 32);
            editor.putString("LedMode", "Red Only");
            editor.putInt("PulseWidth", 411);
            editor.putInt("AdcRange", 16384);
            editor.putString("STRING_KEY", "{\"SamplingRate\":3200,\"Brightness\":30,\"SampleAverage\":32,\"LedMode\":1,\"PulseWidth\":411,\"AdcRange\":16384}")
        }
        editor.apply()
        deviceListAdapter = DeviceListAdapter(this@MainActivity, discoveredDeviceNames)

        lineChart = binding.plot
        lineDataSet = LineDataSet(mutableListOf(), "Live Data1")
//        lineDataSet1 = LineDataSet(mutableListOf(), "Live Data2")
        lineData = LineData(lineDataSet)
        lineChart.data = lineData
        // Customize X-axis text color
        lineChart.xAxis.textColor = Color.WHITE

        lineDataSet.apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircles(false)
            lineWidth = 2f
            color = Color.RED // Set color for the first data set
        }
        lineChart.xAxis.apply {
            textSize = 15f // Set the desired text size
            textColor = Color.WHITE // Optional: Set the text color
        }

//        lineDataSet1.apply {
//            mode = LineDataSet.Mode.CUBIC_BEZIER
//            setDrawCircles(false)
//            lineWidth = 2f
//            color = Color.BLUE // Set color for the second data set
//        }

// Customize Y-axis text color
        lineChart.axisLeft.textColor = Color.WHITE
        lineChart.axisRight.textColor = Color.GREEN

// Customize legend text color
        lineChart.legend.textColor = Color.YELLOW
        enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Bluetooth has been enabled, proceed with your setup
                showDeviceListDialog()
            } else {
                // User denied to enable Bluetooth, show a message or handle accordingly
                Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_SHORT).show()
            }
        }

        setupChart()

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        device= bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS)
        binding.startbutton.setOnClickListener {

//            val randomFloat = Random.nextFloat()
//
//            // Append the random float number to the public list
//            randomFloatList.add(randomFloat)
//            updateChart(randomFloat)
            if(connected){
                Startclicked = true
                binding.startbutton.setVisibility(View.INVISIBLE)
                binding.Stopbutton.setVisibility(View.VISIBLE)
                binding.advancebutton.isEnabled = false

                askMode()
//                if (StartclickedOnce) {
//                    StartclickedOnce = false
//                    val data = sharedPreferences.getString("STRING_KEY", "")
//                    if (data != null) {
//                        sendData(data.toByteArray())
//                    }
//                }
            }
        }
        binding.Stopbutton.setOnClickListener {
            Startclicked = false
//            bluetoothGatt?.disconnect()
            binding.startbutton.setVisibility(View.VISIBLE)
            binding.Stopbutton.setVisibility(View.INVISIBLE)
        }
        binding.clearbutton.setOnClickListener {
            clearChartData()
        }
        binding.savebutton.setOnClickListener{
//            GlobalScope.launch(Dispatchers.IO) {
            // Iterate over the randomFloatList
            // Call a function for each element
            clicked = true
            save()
//            }
        }

        binding.connectbutton.setOnClickListener {
            if (bluetoothAdapter.isEnabled) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_ENABLE_BT)

                } else {
                    Toast.makeText(this, "off", Toast.LENGTH_SHORT).show()
                    showDeviceListDialog()
                }
            }
        }
        binding.disconnectbutton.setOnClickListener {
            disconnectDevice()
            binding.connectbutton.setVisibility(View.VISIBLE)
            binding.disconnectbutton.setVisibility(View.INVISIBLE)
        }
        binding.advancebutton.setOnClickListener{
            val intent = Intent(this@MainActivity, advance::class.java)
            startActivity(intent)
        }
        // Request permissions and start scanning
    }

    private fun askMode() {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Enter file name")

            val inflater = layoutInflater
            val dialogLayout = inflater.inflate(R.layout.dialog_filename, null)
            builder.setView(dialogLayout)

            val filenameInput: EditText = dialogLayout.findViewById(R.id.filename_input)

            builder.setPositiveButton("OK") { dialog, which ->
                val fileName = filenameInput.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    sendData(fileName.toByteArray())
                } else {
                    Toast.makeText(this, "field cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }

                builder.show()
    }


    private fun connectToDevice(address: String) {
        bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != -1) {
            return
        }

        Log.d("Bluetooth", "Attempting to connect to device")

        Thread {
            try {
//                gatt = device.connectGatt(this, false, gattCallback)
                bluetoothSocket?.connect()
                isConnected = true
                Log.d("Bluetooth", "Connected to device")
                inputStream = bluetoothSocket!!.inputStream

                // Establish BLE connection
                startListeningForData()

            } catch (e: java.io.IOException) {
                Log.e("Bluetooth", "Error connecting to device", e)
                closeConnection()
            }
        }.start()
    }

    private fun closeConnection() {
        try {
            bluetoothSocket?.close()
            isConnected = false
        } catch (e: java.io.IOException) {
            Log.e("Bluetooth", "Error closing Bluetooth socket", e)
        }
    }
    private fun startListeningForData() {
        val buffer = ByteArray(1024)
        var bytes: Int

        while (isConnected) {
            try {
                if (inputStream.available() > 0) {
                    bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val receivedMessage = String(buffer, 0, bytes)
//                        handler.post {

                        try {
                            val x = mutableListOf<Float>()
                            val y1 = mutableListOf<Float>()
                            val y2 = mutableListOf<Float>()
                            val dataa = mutableListOf<List<String>>()

                            receivedMessage.split('\n').forEach { line ->
                                if (line.isNotBlank()) {
                                    val parts = line.split(',')
                                    if (parts.size == 3) {
                                        dataa.add(parts)
                                        val (b, c, a) = parts
                                        val bValue = b.toFloat()
                                        val cValue = c.toFloat()
                                        val aValue = a.toFloat()

                                        // Ensure thread safety when modifying the list
                                        synchronized(randomFloatList) {
                                            randomFloatList.add(Triple(bValue, cValue, aValue))
                                        }
//                                        x.add(a.toFloat())
//                                        y1.add(b.toFloat())
//                                        y2.add(c.toFloat())
                                        updateChart(b.toFloat(),c.toFloat(),a.toFloat())
//                                        Toast.makeText(this@MainActivity, "${b.toString()} + ${c.toString()} + ${a.toString()}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: NumberFormatException) {
                            println("Error converting notification data to float: $receivedMessage")
                        }
//                            dataList.add(BluetoothData(receivedMessage))
//                            bluetoothDataAdapter.notifyItemInserted(dataList.size - 1)
//                            recyclerView.scrollToPosition(dataList.size - 1)
//                        }
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e("Bluetooth", "Error reading from device", e)
                isConnected = false
            }
        }
    }













    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = Color.WHITE

            axisLeft.setDrawGridLines(false)
            axisLeft.textColor = Color.WHITE

            axisRight.isEnabled = false

            legend.form = Legend.LegendForm.LINE
            legend.textColor = Color.YELLOW
        }
    }
    private fun clearChartData() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Clear Data")
            .setMessage("Choose an action:")
            .setPositiveButton("Clear All Data") { dialog, which ->
                randomFloatList.clear()
                binding.Data.text = "0"
            }
            .setNegativeButton("Clear Graph") { dialog, which ->
                lineDataSet.clear()
                lineData.notifyDataChanged()
                lineChart.notifyDataSetChanged()
                lineChart.invalidate()
            }
            .create()
        dialog.show()
    }


    private fun updateChart(y1: Float, y2: Float,x:Float) {
        val entry1 = Entry(xValue1, y1)
//        val entry2 = Entry(xValue2, y2)

        xValue1 += 0.005f
        xValue2 += 0.005f

        runOnUiThread {
            // Add data points to both LineDataSets
            lineDataSet.addEntry(entry1)
//            lineDataSet1.addEntry(entry2)

            // Remove first entry if data set exceeds max points
            if (lineDataSet.entryCount > MAX_DATA_POINTS) {
                lineDataSet.removeFirst()
            }
//            if (lineDataSet1.entryCount > MAX_DATA_POINTS) {
//                lineDataSet1.removeFirst()
//            }

            // Notify data changes and refresh the chart
            lineDataSet.notifyDataSetChanged()
//            lineDataSet1.notifyDataSetChanged()
            lineData.notifyDataChanged()
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
        }
    }










// for bluetooth

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.")
                connected = true
                binding.textView.text = "Connected to ${gatt.device.name?: "Unknown Device"}"
//                binding.advancebutton.setEnabled(false)
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                binding.textView.text = "Facing problem to connected ${gatt.device.name?: "Unknown Device"}"
                Log.d(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS)   {
                val service: BluetoothGattService? = gatt.getService(YOUR_SERVICE_UUID)
                rxCharacteristic = service?.getCharacteristic(YOUR_RX_UUID)
                txCharacteristic = service?.getCharacteristic(YOUR_TX_UUID)
//                binding.connectbutton.setVisibility(View.INVISIBLE)
//                binding.disconnectbutton.setVisibility(View.VISIBLE)
                bluetoothGatt?.device?.let { connectToDevice(it.address) }
                txCharacteristic?.let { enableNotifications(gatt, it) }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            startDataProcessing()

            val receivedData = characteristic.value
            val receivedString = String(receivedData)
            runOnUiThread {
                receivedString.toFloatOrNull()?.let {
//                        if (isDataProcessingActive && characteristic.uuid == YOUR_TX_UUID )
//                            updateChart(it+1,it)
                }
//                    binding.textView.text = receivedString
            }

        }


    }
    fun startDataProcessing() {
        isDataProcessingActive = true
    }

    fun stopDataProcessing() {
        isDataProcessingActive = false
    }


    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device: BluetoothDevice = result.device
//            if (device.address == "40:4C:CA:F4:DC:B6" || device.address == "A8:42:E3:3D:92:B2") {
//                bluetoothGatt = device.connectGatt(this@MainActivity, false, bluetoothGattCallback)
//                bluetoothAdapter.bluetoothLeScanner.stopScan(this)
//            }
            if(device.type==2){
                if (!discoveredDeviceAdress.contains(device.address) && !discoveredDeviceNames.contains((device.name ?: "Unknown Device") + " : " + device.address)) {
                    // If not, add the device name and address to the lists
                    discoveredDeviceNames.add((device.name ?: "Unknown Device") + " : " + device.address)
                    discoveredDeviceAdress.add(device.address)
                }

            }
            // Update the ListView adapter
            deviceListAdapter.notifyDataSetChanged()
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }



    private fun scanLeDevice() {
        bluetoothAdapter.bluetoothLeScanner.startScan(leScanCallback)
    }
    private fun updateYValue(y:Float): Float {
        // Implement your logic here to get the y-value dynamically
        // For example, you might fetch the value from a sensor or perform some calculations
        return y
    }

    private fun sendData(data: ByteArray) {
        rxCharacteristic?.let {
            it.value = data
            bluetoothGatt?.writeCharacteristic(it)
        }
    }

    private fun disconnectDevice() {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
            bluetoothGatt = null
            rxCharacteristic = null
            txCharacteristic = null
            Startclicked = true
            connected = false
            StartclickedOnce = true
            binding.advancebutton.isEnabled = true
            runOnUiThread {
                Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show()
            }
        }
        binding.textView.text = "Not connected"
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_UUID))
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 3) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                createFolderAndCsvFile()
            } else {
                // Handle the case where permission is denied
                // E.g., show a message to the user explaining why the permission is needed
            }
        }

        if (requestCode == REQUEST_ENABLE_BT && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, start scanning
            showDeviceListDialog()
//            connectToDevice(DEVICE_ADDRESS)
        }
    }

    companion object {
        const val SAMPLE_SIZE = 10000
        private const val REQUEST_ENABLE_BT = 1
        val YOUR_SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val YOUR_RX_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val YOUR_TX_UUID = UUID.fromString("c8d9c1d0-1fb6-11eb-adc1-0242ac120002")
        const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }



























//for plot

    fun save(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createFolderAndCsvFile()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
            } else {
                createFolderAndCsvFile()
            }
        }



//        val permissions =
//            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
//
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val hasPermission = permissions.all { permission ->
//                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
//            }
//
//            if (!hasPermission) {
//                ActivityCompat.requestPermissions(this, permissions, 3)
//            } else {
//                createFolderAndCsvFile()
//            }
//        } else {
//            createFolderAndCsvFile()
//        }


//        val dirPath = Environment.getExternalStorageDirectory().absolutePath + "/MyApp"
//        val dir = File(dirPath)
//        dir.mkdirs()
//        val filePath = dirPath + "/example.csv"
//        val file = File(filePath)
//        val writer = CSVWriter(FileWriter(file))
//        val header = arrayOf("Name", "Age", "City")
//        writer.writeNext(header)
//        val row1 = arrayOf("John", "25", "New York")
//        val row2 = arrayOf("Sarah", "32", "London")
//        writer.writeNext(row1)
//        writer.writeNext(row2)
//        writer.close()
////        val cowriter = BufferedWriter(Input("/data/s/a.csv"))
////        val row = arrayOf("hi", "Sanmay")
////        cowriter.write("hi")
////        cowriter.close()
    }

    private fun createFolderAndCsvFile() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter file name")

        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_filename, null)
        builder.setView(dialogLayout)

        val filenameInput: EditText = dialogLayout.findViewById(R.id.filename_input)

        builder.setPositiveButton("OK") { dialog, which ->
            val fileName = filenameInput.text.toString().trim()
            if (fileName.isNotEmpty()) {
                createCsvFileWithFileName(fileName)
            } else {
                Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, which ->
            dialog.dismiss()
        }

        if(clicked){
            builder.show()
            clicked = false
        }
    }


    private fun createCsvFileWithFileName(fileName: String) {
        try {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PPG")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, "$fileName.csv")
            if (!file.exists()) {
                file.createNewFile()
            }

            val writer = CSVWriter(FileWriter(file, false))
            val header = arrayOf("y1", "y2", "x")
            val rows = randomFloatList.map { triple ->
                arrayOf(triple.first.toString(), triple.second.toString(), triple.third.toString())
            }
            writer.writeNext(header)

            var d = 0
            CSVWriter(FileWriter(file)).use { writer ->
                writer.writeNext(header)
                rows.forEach { writer.writeNext(it) }
            }
            writer.close()
            lineDataSet.clear()
            lineData.notifyDataChanged()
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            randomFloatList.clear()
            binding.Data.text = "0"
            Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selectedDeviceAddress = discoveredDeviceAdress[position]
        val selectedDevice = bluetoothAdapter.getRemoteDevice(selectedDeviceAddress)
        bluetoothGatt = selectedDevice.connectGatt(this@MainActivity, false, bluetoothGattCallback)
        bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
    }


    private fun showDeviceListDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select a device")

        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_device_list, null)
        builder.setView(dialogLayout)

        val deviceListView: ListView = dialogLayout.findViewById(R.id.device_list_view)
        deviceListAdapter = DeviceListAdapter(this@MainActivity, discoveredDeviceNames)
        deviceListView.adapter = deviceListAdapter

        val dialog = builder.create()
        dialog.show()

        deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedDeviceAddress = discoveredDeviceAdress[position]
            val selectedDevice = bluetoothAdapter.getRemoteDevice(selectedDeviceAddress)
            binding.connectbutton.setVisibility(View.INVISIBLE)
            binding.disconnectbutton.setVisibility(View.VISIBLE)
            bluetoothGatt = selectedDevice.connectGatt(this@MainActivity, false, bluetoothGattCallback)
            bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
            dialog.dismiss()
        }

        scanLeDevice()
    }


}