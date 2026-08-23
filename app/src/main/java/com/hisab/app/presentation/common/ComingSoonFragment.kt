package com.hisab.app.presentation.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hisab.app.R

/**
 * One fragment class, reused for Accounts / People / Transactions / Reports
 * via the "screenTitle" nav argument — those screens are real, honest
 * placeholders for Phase 1, not fake buttons pretending to work. See the
 * project README for what's built vs. what's next.
 */
class ComingSoonFragment : Fragment(R.layout.fragment_coming_soon) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = arguments?.getString(ARG_SCREEN_TITLE) ?: getString(R.string.coming_soon_default_title)
        view.findViewById<TextView>(R.id.title).text = title
    }

    companion object {
        const val ARG_SCREEN_TITLE = "screenTitle"
    }
}
