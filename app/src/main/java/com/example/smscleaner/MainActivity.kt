package com.example.smscleaner

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

data class SmsItem(val id: Long, val address: String, val date: Long, val body: String)

class MainActivity : AppCompatActivity() {

    private val FILTER_TEXT_1 = "Nadawca SMSa"
    private val FILTER_TEXT_2 = "Jesli nie chcesz takich powiadomien, wyslij N na numer 8023"
    
    private val matchedSmsList = mutableListOf<SmsItem>()

    private lateinit var tvStatus: TextView
    private lateinit var tvResults: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scanSms()
        } else {
            Toast.makeText(this, "Brak uprawnień do odczytu SMS.", Toast.LENGTH_SHORT).show()
        }
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            confirmAndDelete()
        } else {
            Toast.makeText(this, "Wymagane ustawienie jako domyślna aplikacja SMS.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvResults = findViewById(R.id.tv_results)

        findViewById<Button>(R.id.btn_scan).setOnClickListener {
            checkPermissionAndScan()
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveToFile()
        }

        findViewById<Button>(R.id.btn_delete).setOnClickListener {
            if (matchedSmsList.isEmpty()) {
                Toast.makeText(this, "Najpierw wykonaj skanowanie!", Toast.LENGTH_SHORT).show()
            } else {
                ensureDefaultSmsAppAndDelete()
            }
        }
    }

    private fun checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            scanSms()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun scanSms() {
        matchedSmsList.clear()
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "address", "date", "body")
        
        val selection = "body LIKE ? OR body LIKE ?"
        val selectionArgs = arrayOf("%$FILTER_TEXT_1%", "%$FILTER_TEXT_2%")

        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")

        val sb = StringBuilder()

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow("_id")
            val addrCol = it.getColumnIndexOrThrow("address")
            val dateCol = it.getColumnIndexOrThrow("date")
            val bodyCol = it.getColumnIndexOrThrow("body")

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val address = it.getString(addrCol) ?: "Nieznany"
                val date = it.getLong(dateCol)
                val body = it.getString(bodyCol) ?: ""

                val item = SmsItem(id, address, date, body)
                matchedSmsList.add(item)

                sb.append("Row: _id=$id, address=$address, date=$date, body=$body\n\n")
            }
        }

        tvStatus.text = "Znaleziono wiadomości: ${matchedSmsList.size}"
        tvResults.text = sb.toString()
    }

    private fun saveToFile() {
        if (matchedSmsList.isEmpty()) {
            Toast.makeText(this, "Brak danych do zapisu. Wykonaj najpierw skanowanie.", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(getExternalFilesDir(null), "sms_do_usuniecia.txt")
        file.writeText(tvResults.text.toString())

        Toast.makeText(this, "Zapisano do: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun ensureDefaultSmsAppAndDelete() {
        if (isDefaultSmsApp()) {
            confirmAndDelete()
        } else {
            requestDefaultSmsRole()
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            packageName == Telephony.Sms.getDefaultSmsPackage(this)
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            roleRequestLauncher.launch(intent)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT_SMS).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            roleRequestLauncher.launch(intent)
        }
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Potwierdzenie")
            .setMessage("Czy chcesz usunąć ${matchedSmsList.size} wiadomości pasujących do filtra?")
            .setPositiveButton("Usuń") { _, _ -> deleteSms() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun deleteSms() {
        var deletedCount = 0

        for (sms in matchedSmsList) {
            val deleteUri = Uri.parse("content://sms/${sms.id}")
            val rows = contentResolver.delete(deleteUri, null, null)
            if (rows > 0) deletedCount++
        }

        Toast.makeText(this, "Pomyślnie usunięto: $deletedCount SMS-ów.", Toast.LENGTH_LONG).show()
        scanSms()
    }
}
