package com.example

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.event.EventHandler
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.image.PixelWriter
import javafx.scene.input.KeyCode
import javafx.scene.paint.Color
import javafx.stage.Stage
import java.lang.Math.pow
import kotlin.math.PI
import kotlin.math.pow

class Game : Application() {

    companion object {
        const val WIDTH = 800
        const val HEIGHT = 800
        const val CAMERA_WIDTH = 800
        const val CAMERA_HEIGHT = 800
    }

    private lateinit var mainScene: Scene
    private lateinit var graphicsContext: GraphicsContext

    private var lastFrameTime: Long = System.nanoTime()

    private val sceneObjects : MutableList<Intersectable> = arrayListOf()
    private val sceneLights : MutableList<Light> = arrayListOf()
    private val camera: Camera = Camera()

    private val ambientColor = Vector3(0.1f, 0.1f, 0.1f)
    private var scale = 10

    private lateinit var pixelWriter: PixelWriter

    // use a set so duplicates are not possible
    private val currentlyActiveKeys = mutableSetOf<KeyCode>()

    override fun start(mainStage: Stage) {
        mainStage.title = "Ray tracing"

        val root = Group()
        mainScene = Scene(root)
        mainStage.scene = mainScene

        val canvas = Canvas(WIDTH.toDouble(), HEIGHT.toDouble())
        root.children.add(canvas)

        prepareActionHandlers()
        
        graphicsContext = canvas.graphicsContext2D
        pixelWriter = graphicsContext.pixelWriter

        camera.set(
            position = Vector3(0f, 2f, 5f),
            lookAt = Vector3(0f, 2f, 0f),
            vup = Vector3(0f, 1f, 0f),
            fov = 60f * PI.toFloat() / 180f
        )

        sceneLights.add(Light(Vector3(1f, 1f, 1f), Vector3(1f, 1f, 1f)))

        val reflectiveMaterial =
            ReflectiveMaterial(Vector3(1f, 1f, 1f), Vector3(5f, 4f, 3f))
        val planeMaterial =
            RoughMaterial(Vector3(0.3f, 0.1f, 0.1f), Vector3(0.3f, 0.1f, 0.1f), 100f)
        val blueMaterial =
            RoughMaterial(Vector3(0.2f, 0.4f, 0.8f), Vector3(0.2f, 0.4f, 0.8f), 100f)
        val greenMaterial =
            RoughMaterial(Vector3(0.2f, 0.8f, 0.4f), Vector3(0.2f, 0.8f, 0.4f), 100f)
        val redMaterial =
            RoughMaterial(Vector3(0.8f, 0.2f, 0.2f), Vector3(0.8f, 0.2f, 0.2f), 100f)
        val imageMaterial =
            RoughMaterial(Image("uv-test.png"), Vector3(1f, 1f, 1f), 100f)


        sceneObjects.add(Sphere(Vector3(2f, 2f, 0f), 1f, blueMaterial))
        sceneObjects.add(Sphere(Vector3(0f, 2f, -2f), 1f, imageMaterial))
        sceneObjects.add(Sphere(Vector3(-2f, 2f, 0f), 1f, reflectiveMaterial))
        sceneObjects.add(Plane(Vector3(0f, 0f, 0f), Vector3(0f, 1f, 0f), planeMaterial))


        // Main loop
        object : AnimationTimer() {
            override fun handle(currentNanoTime: Long) {
                tickAndRender(currentNanoTime)
            }
        }.start()

        mainStage.show()
    }

    private fun prepareActionHandlers() {
        mainScene.onKeyPressed = EventHandler { event ->
            currentlyActiveKeys.add(event.code)
        }
        mainScene.onKeyReleased = EventHandler { event ->
            currentlyActiveKeys.remove(event.code)
        }
    }

    private fun firstHit(ray: Ray) : Hit {
        var firstHit = Hit()
        sceneObjects.forEach {
            val hit = it.Intersect(ray)
            if(hit.t > 0f && (firstHit.t < 0f || hit.t < firstHit.t)) firstHit = hit
        }
        if(dot(ray.direction, firstHit.normal) > 0f) firstHit.normal = -firstHit.normal
        return firstHit
    }

    private fun shadowHit(ray: Ray) : Boolean {
        for(obj in sceneObjects)
            if(obj.Intersect(ray).t > 0f) return true
        return false
    }

