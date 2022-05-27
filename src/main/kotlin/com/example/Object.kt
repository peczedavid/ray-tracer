package com.example

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

abstract class Intersectable(val material: Material) {
    abstract fun Intersect(ray: Ray) : Hit
}

class Sphere(var position: Vector3, var radius: Float, material: Material) : Intersectable(material) {
    override fun Intersect(ray: Ray) : Hit {
        val hit = Hit()
        val distance = ray.start - position
        val a = dot(ray.direction, ray.direction)
        val b = dot(distance, ray.direction) * 2f
        val c = dot(distance, distance) - radius * radius
        val discr = b * b - 4f * a * c
        if(discr < 0) return hit
        val sqrtDiscr = sqrt(discr)
        val t1 = (-b + sqrtDiscr) / 2f / a
        val t2 = (-b - sqrtDiscr) / 2f / a
        if(t1 <= 0f) return hit
        hit.t = if(t2 > 0f) t2 else t1
        hit.position = ray.start + ray.direction * hit.t
        hit.normal = (hit.position - position) * (1f / radius)
        hit.u = (atan2(hit.normal.z, hit.normal.x) / PI.toFloat() + 1f) / 2f
        hit.v = acos(hit.normal.y) / PI.toFloat()
        hit.material = material
        return hit
    }
}

class Plane(val point: Vector3, val normal: Vector3, material: Material) : Intersectable(material) {
    override fun Intersect(ray: Ray) : Hit {
        val hit = Hit()
        val distance = point - ray.start
        val num = dot(distance, normal)
        val denom = dot(ray.direction, normal)

        if(denom == 0f) return hit

        hit.t = num / denom
        hit.position = ray.start + ray.direction * hit.t
        hit.normal = normal
        hit.material = material
        return hit
    }
}