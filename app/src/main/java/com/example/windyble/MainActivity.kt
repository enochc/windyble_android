package com.example.windyble

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.windyble.models.HiveConnection
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.add_hive_diag.view.*

const val WindyTAG = "Windyble <<"
fun debug(s: String) = Log.d(WindyTAG, s)

const val MyPREFERENCES = "MyPrefs"
const val ADDRESS = "addressKey"


class MainActivity : AppCompatActivity() {

    val hiveConnection: HiveConnection by viewModels()
    lateinit var addrPrefs:SharedPreferences



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Disconnect hive when activity is closed
        lifecycle.addObserver(hiveConnection)

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            addHiveDialogue()
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                    .setAction("Action", null).show()
        }

//        val ADDRESS = prefs.getString()
//        val editor = prefs.edit()

        addrPrefs = getSharedPreferences(MyPREFERENCES, MODE_PRIVATE)
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

    private fun addHiveDialogue() {
        val view = layoutInflater.inflate(R.layout.add_hive_diag, null)
        val savedAddress = addrPrefs.getString(ADDRESS, "10.0.2.2")
        view.input_address.setText(savedAddress)

        AlertDialog.Builder(this)
                .setTitle("Add Hive")
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->

                    val addr = view.findViewById<TextInputEditText>(R.id.input_address).text.toString()
                    if(addr != savedAddress) {
                        addrPrefs.edit().putString(ADDRESS, addr).apply()
                    }
                    val port  = view.findViewById<TextInputEditText>(R.id.input_port).text.toString().toInt()
                    hiveConnection.connect("Windyble", addr, port)
                    debug("You clicked ok")

                }
                .show()
    }
}