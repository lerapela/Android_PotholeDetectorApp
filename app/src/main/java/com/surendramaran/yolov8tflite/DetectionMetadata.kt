package com.surendramaran.yolov8tflite

data class DetectionMetadata(
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: String,
    val status: String
)
