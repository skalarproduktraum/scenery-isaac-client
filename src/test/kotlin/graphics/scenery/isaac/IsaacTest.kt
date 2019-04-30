package graphics.scenery.isaac

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import org.junit.Test
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.*
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class IsaacTest : SceneryBase("ISAAC Client", 1280, 720) {
    lateinit var socket: IsaacWebsocket

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.2f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat(), 0.05f, 100.0f)
            active = true

            scene.addChild(this)
        }

        val lightbox = Box(GLVector(20.0f, 20.0f, 20.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = GLVector(0.4f, 0.4f, 0.4f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.Front

        scene.addChild(lightbox)

        (0..10).map {
            val light = PointLight(radius = 15.0f)
            light.emissionColor = Random.randomVectorFromRange(3, 0.0f, 1.0f)
            light.position = Random.randomVectorFromRange(3, -5.0f, 5.0f)
            light.intensity = 100.0f

            light
        }.forEach { scene.addChild(it) }

//        val plane = Box(GLVector(4.0f, 2.0f, 0.5f))
        val plane = FullscreenObject()
        scene.addChild(plane)

        plane.metadata["latestTimestamp"] = System.nanoTime()

        thread {
            while(!sceneInitialized()) {
                Thread.sleep(1000)
            }

            socket = IsaacWebsocket(URI("ws://${System.getProperty("isaac.host","127.0.0.1")}:2459"))
            socket.connect()
            socket.waitForConnection()
            socket.observe()
            socket.setProjection(cam.projection)

            socket.onReceivePayload.add { reply, payload, timestamp ->
                if(socket.width == 0 || socket.height == 0 || timestamp < (plane.metadata["latestTimestamp"] as Long)) {
                    return@add
                }

                plane.lock.withLock {
                    logger.debug("Got payload")
                    val texture = stringToImage(payload)
                    val buffer = BufferUtils.allocateByteAndPut(texture)
                    plane.material.textures.put("diffuse", "fromBuffer:deserialised")
                    plane.material.transferTextures.put("deserialised",
                            GenericTexture("texture",
                                    GLVector(socket.width.toFloat(), socket.height.toFloat(), 1.0f),
                                    3,
                                    contents = buffer))
                    plane.material.needsTextureReload = true
                    plane.metadata["latestTimestamp"] = timestamp
                    val rotation = GLMatrix.fromQuaternion(cam.rotation)
                    socket.setRotation(rotation)
                    socket.setTranslation(cam.position)
                }
            }
        }
    }

    fun stringToImage(base64: String): ByteArray {
        val bytes = Base64.getDecoder().decode(base64.substringAfter("data:image/jpeg;base64,"))
        val stream = ByteArrayInputStream(bytes)
        val image = ImageIO.read(stream)
        val data = (image.raster.dataBuffer as DataBufferByte).data
        var i = 0
        while (i < data.size) {
            val b = data[i]
            data[i] = data[i + 2]
            data[i + 2] = b
            i += 3
        }

        return data
    }

    @Test
    override fun main() {
        super.main()
    }
}