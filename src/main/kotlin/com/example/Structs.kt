package com.example

import javafx.scene.image.Image
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

data class Hit(var t: Float = -1f, var position: Vector3 = Vector3(),
               var normal: Vector3 = Vector3(), var material : Material = Material(),
               var u: Float = 0f, var v: Float = 0f)

data class Ray(val start: Vector3, val direction: Vector3)

data class Light(var direction: Vector3, var powerDensity: Vector3)

enum class MaterialType {
    ROUGH, REFLECTIVE
}

open class Material() {
    lateinit var type: MaterialType
    lateinit var diffuse: Vector3
    lateinit var specular: Vector3
    lateinit var F0: Vector3
    var shininess: Float = 0f
    lateinit var ambient: Vector3
    lateinit var image: Image
    var useTexture = false

    open fun set(u: Float, v: Float) {}
}

class ReflectiveMaterial(n: Vector3, kappa: Vector3) : Material() {
    init {
        val one = Vector3(1f, 1f, 1f)
        F0 = ((n - one) * (n - one) + kappa * kappa) / ((n + one) * (n + one) + kappa * kappa)
        type = MaterialType.REFLECTIVE
    }
}

class RoughMaterial(diffuse: Vector3, specular: Vector3, shininess: Float) : Material() {
    init {
        this.diffuse = diffuse
        this.specular = specular
        this.shininess = shininess
        this.ambient = diffuse * PI.toFloat()
        type = MaterialType.ROUGH
    }

    constructor(image: Image, specular: Vector3, shininess: Float) :
            this(Vector3(0f, 0f, 0f), specular, shininess){
        this.image = image
        useTexture = true
    }

    override fun set(u: Float, v: Float) {
       if(!useTexture) return
        var U = (1- u) * image.width
        var V = v * image.height
        if(U > image.width - 1) U = image.width - 1
        if(V > image.height - 1) V = image.height - 1
        val color = image.pixelReader.getColor(U.toInt(), V.toInt())
        ambient = Vector3(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat())
        diffuse = ambient / PI.toFloat()
    }
}


class Camera() {
    lateinit var position: Vector3
    lateinit var lookAt: Vector3
    lateinit var right: Vector3
    private lateinit var up: Vector3

    fun set(position: Vector3, lookAt: Vector3, vup: Vector3, fov: Float) {
        this.position = position
        this.lookAt = lookAt
        val view = position - lookAt
        val focus = length(view)
        right = normalize(cross(vup, view)) * focus * tan(fov / 2f)
        up = normalize(cross(view, right)) * focus * tan(fov / 2f)
    }

    fun makeRay(x: Int, y: Int, width: Int, height: Int) : Ray {
        val direction =
            lookAt + right * (2f * (x + 0.5f) / width - 1f) + up * (2f * (y + 0.5f) / height - 1f) - position
        return Ray(position, normalize(direction))
    }
}