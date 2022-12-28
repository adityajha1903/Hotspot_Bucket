package com.example.adi.hotspotbucket.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.adi.hotspotbucket.R
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_DATE
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_DESCRIPTION
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_ID
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_LATITUDE
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_LOCATION
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_LONGITUDE
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_TITLE
import com.example.adi.hotspotbucket.databinding.ActivityHotspotDisplayEditBinding
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_URI
import com.example.adi.hotspotbucket.database.PlaceEntity
import com.example.adi.hotspotbucket.database.PlacesDatabase
import com.example.adi.hotspotbucket.databinding.BottomSheetDialogBinding
import com.example.adi.hotspotbucket.databinding.BottomSheetDialogSecondBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class HotspotDisplayEditActivity : AppCompatActivity(), View.OnClickListener {

    private var binding: ActivityHotspotDisplayEditBinding? = null

    private var id = 0
    private var uri = ""
    private var title = ""
    private var description = ""
    private var date = ""
    private var location = ""
    private var latitude = 100000000.0
    private var longitude = 100000000.0

    private var modifiedImgUri: Uri? = null
    private var modifiedImgFile: File? = null

    private val cameraReqCode = 1
    private val readReqCode = 0

    private val openCameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) {
        if (it) {
            binding?.hotspotDisplayImg?.setImageURI(modifiedImgUri)
            updateDatabase()
        } else {
            modifiedImgUri = null
            modifiedImgFile = null
        }
    }

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                if (modifiedImgUri != null && modifiedImgFile != null) {
                    if (modifiedImgFile?.exists()!!) {
                        modifiedImgFile?.delete()
                    }
                    modifiedImgUri = null
                    modifiedImgFile = null
                }
                binding?.hotspotDisplayImg?.setImageURI(result.data?.data)
                val viewBitmap = binding?.hotspotDisplayImg?.width
                    ?.let { Bitmap.createBitmap(it, binding?.hotspotDisplayImg?.height!!, Bitmap.Config.ARGB_8888) }
                val canvas = viewBitmap?.let { Canvas(it) }
                binding?.hotspotDisplayImg?.draw(canvas)
                modifiedImgUri = viewBitmap?.let { saveImageToInternalStorage(it) }
                updateDatabase()
            }
        }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri {
        val wrapper = ContextWrapper(applicationContext)
        modifiedImgFile = wrapper.getDir(AddPlaceActivity.IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        modifiedImgFile = File(modifiedImgFile, "${UUID.randomUUID()}.jpg")

        try {
            val stream: OutputStream = FileOutputStream(modifiedImgFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return Uri.parse(modifiedImgFile!!.absolutePath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot_display_edit)

        binding = ActivityHotspotDisplayEditBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        setSupportActionBar(binding?.hotspotDisplayToolBar)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finish()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            })
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding?.hotspotDisplayToolBar?.setNavigationOnClickListener {
            finish()
        }

        id = intent.getIntExtra(GET_ID, 0)
        uri = intent.getStringExtra(GET_URI)!!
        title = intent.getStringExtra(GET_TITLE)!!
        description = intent.getStringExtra(GET_DESCRIPTION)!!
        date = intent.getStringExtra(GET_DATE)!!
        location = intent.getStringExtra(GET_LOCATION)!!
        latitude = intent.getDoubleExtra(GET_LATITUDE, 100000000.0)
        longitude = intent.getDoubleExtra(GET_LONGITUDE, 100000000.0)

        binding?.hotspotDisplayImg?.setImageURI(Uri.parse(uri))
        binding?.hotspotDisplayToolBar?.title = title
        binding?.hotspotDisplayCollapsingAppBarLayout?.title = title
        binding?.titleTV?.text = title
        binding?.descriptionTV?.text = description
        binding?.dateTV?.text = date
        binding?.locationTV?.text = location

        binding?.getLocationOnMap?.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            intent.putExtra(GET_LATITUDE, latitude)
            intent.putExtra(GET_LONGITUDE, longitude)
            intent.putExtra(GET_TITLE, title)
            startActivity(intent)
        }

        binding?.editImageIB?.setOnClickListener(this)
        binding?.editTitle?.setOnClickListener(this)
        binding?.editDescription?.setOnClickListener(this)
        binding?.editDate?.setOnClickListener(this)
        binding?.editLocation?.setOnClickListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onClick(p0: View?) {
        when(p0?.id) {
            R.id.editImageIB -> {
                changeImage()
            }
            R.id.edit_title -> {
                showBottomSheetDialog(GET_TITLE, binding?.titleTV?.text.toString(), binding?.titleTV!!)
            }
            R.id.edit_description -> {
                showBottomSheetDialog(GET_DESCRIPTION, binding?.descriptionTV?.text.toString(), binding?.descriptionTV!!)
            }
            R.id.edit_date -> {
                showDatePicker()
            }
            R.id.edit_location -> {
                showBottomSheetDialog(GET_LOCATION, binding?.locationTV?.text.toString(), binding?.locationTV!!)
            }
        }
    }

    private fun updateDatabase() {
        val placesDb = Room.databaseBuilder(applicationContext, PlacesDatabase::class.java, "place_database").build()
        val placesDao = placesDb.placesDao()
        val newTitle = binding?.titleTV?.text
        val newDescription = binding?.descriptionTV?.text
        val newUri = if (modifiedImgUri != null) {
            modifiedImgUri.toString()
        } else {
            uri
        }
        val newDate = binding?.dateTV?.text
        val newLocation = binding?.locationTV?.text
        lifecycleScope.launch {
            val modifiedPlace = PlaceEntity(
                id,
                newTitle.toString(),
                newUri,
                newDescription.toString(),
                newDate.toString(),
                newLocation.toString(),
                latitude,
                longitude
            )
            placesDao.update(modifiedPlace)
            uri = newUri
            title = newTitle.toString()
            description = newDescription.toString()
            date = newDate.toString()
            location = newLocation.toString()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this@HotspotDisplayEditActivity, { _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            val format = "dd/MM/yyyy"
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            binding?.dateTV?.text = sdf.format(cal.time).toString()
            updateDatabase()
        }, date.substring(6, 10).toInt(), date.substring(3, 5).toInt(), date.substring(0, 2).toInt()).show()
    }

    @SuppressLint("SetTextI18n")
    private fun showBottomSheetDialog(detailType: String, oldValue: String, textView: TextView) {
        val dialogBinding = BottomSheetDialogSecondBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this@HotspotDisplayEditActivity, R.style.BottomSheetStyle)
        dialog.setContentView(dialogBinding.root)
        dialogBinding.closeDialog.setOnClickListener { dialog.dismiss() }
        dialogBinding.imagePickerTV.text = "Set $detailType"
        dialogBinding.eTModify.setText(oldValue)
        dialogBinding.save.setOnClickListener {
            if (dialogBinding.eTModify.text.isNullOrEmpty()) {
                Toast.makeText(this, "This section can't be empty", Toast.LENGTH_SHORT).show()
            } else {
                textView.text = dialogBinding.eTModify.text.toString()
                updateDatabase()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun changeImage() {
        val dialogBinding = BottomSheetDialogBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this@HotspotDisplayEditActivity, R.style.BottomSheetStyle)
        dialog.setContentView(dialogBinding.root)
        dialogBinding.closeDialog.setOnClickListener { dialog.dismiss() }
        dialogBinding.cameraIB.setOnClickListener {
            askPermissionForCamera()
            dialog.dismiss()
        }
        dialogBinding.galleryIB.setOnClickListener {
            askPermissionForRead()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun askPermissionForRead() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pickImageUsingGallery()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                showAlertDialog()
            } else {
                ActivityCompat.requestPermissions(
                    this@HotspotDisplayEditActivity,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                    readReqCode
                )
            }
        }
    }

    private fun askPermissionForCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pickImageUsingCamera()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                    Manifest.permission.CAMERA
                )) {
                showAlertDialog()
            } else {
                ActivityCompat.requestPermissions(
                    this@HotspotDisplayEditActivity,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                    cameraReqCode
                )
            }
        }
    }

    private fun showAlertDialog() {
        AlertDialog.Builder(this).setMessage("" +
                "It looks like you have turned off permission required " +
                "for this feature. It can be enabled under the " +
                "Application Settings")
            .setPositiveButton("SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") {dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun pickImageUsingGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        openGalleryLauncher.launch(intent)
    }

    private fun pickImageUsingCamera() {
        if (modifiedImgUri != null && modifiedImgFile != null) {
            if (modifiedImgFile?.exists()!!) {
                modifiedImgFile?.delete()
            }
            modifiedImgUri = null
            modifiedImgFile = null
        }
        modifiedImgFile = File(applicationContext.filesDir, "${UUID.randomUUID()}.jpg")
        modifiedImgUri = FileProvider.getUriForFile(applicationContext, "com.example.adi.hotspotbucket.fileProvider", modifiedImgFile!!)
        openCameraLauncher.launch(modifiedImgUri)
    }
}