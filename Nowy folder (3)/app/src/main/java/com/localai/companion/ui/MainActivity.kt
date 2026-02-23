package com.localai.companion.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.localai.companion.R
import com.localai.companion.databinding.ActivityMainBinding

/**
 * Main activity that hosts the bottom navigation and fragment container.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Fragment instances
    private val chatFragment by lazy { ChatFragment() }
    private val modelsFragment by lazy { ModelsFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    private var activeFragment: Fragment = chatFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupBottomNavigation()
    }

    private fun setupFragments() {
        // Add all fragments but hide all except chat
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
            add(R.id.fragment_container, modelsFragment, "models").hide(modelsFragment)
            add(R.id.fragment_container, chatFragment, "chat")
        }.commit()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> {
                    switchFragment(chatFragment)
                    true
                }
                R.id.nav_models -> {
                    switchFragment(modelsFragment)
                    true
                }
                R.id.nav_settings -> {
                    switchFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            hide(activeFragment)
            show(fragment)
        }.commit()
        activeFragment = fragment
    }

    /**
     * Navigate to models tab programmatically.
     */
    fun navigateToModels() {
        binding.bottomNavigation.selectedItemId = R.id.nav_models
    }

    /**
     * Navigate to chat tab programmatically.
     */
    fun navigateToChat() {
        binding.bottomNavigation.selectedItemId = R.id.nav_chat
    }
}
