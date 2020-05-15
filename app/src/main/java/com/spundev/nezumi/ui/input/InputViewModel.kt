package com.spundev.nezumi.ui.input

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.spundev.nezumi.R

class InputViewModel : ViewModel() {

    private val _errorMessageResource = MutableLiveData<Int>()
    val errorMessageResource: LiveData<Int> = _errorMessageResource

    // Internally, we use a MutableLiveData to handle navigation to the selected property
    private val _navigateToDetailsScreen = MutableLiveData<String>()
    // The external immutable LiveData for the navigation property
    val navigateToDetailsScreen: LiveData<String> = _navigateToDetailsScreen

    fun processUrl(url: String) {
        val regex = Regex(pattern = "^.*(youtu\\.be/|v/|u/\\w/|embed/|watch\\?v=|&v=)([^#&?]*).*")
        val matchResults = regex.matchEntire(url)
        _errorMessageResource.value =
            if (matchResults != null &&
                matchResults.groupValues.isNotEmpty() &&
                matchResults.groupValues[2].isNotEmpty()
            ) {
                displayDetailsScreen(matchResults.groupValues[2])
                null    // Clear any previous error message
            } else {
                R.string.input_fragment_error
            }
    }

    /**
     * When the property is clicked, set the [_navigateToDetailsScreen] [MutableLiveData]
     * @param videoId The Video ID from the original url.
     */
    private fun displayDetailsScreen(videoId: String) {
        _navigateToDetailsScreen.value = videoId
    }

    /**
     * After the navigation has taken place, make sure displayPropertyDetails is set to null
     */
    fun displayDetailsScreenComplete() {
        _navigateToDetailsScreen.value = null
    }
}