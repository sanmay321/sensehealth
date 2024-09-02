package com.ble.sensehealth
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(context: Context, private val deviceNames: MutableList<String>) : ArrayAdapter<String>(context, 0, deviceNames) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false)
        }
        view?.findViewById<TextView>(android.R.id.text1)?.text = getItem(position)
        return view!!
    }
}
