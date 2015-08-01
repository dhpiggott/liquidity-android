package com.dhpcs.liquidity

import java.io.IOException
import javax.net.ssl.SSLContext

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.ConnectivityManager
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

  sealed trait ConnectionState

  case object UNAVAILABLE extends ConnectionState

  case object AVAILABLE extends ConnectionState

  case object CONNECTING extends ConnectionState

  case object CONNECTED extends ConnectionState

  case object DISCONNECTING extends ConnectionState

  trait ConnectionStateListener {

    def onConnectionStateChanged(connectionState: ConnectionState)

  }

  trait NotificationListener {

    def onNotificationReceived(notification: Notification)

  }

  trait ResponseCallback {

    def onErrorReceived(errorResponse: ErrorResponse)

    def onResultReceived(resultResponse: ResultResponse) = ()

    // TODO: Timeouts?

  }

  private sealed trait State

  private case object AvailableIdleState extends State

  private case object UnavailableIdleState extends State

  private case class ActiveState(handler: Handler) extends State {

    var subState: SubState = _

  }

  private sealed trait SubState

  private case class ConnectingSubState(webSocketCall: WebSocketCall) extends SubState

  private case class ConnectedSubState(webSocket: WebSocket) extends SubState

  private case object DisconnectingSubState extends SubState

  private case class PendingRequest(requestMessage: JsonRpcRequestMessage,
                                    callback: ResponseCallback)

  private val ServerEndpoint = "https://liquidity.dhpcs.com/ws"
  // TODO
  private val PingPeriod = 5000L

  private def asyncPost(handler: Handler)(body: => Unit) =
    handler.post(new Runnable() {

      override def run() = body

    })

  private def getSslSocketFactory(context: Context) = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      ClientKey.getKeyManagers(context),
      ServerTrust.getTrustManagers(context),
      null
    )
    sslContext.getSocketFactory
  }

  PRNGFixes.apply()

}

