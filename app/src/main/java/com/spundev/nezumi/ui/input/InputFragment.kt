package com.spundev.nezumi.ui.input

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.spundev.nezumi.databinding.InputFragmentBinding
import com.spundev.nezumi.util.autoCleared


class InputFragment : Fragment() {

    // View binding
    private var binding by autoCleared<InputFragmentBinding>()

    private val viewModel: InputViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = InputFragmentBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Go button click listener
        binding.inputGoButton.setOnClickListener {
            processCurrentUrlValue()
        }

        // Listen to the "Done" button on the keyboard
        binding.inputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                processCurrentUrlValue()
                true
            } else {
                false
            }
        }

        // Observe and show possible error messages
        viewModel.errorMessageResource.observe(viewLifecycleOwner, Observer { errorMsgResource ->
            binding.inputTextInputLayout.error = if (errorMsgResource != null) {
                requireContext().getString(errorMsgResource)
            } else {
                null    // This will clear any existing error message
            }
        })

        // Observe the navigateToDetailsScreen LiveData and Navigate when it isn't null
        // After navigating, call displayDetailsScreenComplete() so that the ViewModel is ready
        // for another navigation event.
        viewModel.navigateToDetailsScreen.observe(viewLifecycleOwner, Observer { videoId ->
            if (null != videoId) {
                val action =
                    InputFragmentDirections.actionInputFragmentToListDetailsFragment(videoId)
                findNavController().navigate(action)
                // Tell the ViewModel we've made the navigate call to prevent multiple navigation
                viewModel.displayDetailsScreenComplete()
            }
        })


        if (savedInstanceState == null) {
            // If we have a value as an argument, we pre-populate the editText.
            arguments?.let {
                it.getString("videoUrl")?.let { sharedText ->
                    binding.inputEditText.setText(sharedText)
                }
            }
        }
    }

    private fun processCurrentUrlValue() {
        val currentText: String = binding.inputEditText.text.toString()
        viewModel.processUrl(currentText)
    }
}