// https://developer.android.com/guide/topics/connectivity/bluetooth#kotlin

package com.example.windyble

import android.Manifest
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.windyble.databinding.AddHiveDiagBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
//import kotlinx.android.synthetic.main.add_hive_diag.view.*
import java.util.*


const val WindyTAG = "Windyble <<"
fun debug(s: String) {
    val stack = Thread.currentThread().stackTrace[3]
    val fullClassName = stack.className
    val className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1)
    val methodName = stack.methodName
    val lineNumber = stack.lineNumber

    Log.d("$className.$methodName():$lineNumber <<", s)
}

const val MyPREFERENCES = "MyPrefs"
const val ADDRESS = "addressKey"
const val REQUEST_ENABLE_BT = 1234
const val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 5678
const val MY_PERMISSIONS_REQUEST_READ_CONTACTS_FINE = 9123
const val PERIPHERAL_NAME = "Hive_Peripheral"

class MainActivity : AppCompatActivity() {

    val hiveConnection: HiveConnection by viewModels()
    var bluetoothAdapter: BluetoothAdapter? = null //BluetoothAdapter.getDefaultAdapter()
    lateinit var addrPrefs: SharedPreferences
    var bt_scan_dialogue:Dialog? = null
    var receiver_registered = false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        debug("onActivityResult $requestCode $resultCode $data")
    }



    val receiver = object : BroadcastReceiver() {
        var found = false
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            debug("ACTION: $action")
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    val deviceName = device.name
                    val deviceHardwareAddress = device.address // MAC address
                    debug("<< Found device: $deviceName :: $deviceHardwareAddress")

                    if (deviceName == PERIPHERAL_NAME ) {
                        if(!found){
                            found = true
                            bluetoothAdapter?.cancelDiscovery()

                            val prefs_key = "${deviceName}_address"

                            with(applicationContext.getSharedPreferences(MyPREFERENCES, MODE_PRIVATE).edit()){
                                debug("WRITE TO PREFS: $prefs_key = $deviceHardwareAddress")
                                putString(prefs_key,deviceHardwareAddress)
                                apply()
                            }

                            debug("Found Hive!! connecting...")
                            hiveConnection.connect_bt("Phone", deviceHardwareAddress, get_uuid())
                            bt_scan_dialogue?.dismiss()
                        }else{
                            debug("already found it")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Disconnect hive when activity is closed
        lifecycle.addObserver(hiveConnection)

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            addHiveDialogue()
        }
        findViewById<FloatingActionButton>(R.id.fab_blue).setOnClickListener { view ->
            addBlueHiveDialogue()
        }

        addrPrefs = getSharedPreferences(MyPREFERENCES, MODE_PRIVATE)

        val perm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (perm != PackageManager.PERMISSION_GRANTED) {
            debug("Permission is not granted");
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_READ_CONTACTS_FINE
            );
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            debug("Permission is not granted");
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                MY_PERMISSIONS_REQUEST_READ_CONTACTS
            )
        }


    }

    fun stop_scan(adapter:BluetoothAdapter?){
        adapter?.cancelDiscovery()
        unregisterReceiver(receiver)
        receiver_registered = false
    }

    private fun get_uuid(): UUID{
        var uuid:String? = addrPrefs.getString("MY_BT_UUID", null)
        if (uuid.isNullOrBlank()){
            uuid = UUID.randomUUID().toString()
            addrPrefs.edit().putString("MY_BT_UUID", uuid.toString()).apply()
        }
        return UUID.fromString(uuid)
    }

    fun scan_bluetooth(search_name:String): BluetoothAdapter? {
        val prefs_key = "${search_name}_address"
        // TODO remove this for production
        if (true){
            val mykey = "B8:27:EB:6D:A3:66"
            hiveConnection.connect_bt("Phone", mykey, get_uuid())
            bt_scan_dialogue?.dismiss()
            return null
        }

        addrPrefs.getString(prefs_key, null)?.let{
            debug("..............................$it")
            hiveConnection.connect_bt("Phone", it, get_uuid())
            bt_scan_dialogue?.dismiss()
            return null
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            debug("No bluetooth adapter !!!!!")
            // Device doesn't support Bluetooth
        }
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }


        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
        receiver_registered = true

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            debug("HAVE device: $deviceName, $deviceHardwareAddress")
        }

        val s = bluetoothAdapter?.startDiscovery()
        debug("Start discovery! $s")

        return bluetoothAdapter
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
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        debug("<<<<<<< PAUSE")
        HiveBluetoothGattCallback.unsubscribe()
        val hungup = hiveConnection.hangup()
        debug("<<<<<<< hungup: $hungup")
    }

    override fun onDestroy() {

        if(receiver_registered) {
            unregisterReceiver(receiver)
        }

        debug("<<<<<<< DESTROY")

        super.onDestroy()
    }

    private fun addBlueHiveDialogue() {
        val view = layoutInflater.inflate(R.layout.add_blue_hive, null)

        val adapter = scan_bluetooth(PERIPHERAL_NAME)?.let {
            bt_scan_dialogue = AlertDialog.Builder(this)
                .setTitle("Add Bluetooth Hive")
                .setView(view)
                .setNegativeButton(android.R.string.cancel){_,_ ->
                    stop_scan(it)
                }
                .show()
        }


    }

    private fun addHiveDialogue() {
//        val view = layoutInflater.inflate(R.layout.add_hive_diag, null)
        val view = AddHiveDiagBinding.inflate(layoutInflater)


        val savedAddress = addrPrefs.getString(ADDRESS, "10.0.2.2")
        view.inputAddress.setText(savedAddress)

        AlertDialog.Builder(this)
            .setTitle("Add Hive")
            .setView(view.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->

                val addr = view.inputAddress.text.toString()
                //view.findViewById<TextInputEditText>(R.id.input_address).text.toString()
                if (addr != savedAddress) {
                    addrPrefs.edit().putString(ADDRESS, addr).apply()
                }
                val port = view.inputPort.text.toString().toInt()
//                    view.findViewById<TextInputEditText>(R.id.input_port).text.toString().toInt()

                hiveConnection.connect("Windyble", addr, port)
                debug("You clicked ok")

            }
            .show()
    }
}