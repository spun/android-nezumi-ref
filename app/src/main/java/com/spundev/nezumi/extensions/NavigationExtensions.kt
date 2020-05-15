package com.spundev.nezumi.extensions

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment

fun AppCompatActivity.findNavControllerWithFragmentContainerView(id: Int): NavController {
    val navHostFragment = supportFragmentManager.findFragmentById(id) as NavHostFragment
    return navHostFragment.navController
}