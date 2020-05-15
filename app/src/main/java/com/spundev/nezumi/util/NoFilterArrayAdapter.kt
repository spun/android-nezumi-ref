package com.spundev.nezumi.util

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

// Workaround to avoid the filtering of results that is applied to "AutoCompleteTextView" when rotating.
// Source: https://medium.com/@rmirabelle/there-is-no-material-design-spinner-for-android-3261b7c77da8
class NoFilterArrayAdapter<T>(context: Context, layout: Int) : ArrayAdapter<T>(context, layout) {

    private val filterThatDoesNothing = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val values: Array<String> = emptyArray()
            results.values = values
            results.count = values.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter {
        return filterThatDoesNothing
    }
}