class ServerConnection(context: Context,
                       connectionStateListener: ConnectionStateListener,
                       notificationListener: NotificationListener)
  extends WebSocketListener {

  Try(ProviderInstaller.installIfNeeded(context)) match {

    case Failure(e: GooglePlayServicesRepairableException) =>
      GooglePlayServicesUtil.showErrorNotification(e.getConnectionStatusCode, context)

    case Failure(e: GooglePlayServicesNotAvailableException) =>

    case _ =>

  }


  private val log = LoggerFactory.getLogger(getClass)

  private lazy val client = new OkHttpClient()
    .setSslSocketFactory(getSslSocketFactory(context))
    .setHostnameVerifier(ServerTrust.getHostnameVerifier(context))

  private val mainHandler = new Handler(Looper.getMainLooper)
  private val connectivityStateIntentFilter = new IntentFilter(
    ConnectivityManager.CONNECTIVITY_ACTION
  )
  private val connectivityStateReceiver = new BroadcastReceiver {

    override def onReceive(context: Context, intent: Intent) {
      intent.getAction match {

        case ConnectivityManager.CONNECTIVITY_ACTION =>
          handleConnectivityStateChange()

        case unmatched =>
          sys.error(s"Received unexpected broadcast for action $unmatched")

      }
    }

  }
  private val pingRunnable: Runnable = new Runnable() {

    override def run() =
      Try(state match {

        case AvailableIdleState | UnavailableIdleState =>
          sys.error("Not connected")

        case activeState: ActiveState =>
          activeState.subState match {

            case _: ConnectingSubState =>
              sys.error("Not connected")

            case connectedState: ConnectedSubState =>
              connectedState.webSocket.sendPing(null)
              activeState.handler.postDelayed(pingRunnable, PingPeriod)

            case DisconnectingSubState =>
              log.debug("Ping scheduled while disconnecting, doing nothing")

          }

      }) match {

        case Failure(e) =>
          log.warn("Failed to send ping", e)
          state match {

            case AvailableIdleState | UnavailableIdleState =>
              sys.error("Already disconnected")

            case activeState: ActiveState =>
              doClose(activeState.handler)

          }

        case _ =>

      }

  }

  private var pendingRequests = Map.empty[Int, PendingRequest]
  private var commandIdentifier = 0
  private var state: State = UnavailableIdleState

  private var _connectionState: ConnectionState = UNAVAILABLE

  def connect() =
    state match {

      case _: ActiveState =>
        sys.error("Already connecting/connected/disconnecting")

      case UnavailableIdleState =>
        log.debug("Connection requested when unavailable, doing nothing")

      case AvailableIdleState =>

        val handlerThread = new HandlerThread("ServerConnection")
        handlerThread.start()
        val handler = new Handler(handlerThread.getLooper)
        val activeState = ActiveState(handler)
        state = activeState
        _connectionState = CONNECTING

        asyncPost(handler) {
          log.debug("Creating WebSocket call")
          val webSocketCall = WebSocketCall.create(
            client,
            new com.squareup.okhttp.Request.Builder().url(ServerEndpoint).build
          )
          webSocketCall.enqueue(this)
          activeState.subState = ConnectingSubState(webSocketCall)
        }
        connectionStateListener.onConnectionStateChanged(_connectionState)

    }

  def connectionState = _connectionState

  def disconnect(): Unit = disconnect(1001)

  private def disconnect(code: Int) {
    state match {

      case AvailableIdleState | UnavailableIdleState =>
        sys.error("Already disconnected")

      case activeState: ActiveState =>
        asyncPost(activeState.handler) {
          activeState.subState match {

            case DisconnectingSubState =>
              sys.error("Already disconnecting")

            case connectingState: ConnectingSubState =>
              log.debug("Cancelling WebSocket call")
              connectingState.webSocketCall.cancel()

            case connectedState: ConnectedSubState =>
              Try {
                log.debug("Closing WebSocket")
                connectedState.webSocket.close(code, null)
              } match {

                case Failure(e) =>
                  log.warn("Failed to close WebSocket", e)

                case _ =>

              }

          }
          activeState.subState = DisconnectingSubState
          _connectionState = DISCONNECTING
          asyncPost(mainHandler)(
            connectionStateListener.onConnectionStateChanged(_connectionState)
          )
        }

    }
  }

  def doClose(handler: Handler) {
    asyncPost(handler) {
      handler.removeCallbacks(pingRunnable)
      handler.getLooper.quit()
      asyncPost(mainHandler) {
        if (!isConnectionAvailable) {
          state = UnavailableIdleState
          _connectionState = UNAVAILABLE
        } else {
          state = AvailableIdleState
          _connectionState = AVAILABLE
        }
        connectionStateListener.onConnectionStateChanged(_connectionState)
      }
    }
  }

  def handleConnectivityStateChange() {
    if (!isConnectionAvailable) {
      state match {

        case AvailableIdleState =>
          state = UnavailableIdleState
          _connectionState = UNAVAILABLE
          connectionStateListener.onConnectionStateChanged(_connectionState)

        case _ =>

      }
    } else {
      state match {

        case UnavailableIdleState =>
          state = AvailableIdleState
          _connectionState = AVAILABLE
          connectionStateListener.onConnectionStateChanged(_connectionState)

        case _ =>

      }
    }
  }

  def isConnectionAvailable = {
    val activeNetwork = context
      .getSystemService(Context.CONNECTIVITY_SERVICE)
      .asInstanceOf[ConnectivityManager]
      .getActiveNetworkInfo
    activeNetwork != null && activeNetwork.isConnected
  }

  def onCreate() {
    context.registerReceiver(connectivityStateReceiver, connectivityStateIntentFilter)
    handleConnectivityStateChange()
  }

  override def onClose(code: Int, reason: String) {
    log.debug(s"WebSocket closed. Reason: $reason, Code: $code")
    state match {

      case AvailableIdleState | UnavailableIdleState =>
        sys.error("Already disconnected")

      case activeState: ActiveState =>
        activeState.subState match {

          case _: ConnectingSubState =>
            sys.error("Not connected or disconnecting")

          case _: ConnectedSubState | DisconnectingSubState =>
            doClose(activeState.handler)

        }
    }
  }

  def onDestroy() {
    // TODO: Force closes due to no registration in some cases (also makes reported state wrong)
    context.unregisterReceiver(connectivityStateReceiver)
  }

  override def onFailure(e: IOException, response: com.squareup.okhttp.Response) {
    log.warn(s"WebSocked failed. Response: $response, Exception: $e")
    state match {

      case AvailableIdleState | UnavailableIdleState =>
        sys.error("Already disconnected")

      case activeState: ActiveState =>
        doClose(activeState.handler)

    }
  }

  override def onMessage(payload: BufferedSource, `type`: WebSocket.PayloadType) =
    state match {

      case AvailableIdleState | UnavailableIdleState =>
        sys.error("Not connected")

      case activeState: ActiveState =>
        activeState.subState match {

          case _: ConnectingSubState =>
            sys.error("Not connected")

          case DisconnectingSubState =>
            log.debug("Message received while disconnecting, dropping message")

          case _: ConnectedSubState =>
            `type` match {

              case WebSocket.PayloadType.TEXT =>
                Try(Json.parse(payload.readUtf8)) match {

                  case Failure(exception) =>
                    disconnect(1002)
                    sys.error(s"Invalid JSON: $exception")

                  case Success(json) =>

                    Json.fromJson[JsonRpcMessage](json).fold({ errors =>
                      disconnect(1002)
                      sys.error(s"Invalid JSON-RPC message: $errors")
                    }, {

                      case jsonRpcRequestMessage: JsonRpcRequestMessage =>
                        disconnect(1002)
                        sys.error(s"Received $jsonRpcRequestMessage")

                      case jsonRpcResponseMessage: JsonRpcResponseMessage =>
                        jsonRpcResponseMessage.id.fold {
                          disconnect(1002)
                          sys.error(s"JSON-RPC message ID missing, jsonRpcResponseMessage" +
                            s".eitherErrorOrResult=${jsonRpcResponseMessage.eitherErrorOrResult}")
                        } { id =>
                          id.right.toOption.fold {
                            sys.error(s"JSON-RPC message ID was not an Int, id=$id")
                            disconnect(1002)
                          } { commandIdentifier =>
                            asyncPost(activeState.handler)(
                              pendingRequests.get(commandIdentifier).fold {
                                disconnect(1002)
                                sys.error(s"No pending request exists with commandIdentifier" +
                                  s"=$commandIdentifier")
                              } { pendingRequest =>
                                pendingRequests = pendingRequests - commandIdentifier
                                Response.read(
                                  jsonRpcResponseMessage,
                                  pendingRequest.requestMessage.method
                                ).fold({ errors =>
                                  disconnect(1002)
                                  sys.error(s"Invalid Response: $errors")
                                }, {

                                  case errorResponse: ErrorResponse =>
                                    asyncPost(mainHandler)(
                                      pendingRequest.callback.onErrorReceived(errorResponse)
                                    )

                                  case resultResponse: ResultResponse =>
                                    asyncPost(mainHandler)(
                                      pendingRequest.callback.onResultReceived(resultResponse)
                                    )

                                })
                              }
                            )
                          }
                        }

                      case jsonRpcNotificationMessage: JsonRpcNotificationMessage =>
                        Notification.read(jsonRpcNotificationMessage).fold {
                          disconnect(1002)
                          sys.error(s"No notification type exists with method" +
                            s"=${jsonRpcNotificationMessage.method}")
                        }(_.fold({ errors =>
                          disconnect(1002)
                          sys.error(s"Invalid Notification: $errors")
                        }, notification =>
                          asyncPost(activeState.handler)(
                            asyncPost(mainHandler)(
                              notificationListener.onNotificationReceived(notification)
                            )
                          )
                        ))

                    })

                }

              case WebSocket.PayloadType.BINARY =>
                disconnect(1003)

            }

        }
        payload.close()

    }

  override def onOpen(webSocket: WebSocket, response: com.squareup.okhttp.Response) =
    state match {

      case AvailableIdleState | UnavailableIdleState =>
        sys.error("Not connecting")

      case activeState: ActiveState =>
        activeState.subState match {

          case _: ConnectedSubState | DisconnectingSubState =>
            sys.error("Not connecting")

          case _: ConnectingSubState =>
            asyncPost(activeState.handler) {
              activeState.handler.postDelayed(pingRunnable, PingPeriod)
              activeState.subState = ConnectedSubState(webSocket)
              _connectionState = CONNECTED
              asyncPost(mainHandler)(
                connectionStateListener.onConnectionStateChanged(_connectionState)
              )
            }

        }

    }

  override def onPong(payload: Buffer) {
    val payloadString = if (payload != null) {
      val result = payload.readUtf8
      payload.close()
      result
    }
    // TODO: Timeouts
    log.trace(s"Received pong: $payloadString")
  }

  def sendCommand(command: Command, responseCallback: ResponseCallback) {
    state match {

      case AvailableIdleState | UnavailableIdleState =>
        sys.error("Not connected")

      case activeState: ActiveState =>
        activeState.subState match {

          case _: ConnectingSubState | DisconnectingSubState =>
            sys.error("Not connected")

          case connectedState: ConnectedSubState =>
            asyncPost(activeState.handler) {
              val jsonRpcRequestMessage = Command.write(command, Right(commandIdentifier))
              commandIdentifier = commandIdentifier + 1
              Try {
                connectedState.webSocket.sendMessage(
                  WebSocket.PayloadType.TEXT,
                  new Buffer().writeUtf8(
                    Json.stringify(
                      Json.toJson(jsonRpcRequestMessage)
                    )
                  )
                )
                pendingRequests = pendingRequests +
                  (jsonRpcRequestMessage.id.right.get ->
                    PendingRequest(jsonRpcRequestMessage, responseCallback))
              } match {

                case Failure(e) =>
                  log.warn(s"Failed to send $jsonRpcRequestMessage", e)
                  state match {

                    case AvailableIdleState | UnavailableIdleState =>
                      sys.error("Already disconnected")

                    case activeState: ActiveState =>
                      doClose(activeState.handler)

                  }

                case _ =>

              }
            }

        }

    }
  }

}
