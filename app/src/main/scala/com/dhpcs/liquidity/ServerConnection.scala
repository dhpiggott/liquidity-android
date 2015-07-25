package com.dhpcs.liquidity

import java.io.IOException
import javax.net.ssl.SSLContext

import android.content.Context
import android.os.{Handler, HandlerThread, Looper}
import com.dhpcs.jsonrpc._
import com.dhpcs.liquidity.ServerConnection._
import com.dhpcs.liquidity.models._
import com.google.android.gms.common.{GooglePlayServicesNotAvailableException, GooglePlayServicesRepairableException, GooglePlayServicesUtil}
import com.google.android.gms.security.ProviderInstaller
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.ws.{WebSocket, WebSocketCall, WebSocketListener}
import okio.{Buffer, BufferedSource}
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

import scala.util.{Failure, Right, Success, Try}

object ServerConnection {

  PRNGFixes.apply()

  // TODO: Can this just be boolean?
  // TODO: Check NetworkUtils.isNetworkAvailable somewhere
  sealed trait ConnectionState

  case object CONNECTING extends ConnectionState

  case object CONNECTED extends ConnectionState

  case object DISCONNECTING extends ConnectionState

  case object DISCONNECTED extends ConnectionState

  trait ConnectionStateListener {

    def onStateChanged(connectionState: ConnectionState)

  }

  trait NotificationListener {

    def onNotificationReceived(notification: Notification)

  }

  trait ResponseCallback {

    def onErrorReceived(errorResponse: ErrorResponse)

    def onResultReceived(resultResponse: ResultResponse) = ()

    // TODO: Timeouts?

  }

  private case class PendingRequest(requestMessage: JsonRpcRequestMessage,
                                    callback: ResponseCallback,
                                    handler: Handler)

  private val ServerEndpoint = "https://liquidity.dhpcs.com/ws"
  // TODO
  private val PingPeriod = 5000L

  private def getSslSocketFactory(context: Context) = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      ClientKey.getKeyManagers(context),
      ServerTrust.getTrustManagers(context),
      null
    )
    sslContext.getSocketFactory
  }

}

