package com.example.myapplication

data class BusRoute(
    val routeNum: String,
    val routeTitle: String,
    val coordinates: List<List<Pair<Double, Double>>> // List of coordinate pairs for the route
)