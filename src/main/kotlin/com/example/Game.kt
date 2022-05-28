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
import javafx.scene.input.MouseButton
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.Stage
import kotlin.math.PI
import kotlin.math.pow

class Game : Application() {

    companion object {
        var WIDTH = 1280
        var HEIGHT = 720
    }

    private lateinit var mainScene: Scene
    private lateinit var graphicsContext: GraphicsContext

    private var lastFrameTime: Long = System.nanoTime()

    private val sceneObjects : MutableList<Intersectable> = arrayListOf()
    private val sceneLights : MutableList<Light> = arrayListOf()
    private val camera: Camera = Camera()
    private var cameraFov = 60f

    private val ambientColor = Vector3(0.25f, 0.25f, 0.25f)
    private val movingScale = 15
    private var renderScaleSetting = 3
    private var renderScale = renderScaleSetting
    private var hud = true

    private lateinit var skySphere : Sphere
    private lateinit var pixelWriter: PixelWriter

    // use a set so duplicates are not possible
    private var keysBefore = mutableSetOf<KeyCode>()
    private val currentlyActiveKeys = mutableSetOf<KeyCode>()

    private lateinit var canvas : Canvas

    override fun start(mainStage: Stage) {
        mainStage.title = "Ray tracing"

        val root = Group()
        mainScene = Scene(root)
        mainStage.scene = mainScene

        canvas = Canvas(WIDTH.toDouble(), HEIGHT.toDouble())
        root.children.add(canvas)

        prepareActionHandlers()

        graphicsContext = canvas.graphicsContext2D
        pixelWriter = graphicsContext.pixelWriter

        camera.set(
            position = Vector3(0f, 1f, 3.5f),
            lookAt = Vector3(0f, 1f, 0f),
            vup = Vector3(0f, 1f, 0f),
            fov = cameraFov * PI.toFloat() / 180f
        )

        sceneLights.add(Light(Vector3(1f, 1f, 1f), Vector3(1f, 1f, 1f)))

        val uvImage = Image("/uv-test.png")

        val brass =
            ReflectiveMaterial(Vector3(0.444f, 0.527f, 1.094f), Vector3(3.695f, 2.765f, 1.829f))
        val copper =
            ReflectiveMaterial(Vector3(0.27105f, 0.67693f, 1.3164f), Vector3(3.6092f, 2.6248f, 2.2921f))
        val mercury =
            ReflectiveMaterial(Vector3(2.0733f, 1.55230f, 1.0606f), Vector3(5.3383f, 4.651f, 3.8628f))
        val iron =
            ReflectiveMaterial(Vector3(2.9114f, 2.9497f, 2.5845f), Vector3(3.0893f, 2.9318f, 2.767f))
        val silver =
            ReflectiveMaterial(Vector3(0.15943f, 0.14512f, 0.13547f), Vector3(3.9291f, 3.19f, 2.3808f))
        val gold =
            ReflectiveMaterial(Vector3(0.18299f, 0.42108f, 1.37340f), Vector3(3.4242f, 2.3459f, 1.7704f))
        val groundTexture =
            RoughMaterial(Image("/checker-board.jpg"), Vector3(0f, 0f, 0f))
        val glass =
            ReflectiveMaterial(Vector3(1.5f, 1.5f, 1.5f), Vector3(5f, 5f, 5f))
        val blueGlassMaterial =
            ReflectiveMaterial(Vector3(1f, 1f, 1f), Vector3(3f, 3f, 10f))
        val uvMaterial =
            RoughMaterial(uvImage, Vector3(0f, 0f, 0f), 100f)
        val skyMaterial =
            RoughMaterial(Image("/HDR_029_Sky_Cloudy_Bg.jpg"), Vector3(0f, 0f, 0f))
        skyMaterial.ambient = Vector3(1f, 1f, 1f)

        skySphere = Sphere(Vector3(0f, 0f, 0f), 100f, skyMaterial)
        sceneObjects.add(skySphere)

        sceneObjects.add(Sphere(Vector3(2f, 1f, 0f), 1f, blueGlassMaterial))
        sceneObjects.add(Sphere(Vector3(0f, 1f, -1f), 1f, copper))
        sceneObjects.add(Sphere(Vector3(-2f, 1f, 0f), 1f, silver))
        sceneObjects.add(Plane(Vector3(0f, 0f, 0f), Vector3(0f, 1f, 0f), groundTexture).apply { scale = 0.2f })

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
        sceneObjects.forEachIndexed { i, it ->
            val hit = it.Intersect(ray)
            if(i == 0) hit.skybox = true
            if(hit.t > 0f && (firstHit.t < 0f || hit.t < firstHit.t)) firstHit = hit
        }
        if(dot(ray.direction, firstHit.normal) > 0f) firstHit.normal = -firstHit.normal
        return firstHit
    }

    private fun shadowHit(ray: Ray) : Boolean {
        for(i in 1 until sceneObjects.size)
            if(sceneObjects[i].Intersect(ray).t > 0f) return true
        return false
    }