class ServerConnection(context: Context,
                       connectionStateListener: ServerConnection.ConnectionStateListener,
                       connectionStateHandler: Handler,
                       notificationListener: ServerConnection.NotificationListener,
                       notificationHandler: Handler) extends WebSocketListener {

  private val log = LoggerFactory.getLogger(getClass)
  private val pingRunnable: Runnable = new Runnable() {
    def run() {
      Try {
        webSocket.sendPing(null)
        connectionHandler.postDelayed(pingRunnable, ServerConnection.PingPeriod)
      } match {
        case Failure(e) =>
          log.warn(s"Failed to send ping", e)
        case _ =>
      }
    }
  }

  Try(ProviderInstaller.installIfNeeded(context)) match {

    case Failure(e: GooglePlayServicesRepairableException) =>
      log.warn("GooglePlayServicesRepairableException", e)
      GooglePlayServicesUtil.showErrorNotification(e.getConnectionStatusCode, context)

    case Failure(e: GooglePlayServicesNotAvailableException) =>
      log.warn("GooglePlayServicesNotAvailableException", e)

    case _ =>

  }

  private val client = new OkHttpClient()
    .setSslSocketFactory(ServerConnection.getSslSocketFactory(context))
    .setHostnameVerifier(ServerTrust.getHostnameVerifier(context))
  private val defaultHandler = new Handler(Looper.getMainLooper)

  // TODO
  private var connectionHandler: Handler = _
  private var webSocketCall: WebSocketCall = _
  private var webSocket: WebSocket = _

  private var pendingRequests = Map.empty[Int, PendingRequest]
  private var commandIdentifier = 0

  def this(context: Context,
           connectionStateListener: ServerConnection.ConnectionStateListener,
           notificationListener: ServerConnection.NotificationListener) =
    this(context,
      connectionStateListener,
      new Handler(Looper.getMainLooper),
      notificationListener,
      new Handler(Looper.getMainLooper))

  private def asyncPost(handler: Handler)(body: => Unit) =
    handler.post(new Runnable() {
      override def run() = body
    })

  def connect() {
    if (connectionHandler != null) {
      sys.error("Already connecting/connected")
    }
    val handlerThread = new HandlerThread("Connection")
    handlerThread.start()
    connectionHandler = new Handler(handlerThread.getLooper)

    asyncPost(connectionHandler) {
      asyncPost(connectionStateHandler)(
        connectionStateListener.onStateChanged(ServerConnection.CONNECTING)
      )

      log.debug("Creating WebSocket call")
      this.webSocketCall = WebSocketCall.create(
        client,
        new com.squareup.okhttp.Request.Builder().url(ServerConnection.ServerEndpoint).build
      )
      this.webSocketCall.enqueue(ServerConnection.this)
    }
  }

  def disconnect(): Unit = disconnect(1000, "Bye")

  private def disconnect(code: Int, reason: String) {
    if (connectionHandler == null) {
      sys.error("Not connecting/connected")
    }

    asyncPost(connectionHandler) {
      asyncPost(connectionStateHandler)(
        connectionStateListener.onStateChanged(ServerConnection.DISCONNECTING)
      )

      if (webSocket == null) {
        log.debug("Cancelling WebSocket call")
        this.webSocketCall.cancel()
        this.webSocketCall = null
      } else {
        Try {
          log.debug("Closing WebSocket")
          this.webSocket.close(code, reason)
          this.webSocket = null
        } match {
          case Failure(e) =>
            log.warn("Failed to close WebSocket", e)
          case _ =>
        }
      }
    }
  }

  def isConnectingOrConnected = connectionHandler != null

  override def onClose(code: Int, reason: String) {
    log.debug("WebSocket closed. Reason: {}, Code: {}", reason, code)
    asyncPost(connectionHandler) {
      connectionHandler.removeCallbacks(pingRunnable)
      asyncPost(connectionStateHandler)(
        connectionStateListener.onStateChanged(ServerConnection.DISCONNECTED)
      )
      this.webSocketCall = null
      this.webSocket = null
      connectionHandler.getLooper.quit()
      connectionHandler = null
    }
  }

  override def onFailure(e: IOException, response: com.squareup.okhttp.Response) {
    log.warn("WebSocked failed. Response: {}, Exception: {}", response: Any, e: Any)
  }

  override def onMessage(payload: BufferedSource, `type`: WebSocket.PayloadType) {
    `type` match {

      case WebSocket.PayloadType.TEXT =>
        Try(Json.parse(payload.readUtf8)) match {

          case Failure(exception) =>
            log.warn("Invalid JSON: {}", exception)

          case Success(json) =>

            Json.fromJson[JsonRpcMessage](json).fold(
            errors => log.warn("Invalid JSON-RPC message: {}", errors), {

              case _: JsonRpcRequestMessage =>
                sys.error("Received JsonRpcRequestMessage")

              case jsonRpcResponseMessage: JsonRpcResponseMessage =>
                jsonRpcResponseMessage.id.fold(
                  log.warn("JSON-RPC message ID missing, eitherErrorOrResult={}",
                    jsonRpcResponseMessage.eitherErrorOrResult
                  )
                ) { id =>
                  id.right.toOption.fold(
                    log.warn("JSON-RPC message ID was not an Int, id={}", id)
                  ) { commandIdentifier =>
                    asyncPost(connectionHandler)(
                      pendingRequests.get(commandIdentifier).fold(
                        log.warn("No pending request exists with commandIdentifier={}",
                          commandIdentifier
                        )
                      ) { pendingRequest =>
                        pendingRequests = pendingRequests - commandIdentifier
                        Response.read(
                          jsonRpcResponseMessage,
                          pendingRequest.requestMessage.method
                        ).fold(
                        errors => log.warn("Invalid Response: {}", errors), {

                          case errorResponse: ErrorResponse =>
                            asyncPost(pendingRequest.handler)(
                              pendingRequest.callback.onErrorReceived(errorResponse)
                            )

                          case resultResponse: ResultResponse =>
                            asyncPost(pendingRequest.handler)(
                              pendingRequest.callback.onResultReceived(resultResponse)
                            )

                        })
                      }
                    )
                  }
                }

              case jsonRpcNotificationMessage: JsonRpcNotificationMessage =>
                Notification.read(jsonRpcNotificationMessage).fold(
                  log.warn("No notification type exists with method={}",
                    jsonRpcNotificationMessage.method
                  )
                )(_.fold(
                  errors =>
                    log.warn("Invalid Notification: {}", errors),
                  notification =>
                    asyncPost(connectionHandler)(
                      asyncPost(notificationHandler)(
                        notificationListener.onNotificationReceived(notification)
                      )
                    )
                ))

            })

        }

      case WebSocket.PayloadType.BINARY =>
        disconnect(1003, "I don't support binary data")

      case _ =>
        sys.error("Unknown payload type: " + `type`)

    }
    payload.close()
  }

  override def onOpen(webSocket: WebSocket, response: com.squareup.okhttp.Response) =
    asyncPost(connectionHandler) {
      log.debug("WebSocket opened")
      this.webSocketCall = null
      this.webSocket = webSocket
      asyncPost(connectionStateHandler)(
        connectionStateListener.onStateChanged(ServerConnection.CONNECTED)
      )
      connectionHandler.postDelayed(pingRunnable, ServerConnection.PingPeriod)
    }

  override def onPong(payload: Buffer) {
    val payloadString = if (payload != null) {
      val result = payload.readUtf8
      payload.close()
      result
    }
    // TODO: Timeouts
    log.trace("Received pong: {}", payloadString)
  }

  def sendCommand(command: Command,
                  responseCallback: ServerConnection.ResponseCallback,
                  responseHandler: Handler = defaultHandler) =
    asyncPost(connectionHandler) {
      if (this.webSocket == null) {
        sys.error("Not connected")
      }
      val jsonRpcRequestMessage = Command.write(command, Right(commandIdentifier))
      commandIdentifier = commandIdentifier + 1
      Try {
        this.webSocket.sendMessage(
          WebSocket.PayloadType.TEXT,
          new Buffer().writeUtf8(
            Json.stringify(
              Json.toJson(jsonRpcRequestMessage)
            )
          )
        )
        pendingRequests = pendingRequests +
          (jsonRpcRequestMessage.id.right.get ->
            PendingRequest(jsonRpcRequestMessage, responseCallback, responseHandler))
      } match {
        case Failure(e) =>
          log.warn(s"Failed to send: $jsonRpcRequestMessage", e)
        case _ =>
      }
    }

}
