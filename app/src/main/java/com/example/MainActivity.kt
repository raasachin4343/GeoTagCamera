package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.PhotoDatabase
import com.example.repository.PhotoRepository
import com.example.ui.CameraScreen
import com.example.ui.GalleryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CameraViewModel
import com.example.viewmodel.CameraViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup local storage Room database
        val database = PhotoDatabase.getDatabase(applicationContext)
        val repository = PhotoRepository(database.photoDao())

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    val cameraViewModel: CameraViewModel = viewModel(
                        factory = CameraViewModelFactory(repository)
                    )

                    NavHost(
                        navController = navController,
                        startDestination = "camera",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("camera") {
                            CameraScreen(
                                viewModel = cameraViewModel,
                                onNavigateToGallery = { navController.navigate("gallery") }
                            )
                        }
                        composable("gallery") {
                            GalleryScreen(
                                viewModel = cameraViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
