package com.spundev.nezumi.ui.details

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.spundev.nezumi.R
import com.spundev.nezumi.databinding.DetailsFragmentBinding
import com.spundev.nezumi.util.autoCleared

class DetailsFragment : Fragment() {

    private val args: DetailsFragmentArgs by navArgs()

    // View binding
    private var binding by autoCleared<DetailsFragmentBinding>()

    private val viewModel: DetailsViewModel by viewModels()

    private var selectedDownloadUrl = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DetailsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val detailsAdapter = DetailsAdapter(
            requireContext(),
            this::openInBrowser,
            this::download,
            this::copyToClipboard
        )
        binding.episodesRecyclerview.apply {
            adapter = detailsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        viewModel.formatsList.observe(viewLifecycleOwner, Observer(detailsAdapter::submitList))

        if (savedInstanceState == null) {
            viewModel.setVideoId(args.videoId)
        }
    }

    private fun openInBrowser(url: String) {
        // Launch the url in the default browser
        val intent = Intent(Intent.ACTION_VIEW)
        // TODO: Appending a "title" parameter forces the browser to start the download
        //  instead of opening the video (only in some formats)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private val requestWritePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // permission was granted. Do the task you need to do.
                viewModel.downloadWithOkHTTP(selectedDownloadUrl)
            } /* else { // permission denied. Disable the functionality. } */
        }


    private fun download(url: String) {
        // Don't unnecessarily request storage-related permissions for devices that run Android 10 or higher.
        val isScopedStorageAvailable = Build.VERSION.SDK_INT >= 29
        val isWriteExternalStorageGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!isScopedStorageAvailable && !isWriteExternalStorageGranted) {
            // Permission is not granted
            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar.make(
                    binding.detailsLayout,
                    getString(R.string.write_permission_rationale),
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    // We save the url in a scope the request permission response can read
                    selectedDownloadUrl = url
                    // Request permission
                    requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }.show()
            } else {
                // We save the url in a scope the request permission response can read
                selectedDownloadUrl = url
                // No explanation needed, we can request the permission.
                requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // Permission has already been granted
            viewModel.downloadWithOkHTTP(url)
        }
    }


    private fun copyToClipboard(url: String) {
        // Gets a handle to the clipboard service.
        ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)?.apply {
            // Creates a new text clip to put on the clipboard
            val clip: ClipData = ClipData.newPlainText("Download url", url)
            // Set the clipboard's primary clip.
            setPrimaryClip(clip)
        }
        Toast.makeText(
            requireContext(),
            getString(R.string.copied_to_clipboard_message),
            Toast.LENGTH_SHORT
        ).show()
    }
}