package graphics.scenery.isaac

import cleargl.GLMatrix
import cleargl.GLVector
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.protocols.Protocol
import java.net.URI
import java.net.URISyntaxException

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

data class Stream(
        val name: String,
        val id: Int
)

data class IsaacResponse(
        var type: String? = null,
        var name: String? = null,
        var id: Int? = null,
        var nodes: Int? = null,
        var streams: List<Stream>? = null,
        var width: Int? = null,
        var height: Int? = null,
        var depth: Int? = null,
        var dimension: Int? = null,
        var projection: List<Float>? = null,
        var position: List<Float>? = null,
        var distance: Float? = null,
        var rotation: List<Float>? = null,
        var interpolation: Boolean? = null,
        var step: Float? = null,
        @JsonProperty("framebuffer width") var framebufferWidth: Int? = null,
        @JsonProperty("framebuffer height") var framebufferHeight: Int? = null,
        var payload: String? = null
)

data class IsaacObserveRequest(
        val type: String = "observe",
        val stream: Int = 0,
        val dropable: Boolean = false,
        @JsonProperty("observe id") val observeId: Int = 0
)

data class Feedback(
        var type: String = "feedback",
        @JsonProperty("observe id") var observeId: Int = 0,
        var projection: Array<Float>? = null,
        var modelview: Array<Float>? = null,
        @JsonProperty("rotation absolute") var rotation: Array<Float>? = null,
        @JsonProperty("position absolute") var translation: Array<Float>? = null
)

class IsaacWebsocket(serverURI: URI) : WebSocketClient(serverURI, Draft_6455(emptyList(), listOf(Protocol("isaac-json-protocol")))) {

    val mapper = ObjectMapper(JsonFactory())
    var writer: ObjectWriter

    var connected = false

    var onReceivePayload = arrayListOf<(IsaacResponse, String, Long) -> Unit>()

    var width = 0
    var height = 0

    init {
        mapper.registerKotlinModule()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        writer = mapper.writer().withDefaultPrettyPrinter()
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        println("opened connection")
        connected = true
    }

    override fun onMessage(message: String) {
        val reply = try {
           mapper.readValue(message, IsaacResponse::class.java)
        } catch (e: Exception) {
            System.err.println("Error: $e")
            null
        }

        val w = reply?.framebufferWidth
        val h = reply?.framebufferHeight
        if(w != null && h != null) {
            width = w
            height = h
        }

        val payload = reply?.payload
        if(payload != null) {
            onReceivePayload.forEach { it.invoke(reply, payload, System.nanoTime()) }
        } else {
//            println(message)
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        println("Connection closed by " + (if (remote) "remote peer" else "us") + " Code: " + code + " Reason: " + reason)
    }

    override fun onError(ex: Exception) {
        ex.printStackTrace()
    }

    fun observe() {
        val req = IsaacObserveRequest()
        sendCommand(writer.writeValueAsString(req))
    }

    fun waitForConnection() {
        while(!connected) {
            Thread.sleep(50)
        }

        println("Connected to ISAAC host")
    }

    fun setProjection(projection: GLMatrix) {
        val projf = Feedback(projection = projection.floatArray.toTypedArray())
        sendCommand(writer.writeValueAsString(projf))
    }

    fun setModelView(mv: GLMatrix) {
        val mvf = Feedback(modelview = mv.floatArray.toTypedArray())
        sendCommand(writer.writeValueAsString(mvf))
    }

    fun setRotation(rotation: GLMatrix) {
        val rot = rotation.floatArray
        val rf = Feedback(rotation = floatArrayOf(rot[0], rot[1], rot[2],
                rot[4], rot[5], rot[6],
                rot[8], rot[9], rot[10]).toTypedArray())
        sendCommand(writer.writeValueAsString(rf))
    }

    fun setTranslation(translation: GLVector) {
        val tf = Feedback(translation = translation.toFloatArray().toTypedArray())
        sendCommand(writer.writeValueAsString(tf))
    }

    fun sendCommand(command: String) {
//        println("Sending Command: $command")
        send(command)
    }

    companion object {

        @Throws(URISyntaxException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val c = IsaacWebsocket(URI("ws://${System.getProperty("isaac.host","127.0.0.1")}:2459"))
            c.connect()

            c.waitForConnection()
            c.observe()
        }
    }

}