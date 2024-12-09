package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DarkModeViewModel : ViewModel() {
    private val _isDarkMode = MutableLiveData<Boolean>()
    val isDarkMode: LiveData<Boolean> get() = _isDarkMode

    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
    }

    companion object {
        private var instance: DarkModeViewModel? = null

        fun getInstance(): DarkModeViewModel {
            if (instance == null) {
                instance = ViewModelProvider.NewInstanceFactory().create(DarkModeViewModel::class.java)
            }
            return instance!!
        }
    }
}