    private fun trace(ray: Ray, depth : Int = 0) : Vector3 {
        if(depth > 2) return ambientColor
        val hit = firstHit(ray)
        if(hit.t < 0f) return ambientColor
        hit.material.set(hit.u, hit.v)

        var outColor = Vector3(0f, 0f, 0f)
        when (hit.material.type) {
            MaterialType.ROUGH -> {
                outColor = hit.material.ambient * ambientColor
                sceneLights.forEach {
                    val cosTheta = dot(hit.normal, it.direction)
                    val shadowRay = Ray(hit.position + hit.normal * 0.0001f, it.direction)
                    if(cosTheta > 0f && !shadowHit(shadowRay)) {
                        outColor += it.powerDensity * hit.material.diffuse * cosTheta
                        val halfWay = normalize(-ray.direction + it.direction)
                        val cosDelta = dot(hit.normal, halfWay)
                        if(cosDelta > 0f)
                            outColor +=
                                it.powerDensity * hit.material.specular *
                                        cosDelta.toDouble().pow(hit.material.shininess.toDouble()).toFloat()
                    }
                }
            }
            MaterialType.REFLECTIVE -> {
                val reflectedDirection = ray.direction - hit.normal * dot(hit.normal, ray.direction) * 2f
                val cosa = -dot(ray.direction, hit.normal)
                val one = Vector3(1f, 1f, 1f)
                val F = hit.material.F0 + (one - hit.material.F0) * (1.0 - cosa).pow(5.0).toFloat()

                outColor += trace(Ray(hit.position + hit.normal * 0.0001f, reflectedDirection), depth + 1) *F
            }
        }
        outColor.x = outColor.x.coerceIn(0f, 1f)
        outColor.y = outColor.y.coerceIn(0f, 1f)
        outColor.z = outColor.z.coerceIn(0f, 1f)
        return outColor
    }

    private fun controlCamera(dt: Float) {
        val speed = 4f
        val facing = normalize(camera.lookAt - camera.position)
        val fov = 60f * PI.toFloat() / 180f
        val vup = Vector3(0f, 1f, 0f)
        if(currentlyActiveKeys.contains(KeyCode.W)) {
            val delta = facing * speed * dt
            val newPos = camera.position + delta
            val newLookAt = camera.lookAt + delta
            camera.set(newPos, newLookAt, vup, fov)
        }
        if(currentlyActiveKeys.contains(KeyCode.S)) {
            val delta = facing * speed * dt
            val newPos = camera.position - delta
            val newLookAt = camera.lookAt - delta
            camera.set(newPos, newLookAt, vup, fov)
        }
        if(currentlyActiveKeys.contains(KeyCode.D)) {
            val delta = camera.right * speed * dt
            val newPos = camera.position + delta
            val newLookAt = camera.lookAt + delta
            camera.set(newPos, newLookAt, vup, fov)
        }
        if(currentlyActiveKeys.contains(KeyCode.A)) {
            val delta = camera.right * speed * dt
            val newPos = camera.position - delta
            val newLookAt = camera.lookAt - delta
            camera.set(newPos, newLookAt, vup, fov)
        }
        if(currentlyActiveKeys.contains(KeyCode.SPACE)) {
            val delta = Vector3(0f, 1f, 0f) * speed * dt
            val newPos = camera.position + delta
            val newLookAt = camera.lookAt + delta
            camera.set(newPos, newLookAt, vup, fov)
        }
        if(currentlyActiveKeys.contains(KeyCode.C)) {
            val delta = Vector3(0f, 1f, 0f) * speed * dt
            val newPos = camera.position - delta
            val newLookAt = camera.lookAt - delta
            camera.set(newPos, newLookAt, vup, fov)
        }
    }

    private fun controlScale() {
        if(currentlyActiveKeys.contains(KeyCode.E)) scale -= 1
        if(currentlyActiveKeys.contains(KeyCode.Q)) scale += 1

        scale = scale.coerceIn(1, 30)
    }

    private fun tickAndRender(currentNanoTime: Long) {
        // the time elapsed since the last frame, in nanoseconds
        // can be used for physics calculation, etc
        val elapsedNanos = currentNanoTime - lastFrameTime
        lastFrameTime = currentNanoTime
        val elapsedMs = elapsedNanos / 1_000_000

        // clear canvas
        graphicsContext.clearRect(0.0, 0.0, WIDTH.toDouble(), HEIGHT.toDouble())

        controlCamera(elapsedMs / 1000f)
        controlScale()

        for(y in 0 until HEIGHT step scale) {
            for(x in 0 until WIDTH step scale) {
                val color = trace(camera.makeRay(x, y, CAMERA_WIDTH, CAMERA_HEIGHT))
                val colorVector = Color.color(
                    color.x.toDouble(),
                    color.y.toDouble(),
                    color.z.toDouble())
                for(_y in y until y+scale) {
                    for(_x in x until x + scale) {
                        pixelWriter.setColor(_x, HEIGHT - _y, colorVector)
                    }
                }

            }
        }

        // display crude fps counter

        if (elapsedMs != 0L) {
            graphicsContext.fill = Color.WHITE
            graphicsContext.fillText("${1000 / elapsedMs} fps", 10.0, 10.0)
        }
    }
}