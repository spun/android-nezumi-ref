package com.spundev.nezumi.ui.details

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import com.spundev.nezumi.R
import com.spundev.nezumi.databinding.DetailsOldFragmentBinding
import com.spundev.nezumi.model.formats
import com.spundev.nezumi.util.NoFilterArrayAdapter
import com.spundev.nezumi.util.autoCleared


class DetailsOldFragment : Fragment() {
    private val args: DetailsOldFragmentArgs by navArgs()

    // View binding
    private var binding by autoCleared<DetailsOldFragmentBinding>()

    private val viewModel: DetailsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DetailsOldFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Dropdown array adapter
        val dropdownMenuAdapter =
            NoFilterArrayAdapter<String>(
                requireContext(),
                R.layout.dropdown_menu_popup_item
            )

        // Observe and enable the button if the user selection is a valid format
        viewModel.isSelectionValid.observe(viewLifecycleOwner, Observer { isSelectionValid ->
            binding.downloadButton.isEnabled = isSelectionValid
        })

        binding.dropdownMenuAutoCompleteTextView.setAdapter(dropdownMenuAdapter)
        // Listen for changes on available formats
        viewModel.formatsList.observe(viewLifecycleOwner, Observer { formatsList ->
            // binding.dropdownMenuAutoCompleteTextView.dismissDropDown()
            // Clear dropdown menu content
            dropdownMenuAdapter.clear()

            formatsList?.map {
                formats[it.itag] ?: "Unknown (" + it.itag + ")"
            }?.let { formatStringsList ->
                dropdownMenuAdapter.addAll(formatStringsList)
            }
        })

        // Listen for dropdown selection changes
        binding.dropdownMenuAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            viewModel.setCurrentFormatSelectionPosition(position)
        }

        // Listen for download button click events
        binding.downloadButton.setOnClickListener {
            Toast.makeText(requireContext(), "Opening...", Toast.LENGTH_SHORT).show()

            // TODO: Use DownloadManager for the download

            // Use the browser to trigger the download
            val selectedFormat = viewModel.getSelectedFormat()
            // Launch the url in the default browser
            val intent = Intent(Intent.ACTION_VIEW)
            // Appending a title forces the browser to start the download instead
            // of opening the video (only in some formats)
            intent.data = Uri.parse(selectedFormat?.url)
            startActivity(intent)
        }

        if (savedInstanceState == null) {
            viewModel.setVideoId(args.videoId)
        }
    }
}