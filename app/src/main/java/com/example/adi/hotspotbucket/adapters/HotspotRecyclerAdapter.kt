package com.example.adi.hotspotbucket.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.adi.hotspotbucket.database.PlaceEntity
import com.example.adi.hotspotbucket.databinding.ItemHotspotBinding

class HotspotRecyclerAdapter(private val placesList: ArrayList<PlaceEntity>, private val displayOrEditHotspot: (position: Int) -> Unit): RecyclerView.Adapter<HotspotRecyclerAdapter.ViewHolderHistory>() {

    class ViewHolderHistory(binding: ItemHotspotBinding): RecyclerView.ViewHolder(binding.root) {
        val title = binding.placesRVTileTV
        val description = binding.placesRVDescriptionTV
        val image = binding.placesRVIV
        val hotspot = binding.itemRV
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderHistory {
        return ViewHolderHistory(ItemHotspotBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolderHistory, position: Int) {
        holder.title.text = placesList[position].title
        holder.description.text = placesList[position].description
        val imgUri = Uri.parse(placesList[position].image)
        holder.image.setImageURI(imgUri)
        holder.hotspot.setOnClickListener {
            displayOrEditHotspot.invoke(position)
        }
    }

    override fun getItemCount(): Int {
        return placesList.size
    }
}