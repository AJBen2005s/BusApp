package com.example.myapplication.model

data class BusResponse(
    val header: Header,
    val entity: List<BusEntity>
)

data class Header(
    val gtfsRealtimeVersion: String,
    val timestamp: String
)

data class BusEntity(
    val id: String,
    val vehicle: Vehicle
)

data class Vehicle(
    val trip: Trip,
    val position: Position,
    val vehicle: VehicleDetails
)

data class Trip(
    val routeId: String
)

data class Position(
    val latitude: Double,
    val longitude: Double
)

data class VehicleDetails(
    val id: String,
    val label: String
)
