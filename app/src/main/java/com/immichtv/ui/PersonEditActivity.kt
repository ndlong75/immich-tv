package com.immichtv.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.immichtv.R
import com.immichtv.api.ImmichClient
import com.immichtv.api.UpdatePersonRequest
import kotlinx.coroutines.launch

class PersonEditActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_PERSON_NAME = "person_name"
        const val EXTRA_PERSON_BIRTH = "person_birth"
        const val EXTRA_CURRENT_ASSET_ID = "current_asset_id"
    }

    private lateinit var personImage: ImageView
    private lateinit var nameInput: EditText
    private lateinit var birthInput: EditText
    private lateinit var saveButton: Button
    private lateinit var setPhotoButton: Button
    private lateinit var statusText: TextView

    private var personId: String = ""
    private var currentAssetId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_edit)

        personImage = findViewById(R.id.person_image)
        nameInput = findViewById(R.id.input_name)
        birthInput = findViewById(R.id.input_birth)
        saveButton = findViewById(R.id.btn_save)
        setPhotoButton = findViewById(R.id.btn_set_photo)
        statusText = findViewById(R.id.text_status)

        personId = intent.getStringExtra(EXTRA_PERSON_ID) ?: run { finish(); return }
        currentAssetId = intent.getStringExtra(EXTRA_CURRENT_ASSET_ID)

        // Pre-fill
        nameInput.setText(intent.getStringExtra(EXTRA_PERSON_NAME) ?: "")
        val birth = intent.getStringExtra(EXTRA_PERSON_BIRTH) ?: ""
        birthInput.setText(birth)

        // Load person thumbnail
        loadPersonImage()

        // Show/hide set photo button
        if (currentAssetId != null) {
            setPhotoButton.visibility = android.view.View.VISIBLE
        } else {
            setPhotoButton.visibility = android.view.View.GONE
        }

        saveButton.setOnClickListener { savePerson() }
        setPhotoButton.setOnClickListener { setPersonPhoto() }

        // Enter key navigation
        nameInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                birthInput.requestFocus(); true
            } else false
        }
        birthInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                saveButton.requestFocus(); true
            } else false
        }
    }

    private fun loadPersonImage() {
        val url = ImmichClient.personThumbnailUrl(personId)
        val headers = ImmichClient.authHeaders()
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build())

        Glide.with(this)
            .load(glideUrl)
            .transform(CenterCrop(), RoundedCorners(16))
            .placeholder(ColorDrawable(Color.parseColor("#2d3561")))
            .into(personImage)
    }

    private fun savePerson() {
        val name = nameInput.text.toString().trim()
        val birth = birthInput.text.toString().trim()

        if (name.isBlank()) {
            statusText.text = "Name cannot be empty"
            statusText.setTextColor(Color.parseColor("#f87171"))
            return
        }

        statusText.text = "Saving..."
        statusText.setTextColor(Color.parseColor("#fbbf24"))
        saveButton.isEnabled = false

        lifecycleScope.launch {
            try {
                ImmichClient.getApi().updatePerson(
                    personId,
                    UpdatePersonRequest(
                        name = name,
                        birthDate = birth.ifBlank { null }
                    )
                )
                statusText.text = "Saved!"
                statusText.setTextColor(Color.parseColor("#4ade80"))
                Toast.makeText(this@PersonEditActivity, "Person updated", Toast.LENGTH_SHORT).show()
                window.decorView.postDelayed({ finish() }, 1000)
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                statusText.setTextColor(Color.parseColor("#f87171"))
            } finally {
                saveButton.isEnabled = true
            }
        }
    }

    private fun setPersonPhoto() {
        val assetId = currentAssetId ?: return
        statusText.text = "Setting photo..."
        statusText.setTextColor(Color.parseColor("#fbbf24"))

        lifecycleScope.launch {
            try {
                ImmichClient.getApi().updatePerson(
                    personId,
                    UpdatePersonRequest(featureFaceAssetId = assetId)
                )
                statusText.text = "Photo updated!"
                statusText.setTextColor(Color.parseColor("#4ade80"))
                loadPersonImage()
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                statusText.setTextColor(Color.parseColor("#f87171"))
            }
        }
    }
}
