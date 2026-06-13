package com.app.nosatmosphereeffect.helper

import android.app.Activity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.app.nosatmosphereeffect.R
import com.google.android.material.textfield.TextInputLayout

/**
 * Wires the shared "Image Fit" chooser (res/layout/include_fit_chooser.xml) into a
 * crop screen. Populates the dropdowns, restores the initial selection, keeps the
 * "Empty Space Fill" dropdown visible only for modes that letterbox, and updates a
 * short hint describing the selected mode.
 *
 * Usage:
 *   val fit = FitChooser.attach(this, initialFit, initialFill)
 *   ... later, when the user confirms ...
 *   WallpaperFitHelper.setActiveModes(this, fit.fitMode, fit.fillMode)
 */
object FitChooser {

    class Selection internal constructor(
        var fitMode: String,
        var fillMode: String
    )

    private val fitOptions = arrayOf(
        "Screen Fill (Crop)",
        "Fit Image (Show All)",
        "Stretch",
        "Rotate to Fit (Landscape)"
    )
    private val fitValues = arrayOf(
        WallpaperFitHelper.MODE_FILL,
        WallpaperFitHelper.MODE_FIT,
        WallpaperFitHelper.MODE_STRETCH,
        WallpaperFitHelper.MODE_ROTATE_FIT
    )
    private val fillOptions = arrayOf("Black Bars", "Repeat Pattern", "Mirror Pattern")
    private val fillValues = arrayOf(
        WallpaperFitHelper.FILL_BLACK,
        WallpaperFitHelper.FILL_REPEAT,
        WallpaperFitHelper.FILL_MIRROR
    )

    fun attach(
        activity: Activity,
        initialFit: String = WallpaperFitHelper.MODE_FILL,
        initialFill: String = WallpaperFitHelper.FILL_BLACK,
        onChange: ((fit: String, fill: String) -> Unit)? = null
    ): Selection {
        val layoutFill = activity.findViewById<TextInputLayout>(R.id.layoutEmptyFill)
        val dropFit = activity.findViewById<AutoCompleteTextView>(R.id.dropdownImageFit)
        val dropFill = activity.findViewById<AutoCompleteTextView>(R.id.dropdownEmptyFill)
        val hint = activity.findViewById<TextView>(R.id.tvFitHint)

        val selection = Selection(initialFit, initialFill)

        dropFit.setAdapter(ArrayAdapter(activity, R.layout.item_dropdown, fitOptions))
        dropFill.setAdapter(ArrayAdapter(activity, R.layout.item_dropdown, fillOptions))

        dropFit.setText(fitOptions[fitValues.indexOf(initialFit).takeIf { it >= 0 } ?: 0], false)
        dropFill.setText(fillOptions[fillValues.indexOf(initialFill).takeIf { it >= 0 } ?: 0], false)

        fun refresh() {
            val letterboxed = selection.fitMode == WallpaperFitHelper.MODE_FIT ||
                    selection.fitMode == WallpaperFitHelper.MODE_ROTATE_FIT
            layoutFill?.visibility = if (letterboxed) View.VISIBLE else View.GONE
            hint?.text = when (selection.fitMode) {
                WallpaperFitHelper.MODE_FIT ->
                    "The whole image is shown. Zoom or drag to adjust; empty space uses your fill choice."
                WallpaperFitHelper.MODE_STRETCH ->
                    "The image is stretched to fill the screen."
                WallpaperFitHelper.MODE_ROTATE_FIT ->
                    "Landscape photos are rotated to fill the screen. Zoom or drag to adjust."
                else ->
                    "Pinch to zoom and drag to frame your wallpaper."
            }
        }
        refresh()
        // Sync the preview to the initial mode.
        onChange?.invoke(selection.fitMode, selection.fillMode)

        dropFit.setOnItemClickListener { _, _, position, _ ->
            selection.fitMode = fitValues[position]
            refresh()
            onChange?.invoke(selection.fitMode, selection.fillMode)
        }
        dropFill.setOnItemClickListener { _, _, position, _ ->
            selection.fillMode = fillValues[position]
            onChange?.invoke(selection.fitMode, selection.fillMode)
        }

        return selection
    }
}
