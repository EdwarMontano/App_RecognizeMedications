// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/MainActivity.kt
package com.chocoplot.apprecognicemedications.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chocoplot.apprecognicemedications.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // La navegación la maneja el NavHostFragment definido en activity_main.xml
    }
}