    private fun trace(ray: Ray, depth : Int = 0) : Vector3 {
        if(depth > 4f) return ambientColor
        val hit = firstHit(ray)
        if(hit.t < 0f) return ambientColor

        hit.material.set(hit.u, hit.v)

        var outColor = Vector3(0f, 0f, 0f)
        when (hit.material.type) {
            MaterialType.ROUGH -> {
                outColor = if(hit.skybox) hit.material.ambient else hit.material.ambient * ambientColor
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

                outColor += trace(Ray(hit.position + hit.normal * 0.0001f, reflectedDirection), depth + 1) * F
            }
        }
        outColor.x = outColor.x.coerceIn(0f, 1f)
        outColor.y = outColor.y.coerceIn(0f, 1f)
        outColor.z = outColor.z.coerceIn(0f, 1f)
        return outColor
    }

    private fun controlCamera(dt: Float) : Boolean {
        val speed = 4f
        val facing = normalize(camera.lookAt - camera.position)
        val fov = cameraFov * PI.toFloat() / 180f
        val vup = Vector3(0f, 1f, 0f)
        var moved = false
        if(currentlyActiveKeys.contains(KeyCode.W)) {
            val delta = facing * speed * dt
            val newPos = camera.position + delta
            val newLookAt = camera.lookAt + delta
            camera.set(newPos, newLookAt, vup, fov)
            moved = true
        }
        if(currentlyActiveKeys.contains(KeyCode.S)) {
            val delta = facing * speed * dt
            val newPos = camera.position - delta
            val newLookAt = camera.lookAt - delta
            camera.set(newPos, newLookAt, vup, fov)
            moved = true
        }
        if(currentlyActiveKeys.contains(KeyCode.D)) {
            val delta = camera.right * speed * dt
            val newPos = camera.position + delta
            val newLookAt = camera.lookAt + delta
            camera.set(newPos, newLookAt, vup, fov)
            moved = true
        }
        if(currentlyActiveKeys.contains(KeyCode.A)) {
            val delta = camera.right * speed * dt
            val newPos = camera.position - delta
            val newLookAt = camera.lookAt - delta
            camera.set(newPos, newLookAt, vup, fov)
            moved = true
        }
        if(currentlyActiveKeys.contains(KeyCode.SPACE)) {
            val delta = Vector3(0f, 1f, 0f) * speed * dt
            val newPos = camera.position + delta
            val newLookAt = camera.lookAt + delta
            camera.set(newPos, newLookAt, vup, fov)
            moved = true
        }
        if(currentlyActiveKeys.contains(KeyCode.C)) {
            val delta = Vector3(0f, 1f, 0f) * speed * dt
            val newPos = camera.position - delta
            val newLookAt = camera.lookAt - delta
            camera.set(newPos, newLookAt, vup, fov)
            moved = true
        }
        return moved
    }

    private fun controlScale() {
        if(currentlyActiveKeys.contains(KeyCode.E)) renderScaleSetting -= 1
        if(currentlyActiveKeys.contains(KeyCode.Q)) renderScaleSetting += 1

        renderScaleSetting = renderScaleSetting.coerceIn(1, 30)
    }

    private fun keyJustPressed(keyCode: KeyCode) : Boolean {
        return !keysBefore.contains(keyCode) && currentlyActiveKeys.contains(keyCode)
    }

    private fun controlHud() {
        if(keyJustPressed(KeyCode.H))
            hud = !hud
    }

    private fun tickAndRender(currentNanoTime: Long) {
        WIDTH = mainScene.width.toInt()
        HEIGHT = mainScene.height.toInt()
        canvas.width = WIDTH.toDouble()
        canvas.height = HEIGHT.toDouble()
        // the time elapsed since the last frame, in nanoseconds
        // can be used for physics calculation, etc
        val elapsedNanos = currentNanoTime - lastFrameTime
        lastFrameTime = currentNanoTime
        val elapsedMs = elapsedNanos / 1_000_000

        // clear canvas
        graphicsContext.clearRect(0.0, 0.0, WIDTH.toDouble(), HEIGHT.toDouble())
        val moved = controlCamera(elapsedMs / 1000f)
        renderScale = if(moved) movingScale else renderScaleSetting
        controlScale()
        controlHud()

        for(y in 0 until HEIGHT step renderScale) {
            for(x in 0 until WIDTH step renderScale) {
                val color = trace(camera.makeRay(x, y, WIDTH, HEIGHT))
                val colorVector = Color.color(
                    color.x.toDouble(),
                    color.y.toDouble(),
                    color.z.toDouble())
                for(_y in y until y+renderScale) {
                    for(_x in x until x + renderScale) {
                        pixelWriter.setColor(_x, HEIGHT - _y, colorVector)
                    }
                }

            }
        }

        // display crude fps counter

        if (elapsedMs != 0L) {
            if(hud) {
                graphicsContext.fill = Color.WHITE
                graphicsContext.font = Font.font(22.0)
                graphicsContext.fillText("$elapsedMs ms - $renderScale:1", 10.0, 27.0)
                graphicsContext.fillText("$WIDTH x $HEIGHT", 10.0, (HEIGHT - 10).toDouble())
            }
        }
        keysBefore = currentlyActiveKeys.toMutableSet()
    }
}
