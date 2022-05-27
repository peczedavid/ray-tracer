package com.example

import kotlin.math.sqrt

data class Vector4(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 0f) {
    fun xyz() : Vector3 {
        return Vector3(x, y, z)
    }
}

data class Vector3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun minus(other: Vector3) : Vector3 {
        return Vector3(x - other.x, y - other.y, z - other.z)
    }

    operator fun div(other: Vector3) : Vector3 {
        return Vector3(x / other.x, y / other.y, z / other.z)
    }

    operator fun div(other: Float) : Vector3 {
        return Vector3(x / other, y / other, z / other)
    }

    operator fun times(other: Vector3) : Vector3 {
        return Vector3(x * other.x, y * other.y, z * other.z)
    }

    operator fun times(other: Float) : Vector3 {
        return Vector3(x * other, y * other, z * other)
    }

    operator fun plus(other: Vector3) : Vector3 {
        return Vector3(x + other.x, y + other.y, z + other.z)
    }

    operator fun unaryMinus() : Vector3 {
        return Vector3(-x, -y, -z)
    }
}

fun cross(v1: Vector3, v2: Vector3) : Vector3 {
    return Vector3(v1.y * v2.z - v1.z * v2.y,
                   v1.z * v2.x - v1.x * v2.z,
                   v1.x * v2.y - v1.y * v2.x)
}

fun normalize(v1: Vector3) : Vector3 {
    val length = length(v1)
    return v1 / length
}

fun length(v1: Vector3) : Float {
    return sqrt(v1.x * v1.x + v1.y * v1.y + v1.z * v1.z)
}

fun dot(v1: Vector3, v2: Vector3) : Float {
    return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z
}