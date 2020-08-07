package com.example.windyble

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.windyble.models.HiveConnection
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.add_hive_diag.*
import org.w3c.dom.Text

const val WindyTAG = "Windyble <<"
fun debug(s: String) = Log.d(WindyTAG, s)

class MainActivity : AppCompatActivity() {

    val hiveConnection: HiveConnection by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            addHiveDialogue()
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                    .setAction("Action", null).show()
        }

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

        AlertDialog.Builder(this)
                .setTitle("Add Hive")
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->

                    val addr = view.findViewById<TextInputEditText>(R.id.input_address).text.toString()
                    val port  = view.findViewById<TextInputEditText>(R.id.input_port).text.toString().toInt()
                    hiveConnection.connect("Windyble", addr, port)
                    debug("You clicked ok")

                }
                .show()
    }
}