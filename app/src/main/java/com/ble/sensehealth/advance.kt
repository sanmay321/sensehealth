package com.ble.sensehealth

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ble.sensehealth.databinding.ActivityAdvanceBinding
import org.json.JSONObject

class advance : AppCompatActivity() {
    lateinit var binding: ActivityAdvanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val spinnerLedBrightness: EditText = findViewById(R.id.LedBrightness)
        val spinnerSampleAverage: Spinner = findViewById(R.id.spinnerSampleAverage)
        val spinnerLedMode: Spinner = findViewById(R.id.spinnerLedMode)
        val spinnerSampleRate: Spinner = findViewById(R.id.spinnerSampleRate)
        val spinnerPulseWidth: Spinner = findViewById(R.id.spinnerPulseWidth)
        val spinnerAdcRange: Spinner = findViewById(R.id.spinnerAdcRange)
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        binding.LedBrightness.setText(sharedPreferences.getInt("Brightness", 30).toString())
        initializeSpinner(spinnerSampleAverage, R.array.sample_average_options, sharedPreferences.getInt("SampleAverage", 32).toString())
        initializeSpinner(spinnerLedMode, R.array.led_mode_options, sharedPreferences.getString("LedMode", "Red only"))
        initializeSpinner(spinnerSampleRate, R.array.sample_rate_options, sharedPreferences.getInt("SamplingRate", 3200).toString())
        initializeSpinner(spinnerPulseWidth, R.array.pulse_width_options, sharedPreferences.getInt("PulseWidth", 69).toString())
        initializeSpinner(spinnerAdcRange, R.array.adc_range_options, sharedPreferences.getInt("AdcRange", 16384).toString())

        val buttonSubmit: Button = findViewById(R.id.buttonSubmit)
        buttonSubmit.setOnClickListener {
            // Check if any field is empty
            if (spinnerLedBrightness.text.isNullOrEmpty() ||
                spinnerSampleAverage.selectedItem == null ||
                spinnerLedMode.selectedItem == null ||
                spinnerSampleRate.selectedItem == null ||
                spinnerPulseWidth.selectedItem == null ||
                spinnerAdcRange.selectedItem == null) {

                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Retrieve selected values
            var ledBrightness: Int = spinnerLedBrightness.text.toString().toInt()
            if (ledBrightness < 0) ledBrightness = 0
            if (ledBrightness > 255) ledBrightness = 255

            val sampleAverage: Int = spinnerSampleAverage.selectedItem.toString().toInt()

            val ledModeString: String = spinnerLedMode.selectedItem.toString()
            val ledMode: Int = when (ledModeString) {
                "Red only" -> 1
                "Red + IR" -> 2
                "Red + IR + Green" -> 3
                else -> 0
            }

            val sampleRate: Int = spinnerSampleRate.selectedItem.toString().toInt()
            val pulseWidth: Int = spinnerPulseWidth.selectedItem.toString().toInt()
            val adcRange: Int = spinnerAdcRange.selectedItem.toString().toInt()

            // Use the values as needed
            val json = JSONObject().apply {
                put("SamplingRate", sampleRate)
                put("Brightness", ledBrightness)
                put("SampleAverage", sampleAverage)
                put("LedMode", ledMode)
                put("PulseWidth", pulseWidth)
                put("AdcRange", adcRange)
            }

            // Convert JSONObject to JSON string
            val jsonString = json.toString()
            editor.putString("STRING_KEY", jsonString)
            editor.putInt("SamplingRate", sampleRate);
            editor.putInt("Brightness", ledBrightness);
            editor.putInt("SampleAverage", sampleAverage);
            editor.putString("LedMode", ledModeString);
            editor.putInt("PulseWidth", pulseWidth);
            editor.putInt("AdcRange", adcRange);
            editor.putBoolean("changed", true);
            editor.apply()
            Toast.makeText(this, "All are added now you can click on the start button", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    private fun initializeSpinner(spiner : Spinner, ok : Int ,valueToFind: String? = null){

        val res = resources

        // Example: Find the position of value "100" in the sample_rate_options array
        val sampleRateOptions = res.getStringArray(ok)
        val position = valueToFind?.let { findPosition(sampleRateOptions, it) }
        if (position != null) {
            if (position >= 0) { // Check if position is valid
                    spiner.setSelection(position)
            }
        }
    }

    private fun findPosition(array: Array<String>, value: String): Int {
        for ((index, item) in array.withIndex()) {
            if (item == value) {
                return index
            }
        }
        return -1 // Return -1 if the value is not found
    }

}
