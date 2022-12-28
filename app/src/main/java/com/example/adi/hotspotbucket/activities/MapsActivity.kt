package com.example.adi.hotspotbucket.activities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.adi.hotspotbucket.R
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_LATITUDE
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_LONGITUDE
import com.example.adi.hotspotbucket.activities.MainActivity.Companion.GET_TITLE
import com.example.adi.hotspotbucket.databinding.ActivityMapsBinding
import com.example.adi.hotspotbucket.databinding.MapLayersBottomSheetLayoutBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetDialog


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var binding: ActivityMapsBinding? = null

    private var currentLocation: Location? = null
    private val locationReqCode = 1
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private var mMarker: MarkerOptions? = null

    private val fromBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim) }
    private val toBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim) }
    private var markerClicked = false

    private var latitude = 100000000.0
    private var longitude = 100000000.0
    private var mapForDisplay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        latitude = intent.getDoubleExtra(GET_LATITUDE, 100000000.0)
        longitude = intent.getDoubleExtra(GET_LONGITUDE, 100000000.0)
        mapForDisplay = !(latitude == 100000000.0 || longitude == 100000000.0)

        if (mapForDisplay) {
            binding?.selectLocation?.visibility = View.GONE
            binding?.fabPresentLocation?.visibility = View.GONE
        }

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
        binding?.back?.setOnClickListener { finish() }

        val preferences = getSharedPreferences(PREFERENCE, MODE_PRIVATE)
        val firstStart = preferences.getBoolean(FIRST_START, true)
        if (firstStart) {
            saveMapType()
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        binding?.fabPresentLocation?.setOnClickListener {
            fetchLocation()
        }

        binding?.fabMapLayers?.setOnClickListener {
            val dialogBinding = MapLayersBottomSheetLayoutBinding.inflate(layoutInflater)
            val dialog = BottomSheetDialog(this@MapsActivity, R.style.BottomSheetStyle)
            dialog.setContentView(dialogBinding.root)
            dialogBinding.closeDialog.setOnClickListener { dialog.dismiss() }
            showAlreadySelectedType(dialogBinding)
            val editor = preferences.edit()
            dialogBinding.defaultMapTypeIB.setOnClickListener {
                dialogBinding.defaultMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg_selected)
                dialogBinding.defaultMapTypeTV.setTextColor(Color.parseColor("#FFD60A"))
                dialogBinding.satelliteMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg)
                dialogBinding.satelliteMapTypeTV.setTextColor(Color.parseColor("#FFFFFFFF"))
                dialogBinding.terrainMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg)
                dialogBinding.terrainMapTypeTV.setTextColor(Color.parseColor("#FFFFFFFF"))
                mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                editor.putInt(MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL)
                dialog.dismiss()
                editor.apply()
            }
            dialogBinding.satelliteMapTypeIB.setOnClickListener {
                dialogBinding.satelliteMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg_selected)
                dialogBinding.satelliteMapTypeTV.setTextColor(Color.parseColor("#FFD60A"))
                dialogBinding.defaultMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg)
                dialogBinding.defaultMapTypeTV.setTextColor(Color.parseColor("#FFFFFFFF"))
                dialogBinding.terrainMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg)
                dialogBinding.terrainMapTypeTV.setTextColor(Color.parseColor("#FFFFFFFF"))
                mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                editor.putInt(MAP_TYPE, GoogleMap.MAP_TYPE_SATELLITE)
                dialog.dismiss()
                editor.apply()
            }
            dialogBinding.terrainMapTypeIB.setOnClickListener {
                dialogBinding.terrainMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg_selected)
                dialogBinding.terrainMapTypeTV.setTextColor(Color.parseColor("#FFD60A"))
                dialogBinding.satelliteMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg)
                dialogBinding.satelliteMapTypeTV.setTextColor(Color.parseColor("#FFFFFFFF"))
                dialogBinding.defaultMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg)
                dialogBinding.defaultMapTypeTV.setTextColor(Color.parseColor("#FFFFFFFF"))
                mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                editor.putInt(MAP_TYPE, GoogleMap.MAP_TYPE_TERRAIN)
                dialog.dismiss()
                editor.apply()
            }
            dialog.show()
        }

        binding?.selectLocation?.setOnClickListener {
            if (mMarker != null) {
                val intent = Intent()
                intent.putExtra(AddPlaceActivity.SELECTED_LOCATION_LATITUDE, mMarker?.position?.latitude)
                intent.putExtra(AddPlaceActivity.SELECTED_LOCATION_LONGITUDE, mMarker?.position?.longitude)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun saveMapType() {
        val preferences = getSharedPreferences(PREFERENCE, MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putInt(MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL)
        editor.putBoolean(FIRST_START, false)
        editor.apply()
    }

    private fun showAlreadySelectedType(dialogBinding: MapLayersBottomSheetLayoutBinding) {
        when (mMap.mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> {
                dialogBinding.defaultMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg_selected)
                dialogBinding.defaultMapTypeTV.setTextColor(Color.parseColor("#FFD60A"))
            }
            GoogleMap.MAP_TYPE_SATELLITE -> {
                dialogBinding.satelliteMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg_selected)
                dialogBinding.satelliteMapTypeTV.setTextColor(Color.parseColor("#FFD60A"))
            }
            GoogleMap.MAP_TYPE_TERRAIN -> {
                dialogBinding.terrainMapTypeIB.setBackgroundResource(R.drawable.ic_iv_bg_selected)
                dialogBinding.terrainMapTypeTV.setTextColor(Color.parseColor("#FFD60A"))
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val preferences = getSharedPreferences(PREFERENCE, MODE_PRIVATE)
        mMap.mapType = preferences.getInt(MAP_TYPE, 2)
        mMap.isBuildingsEnabled = true
        mMap.isIndoorEnabled = true

        if (!mapForDisplay) {
            fetchLocation()
            mMap.setOnMarkerClickListener {
                if (!markerClicked) {
                    markerIsClicked()
                }
                markerClicked = true
                false
            }
            mMap.setOnMapClickListener {
                if (markerClicked) {
                    markerIsUnClicked()
                }
                markerClicked = false
            }
        } else {
            val latLng = LatLng(latitude, longitude)
            mMarker = MarkerOptions().position(latLng).title(intent.getStringExtra(GET_TITLE))
            mMap.addMarker(mMarker!!)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
        }
    }

    private fun markerIsUnClicked() {
        binding?.selectLocation?.visibility = View.INVISIBLE
        binding?.fabPresentLocation?.visibility = View.VISIBLE
        binding?.fabMapLayers?.visibility = View.VISIBLE

        binding?.selectLocation?.isEnabled = false
        binding?.fabPresentLocation?.isEnabled = true
        binding?.fabMapLayers?.isEnabled = true

        binding?.selectLocation?.startAnimation(toBottom)
        binding?.fabPresentLocation?.startAnimation(fromBottom)
        binding?.fabMapLayers?.startAnimation(fromBottom)
    }

    private fun markerIsClicked() {
        binding?.selectLocation?.visibility = View.VISIBLE
        binding?.fabPresentLocation?.visibility = View.INVISIBLE
        binding?.fabMapLayers?.visibility = View.INVISIBLE

        binding?.selectLocation?.isEnabled = true
        binding?.fabPresentLocation?.isEnabled = false
        binding?.fabMapLayers?.isEnabled = false

        binding?.selectLocation?.startAnimation(fromBottom)
        binding?.fabPresentLocation?.startAnimation(toBottom)
        binding?.fabMapLayers?.startAnimation(toBottom)
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (isGpsEnabled()) {
                val task: Task<Location> = fusedLocationProviderClient!!.lastLocation
                task.addOnSuccessListener { location ->
                    if (location != null) {
                        mMap.clear()
                        currentLocation = location
                        val present = LatLng(currentLocation?.latitude!!, currentLocation?.longitude!!)
                        mMarker = MarkerOptions().position(present).title("Present")
                        mMap.addMarker(mMarker!!)
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(present))
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(present, 12f))
                    }
                }
            } else {
                turnOnGps()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showAlertDialog()
            } else {
                ActivityCompat.requestPermissions(this@MapsActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationReqCode)
            }
        }
    }

    private fun turnOnGps() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
            .setCancelable(true)
            .setPositiveButton(
                "Yes"
            ) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .setNegativeButton(
                "No"
            ) { dialog, _ -> dialog.cancel() }
        val alert = builder.create()
        alert.show()
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            locationReqCode -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchLocation()
                }
            }
        }
    }

//    override fun onActivityReenter(resultCode: Int, data: Intent?) {
//        super.onActivityReenter(resultCode, data)
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            if (isGpsEnabled()) {
//                val task: Task<Location> = fusedLocationProviderClient!!.lastLocation
//                task.addOnSuccessListener { location ->
//                    if (location != null) {
//                        currentLocation = location
//                        val present = LatLng(currentLocation?.latitude!!, currentLocation?.longitude!!)
//                        mMarker = MarkerOptions().position(present).title("Your location")
//                        mMap.addMarker(mMarker!!)
//                        mMap.moveCamera(CameraUpdateFactory.newLatLng(present))
//                    }
//                }
//            }
//        }
//    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {
        const val MAP_TYPE = "mapType"
        const val PREFERENCE = "pref"
        const val FIRST_START = "firstStart"
    }
}