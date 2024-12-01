package com.example.myapplication

data class BusRoute(
    val routeNum: String,
    val coordinates: List<List<Pair<Double, Double>>> // Each pair represents a [longitude, latitude] point
)