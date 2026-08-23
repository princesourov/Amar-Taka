package com.hisab.app.presentation.people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.databinding.FragmentCreatePersonBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

class CreatePersonFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreatePersonBinding? = null
    private val binding get() = _binding!!

    private val personId: String? get() = arguments?.getString("personId")
    private var editingPerson: PersonEntity? = null

    private val viewModel: PeopleViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePersonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editId = personId
        if (editId != null) {
            binding.formTitle.text = getString(R.string.edit_person_title)
            viewLifecycleOwner.lifecycleScope.launch {
                val person = viewModel.loadForEdit(editId)
                editingPerson = person
                person?.let { p ->
                    binding.nameInput.setText(p.name)
                    binding.phoneInput.setText(p.phone.orEmpty())
                    binding.emailInput.setText(p.email.orEmpty())
                    binding.notesInput.setText(p.notes.orEmpty())
                }
            }
        } else {
            binding.formTitle.text = getString(R.string.create_person_title)
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameInput.text.toString()
            val phone = binding.phoneInput.text.toString()
            val email = binding.emailInput.text.toString()
            val notes = binding.notesInput.text.toString()
            val existing = editingPerson
            if (existing != null) {
                viewModel.updatePerson(existing, name, phone, email, notes)
            } else {
                viewModel.createPerson(name, phone, email, notes)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.saveButton.isEnabled = !saving
                        binding.saveButton.text = getString(if (saving) R.string.action_saving else R.string.action_save)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CreatePersonEvent.Saved -> dismiss()
                            is CreatePersonEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_person_name_required, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
