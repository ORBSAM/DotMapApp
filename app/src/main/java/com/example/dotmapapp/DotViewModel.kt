package com.example.dotmapapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.collections.minus
import kotlin.collections.plus

class DotViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DotStore(app)

    private val _dots = MutableStateFlow<List<Dot>>(emptyList())
    val dots = _dots.asStateFlow()

    private val _activeFilters = MutableStateFlow(DotColor.entries.toSet())
    val activeFilters = _activeFilters.asStateFlow()

    val visibleDots get() = dots.value.filter { it.color in activeFilters.value }

    init {
        _dots.value = store.load()
    }

    fun addDot(lat: Double, lng: Double, description: String, color: DotColor) {
        val updated = _dots.value + Dot(
            id = UUID.randomUUID().toString(),
            lat = lat, lng = lng, description = description, color = color
        )
        _dots.value = updated
        viewModelScope.launch { store.save(updated) }
    }

    fun deleteDot(id: String) {
        val updated = _dots.value.filterNot { it.id == id }
        _dots.value = updated
        viewModelScope.launch { store.save(updated) }
    }

    fun toggleFilter(color: DotColor) {
        val cur = _activeFilters.value
        _activeFilters.value = if (color in cur) cur - color else cur + color
    }
}