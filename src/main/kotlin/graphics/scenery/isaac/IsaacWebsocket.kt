package graphics.scenery.isaac

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
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
        @JsonProperty("framebuffer width")
        var framebufferWidth: Int? = null,
        @JsonProperty("framebuffer height")
        var framebufferHeight: Int? = null,
        var payload: String? = null
)

data class IsaacObserveRequest(
        val type: String = "observe",
        val stream: Int = 0,
        val dropable: Boolean = false,
        @JsonProperty("observer id")
        val observerId: Int = 0
)

class IsaacWebsocket(serverURI: URI) : WebSocketClient(serverURI, Draft_6455(emptyList(), listOf(Protocol("isaac-json-protocol")))) {

    val mapper = ObjectMapper(JsonFactory())
    var writer = mapper.writer().withDefaultPrettyPrinter()

    var connected = false

    var onReceivePayload = arrayListOf<(IsaacResponse, String) -> Unit>()

    var width = 0
    var height = 0

    init {
        mapper.registerModule(KotlinModule())
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        println("opened connection")
        connected = true
    }

    override fun onMessage(message: String) {
//        println(message)
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
            onReceivePayload.forEach { it.invoke(reply, payload) }
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        // The codecodes are documented in class org.java_websocket.framing.CloseFrame
        println("Connection closed by " + (if (remote) "remote peer" else "us") + " Code: " + code + " Reason: " + reason)
    }

    override fun onError(ex: Exception) {
        ex.printStackTrace()
        // if the error is fatal then onClose will be called additionally
    }

    fun observe() {
        val req = IsaacObserveRequest()
        send(writer.writeValueAsString(req))
    }

    fun waitForConnection() {
        while(!connected) {
            Thread.sleep(50)
        }

        println("Connected to ISAAC host")
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