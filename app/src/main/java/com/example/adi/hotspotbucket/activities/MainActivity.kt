package com.example.adi.hotspotbucket.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.adi.hotspotbucket.R
import com.example.adi.hotspotbucket.adapters.HotspotRecyclerAdapter
import com.example.adi.hotspotbucket.database.PlaceEntity
import com.example.adi.hotspotbucket.database.PlacesDatabase
import com.example.adi.hotspotbucket.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private var lastId: Int? = null
    private var placeList = ArrayList<PlaceEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        setSupportActionBar(binding?.addPlacesToolBar)
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

        getPlaceList()

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val placesDb = Room.databaseBuilder(applicationContext, PlacesDatabase::class.java, "place_database").build()
                val placesDao = placesDb.placesDao()
                val position = viewHolder.layoutPosition
                val id = placeList[position].id
                val title = placeList[position].title
                val imageUri = placeList[position].image
                val description = placeList[position].description
                val date = placeList[position].date
                val location = placeList[position].location
                val latitude = placeList[position].latitude
                val longitude = placeList[position].longitude
                val entity = PlaceEntity(id, title, imageUri, description, date, location, latitude, longitude)
                lifecycleScope.launch {
                    placesDao.delete(entity)
                }
                getPlaceList()
                Snackbar.make(binding?.hotspotRV!!, "Hotspot $title deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        lifecycleScope.launch {
                            placesDao.insert(entity)
                            getPlaceList()
                        }
                    }.show()
            }

        }).attachToRecyclerView(binding?.hotspotRV)

        binding?.fABAddPlaces?.setOnClickListener {
            val intent = Intent(this, AddPlaceActivity::class.java)
            intent.putExtra("lats_place_id", lastId)
            startActivity(intent)
        }
    }

    private fun getPlaceList() {
        val placesDb = Room.databaseBuilder(applicationContext, PlacesDatabase::class.java, "place_database").build()
        val placesDao = placesDb.placesDao()
        lifecycleScope.launch {
            placesDao.fetchAllPlaces().collect {
                placeList = ArrayList(it)
                setUpRecyclerView()
            }
        }
    }

    private fun setUpRecyclerView() {
        if (placeList.isNotEmpty()) {
            lastId = placeList[placeList.size - 1].id
            binding?.noPlacesAddedNote?.visibility = View.GONE
            val adapter = HotspotRecyclerAdapter(placeList) { position ->
                val intent = Intent(this, HotspotDisplayEditActivity::class.java)
                intent.putExtra(GET_ID, placeList[position].id)
                intent.putExtra(GET_TITLE, placeList[position].title)
                intent.putExtra(GET_URI, placeList[position].image)
                intent.putExtra(GET_DESCRIPTION, placeList[position].description)
                intent.putExtra(GET_DATE, placeList[position].date)
                intent.putExtra(GET_LOCATION, placeList[position].location)
                intent.putExtra(GET_LATITUDE, placeList[position].latitude)
                intent.putExtra(GET_LONGITUDE, placeList[position].longitude)
                startActivity(intent)
            }
            val llm = LinearLayoutManager(this)
            binding?.hotspotRV?.adapter = adapter
            binding?.hotspotRV?.layoutManager = llm
        } else {
            binding?.noPlacesAddedNote?.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onResume() {
        getPlaceList()
        super.onResume()
    }

    companion object {
        const val GET_ID = "id"
        const val GET_TITLE = "Title"
        const val GET_URI = "uri"
        const val GET_DESCRIPTION = "Description"
        const val GET_DATE = "date"
        const val GET_LOCATION = "Location"
        const val GET_LONGITUDE = "longitude"
        const val GET_LATITUDE = "latitude"
    }
}