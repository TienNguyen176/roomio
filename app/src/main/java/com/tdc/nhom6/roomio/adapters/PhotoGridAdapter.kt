package com.tdc.nhom6.roomio.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R

class PhotoGridAdapter(
    private val context: Context,
    private val items:List<String>
): BaseAdapter() {
    override fun getCount(): Int =items.size

    override fun getItem(position: Int): Any =items[position]

    override fun getItemId(position: Int): Long =position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val inflater=LayoutInflater.from(context)
        var itemView=inflater.inflate(R.layout.item_photo,parent,false)
        val photo=itemView.findViewById<ImageView>(R.id.imgPhoto)
        val imageUrl=items[position]
        Glide.with(context)
            .load(imageUrl)
            .into(photo)
        return itemView
    }
}