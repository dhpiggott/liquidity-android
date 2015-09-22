package com.dhpcs.liquidity

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.{SSLContext, SSLException}

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.ConnectivityManager
import android.os.{Handler, HandlerThread, Looper}
import com.dhpcs.jsonrpc._
import com.dhpcs.liquidity.ServerConnection._
import com.dhpcs.liquidity.models._
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.ws.{WebSocket, WebSocketCall, WebSocketListener}
import okio.{Buffer, BufferedSource}
import play.api.libs.json.Json

import scala.util.{Failure, Right, Success, Try}

object ServerConnection {

  PRNGFixes.apply()

  sealed trait ConnectionState

  case object UNAVAILABLE extends ConnectionState

  case object GENERAL_FAILURE extends ConnectionState

  case object TLS_ERROR extends ConnectionState

  case object AVAILABLE extends ConnectionState

  case object CONNECTING extends ConnectionState

  case object CONNECTED extends ConnectionState

  case object DISCONNECTING extends ConnectionState

  trait ConnectionStateListener {

    def onConnectionStateChanged(connectionState: ConnectionState)

  }

  trait NotificationReceiptListener {

    def onNotificationReceived(notification: Notification)

  }

  class ConnectionRequestToken

  trait ResponseCallback {

    def onErrorReceived(errorResponse: ErrorResponse)

    def onResultReceived(resultResponse: ResultResponse) = ()

  }

  private sealed trait State

  private sealed trait IdleState extends State

  private case object UnavailableIdleState extends IdleState

  private case object GeneralFailureIdleState extends IdleState

  private case object TlsErrorIdleState extends IdleState

  private case object AvailableIdleState extends IdleState

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

  private var instance: ServerConnection = _

  private def asyncPost(handler: Handler)(body: => Unit) {
    handler.post(new Runnable() {

      override def run() = body

    })
  }

  def getInstance(context: Context) = {
    if (instance == null) {
      instance = new ServerConnection(context)
    }
    instance
  }

  private def getSslSocketFactory(context: Context) = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      ClientKey.getKeyManagers(context),
      ServerTrust.getTrustManagers(context),
      null
    )
    sslContext.getSocketFactory
  }

  private def readJsonRpcMessage(jsonString: String): Either[String, JsonRpcMessage] =

    Try(Json.parse(jsonString)) match {

      case Failure(exception) =>

        Left(s"Invalid JSON: $exception")

      case Success(json) =>

        Json.fromJson[JsonRpcMessage](json).fold({ errors =>
          Left(s"Invalid JSON-RPC message: $errors")
        }, Right(_))

    }

}

class ServerConnection private(context: Context) extends WebSocketListener {

  private lazy val client = {
    val client = new OkHttpClient()
      .setSslSocketFactory(getSslSocketFactory(context))
    client.setReadTimeout(0, TimeUnit.SECONDS)
    client.setWriteTimeout(0, TimeUnit.SECONDS)
    client
  }

  private val mainHandler = new Handler(Looper.getMainLooper)

  private val connectionStateFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
  private val connectionStateReceiver = new BroadcastReceiver {

    override def onReceive(context: Context, intent: Intent) = intent.getAction match {

      case ConnectivityManager.CONNECTIVITY_ACTION =>

        handleConnectivityStateChange()

      case unmatched =>

        sys.error(s"Received unexpected broadcast for action $unmatched")

    }

  }

  private var pendingRequests = Map.empty[BigDecimal, PendingRequest]
  private var commandIdentifier = BigDecimal(0)
  private var state: State = UnavailableIdleState
  private var hasFailed: Boolean = _

  private var _connectionState: ConnectionState = UNAVAILABLE

  private var connectionStateListeners = Set.empty[ConnectionStateListener]
  private var connectRequestTokens = Set.empty[ConnectionRequestToken]

  private var notificationReceiptListeners = Set.empty[NotificationReceiptListener]

  handleConnectivityStateChange()

  private def connect() = state match {

    case _: ActiveState =>

      sys.error("Already connecting/connected/disconnecting")

    case UnavailableIdleState =>

    case AvailableIdleState | GeneralFailureIdleState | TlsErrorIdleState =>

      doOpen()

  }

  private def disconnect(code: Int) = state match {

    case _: IdleState =>

      sys.error("Already disconnected")

    case activeState: ActiveState =>

      asyncPost(activeState.handler) {
        activeState.subState match {

          case DisconnectingSubState =>

            sys.error("Already disconnecting")

          case connectingState: ConnectingSubState =>

            activeState.subState = DisconnectingSubState
            _connectionState = DISCONNECTING
            asyncPost(mainHandler)(
              connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
            )

            connectingState.webSocketCall.cancel()

          case connectedState: ConnectedSubState =>

            activeState.subState = DisconnectingSubState
            _connectionState = DISCONNECTING
            asyncPost(mainHandler)(
              connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
            )

            try {
              connectedState.webSocket.close(code, null)
            } catch {
              case _: IOException =>
            }

        }
      }

  }

  private def doClose(handler: Handler,
                      dueToGeneralFailure: Boolean,
                      dueToTlsError: Boolean,
                      reconnect: Boolean = false) =
    asyncPost(handler) {
      handler.getLooper.quit()
      asyncPost(mainHandler) {
        hasFailed = dueToGeneralFailure || dueToTlsError
        if (!reconnect) {
          if (dueToTlsError) {
            state = TlsErrorIdleState
            _connectionState = TLS_ERROR
          } else if (dueToGeneralFailure) {
            state = GeneralFailureIdleState
            _connectionState = GENERAL_FAILURE
          } else {
            state = AvailableIdleState
            _connectionState = AVAILABLE
          }
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
        } else {
          doOpen()
        }
      }
    }

  private def doOpen() {
    val handlerThread = new HandlerThread("ServerConnection")
    handlerThread.start()
    val handler = new Handler(handlerThread.getLooper)
    val activeState = ActiveState(handler)
    state = activeState
    _connectionState = CONNECTING

    asyncPost(handler) {
      val webSocketCall = WebSocketCall.create(
        client,
        new com.squareup.okhttp.Request.Builder().url(ServerEndpoint).build
      )
      webSocketCall.enqueue(this)
      activeState.subState = ConnectingSubState(webSocketCall)
    }
    connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
  }

  private def handleConnectivityStateChange() =
    if (!isConnectionAvailable) {
      state match {

        case AvailableIdleState | GeneralFailureIdleState | TlsErrorIdleState =>

          state = UnavailableIdleState
          _connectionState = UNAVAILABLE
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))

        case _ =>

      }
    } else {
      state match {

        case UnavailableIdleState =>

          state = AvailableIdleState
          _connectionState = AVAILABLE
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))

        case _ =>

      }
    }

  def isConnectionAvailable = {
    val activeNetwork = context
      .getSystemService(Context.CONNECTIVITY_SERVICE)
      .asInstanceOf[ConnectivityManager]
      .getActiveNetworkInfo
    activeNetwork != null && activeNetwork.isConnected
  }

  override def onClose(code: Int, reason: String) = state match {

    case _: IdleState =>

      sys.error("Already disconnected")

    case activeState: ActiveState =>

      activeState.subState match {

        case _: ConnectingSubState =>

          sys.error("Not connected or disconnecting")

        case _: ConnectedSubState =>

          doClose(
            activeState.handler,
            dueToGeneralFailure = false,
            dueToTlsError = false
          )

        case DisconnectingSubState =>

          doClose(
            activeState.handler,
            dueToGeneralFailure = false,
            dueToTlsError = false,
            reconnect = connectRequestTokens.nonEmpty
          )

          hasFailed = false

      }

  }

  override def onFailure(e: IOException, response: com.squareup.okhttp.Response) = state match {

    case _: IdleState =>

      sys.error("Already disconnected")

    case activeState: ActiveState =>

      activeState.subState match {

        case DisconnectingSubState =>

          doClose(
            activeState.handler,
            dueToGeneralFailure = false,
            dueToTlsError = false,
            reconnect = connectRequestTokens.nonEmpty
          )

          hasFailed = false

        case _ =>

          if (response == null) {

            e match {

              case _: SSLException =>

                /*
                 * Client rejected server certificate.
                 */
                doClose(
                  activeState.handler,
                  dueToGeneralFailure = false,
                  dueToTlsError = true
                )

              case _ =>

                doClose(
                  activeState.handler,
                  dueToGeneralFailure = true,
                  dueToTlsError = false
                )

            }

          } else {

            if (response.code == 400) {

              /*
               * Server rejected client certificate.
               */
              doClose(
                activeState.handler,
                dueToGeneralFailure = false,
                dueToTlsError = true
              )

            } else {

              doClose(
                activeState.handler,
                dueToGeneralFailure = true,
                dueToTlsError = false
              )

            }

          }

      }

  }

  override def onMessage(payload: BufferedSource, `type`: WebSocket.PayloadType) = state match {

    case _: IdleState =>

      sys.error("Not connected")

    case activeState: ActiveState =>

      activeState.subState match {

        case _: ConnectingSubState =>

          sys.error("Not connected")

        case DisconnectingSubState =>

        case _: ConnectedSubState =>

          `type` match {

            case WebSocket.PayloadType.TEXT =>

              readJsonRpcMessage(payload.readUtf8) match {

                case Left(error) =>

                  disconnect(1002)
                  sys.error(error)

                case Right(jsonRpcMessage) => jsonRpcMessage match {

                  case jsonRpcRequestMessage: JsonRpcRequestMessage =>

                    disconnect(1002)
                    sys.error(s"Received $jsonRpcRequestMessage")

                  case jsonRpcRequestMessageBatch: JsonRpcRequestMessageBatch =>

                    disconnect(1002)
                    sys.error(s"Received $jsonRpcRequestMessageBatch")

                  case jsonRpcResponseMessage: JsonRpcResponseMessage =>

                    jsonRpcResponseMessage.id.fold {
                      disconnect(1002)
                      sys.error(s"JSON-RPC message ID missing, jsonRpcResponseMessage" +
                        s".eitherErrorOrResult=${jsonRpcResponseMessage.eitherErrorOrResult}")
                    } { id =>
                      id.right.toOption.fold {
                        sys.error(s"JSON-RPC message ID was not a number, id=$id")
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

                              case Left(errorResponse) =>

                                asyncPost(mainHandler)(
                                  pendingRequest.callback.onErrorReceived(errorResponse)
                                )

                              case Right(resultResponse) =>

                                asyncPost(mainHandler)(
                                  pendingRequest.callback.onResultReceived(resultResponse)
                                )

                            })
                          }
                        )
                      }
                    }

                  case jsonRpcResponseMessageBatch: JsonRpcResponseMessageBatch =>

                    disconnect(1002)
                    sys.error(s"Received $jsonRpcResponseMessageBatch")

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
                          notificationReceiptListeners.foreach(
                            _.onNotificationReceived(notification)
                          )
                        )
                      )
                    ))

                }

              }

            case WebSocket.PayloadType.BINARY =>

              disconnect(1003)

          }

      }
      payload.close()

  }

  override def onOpen(webSocket: WebSocket, response: com.squareup.okhttp.Response) =
    state match {

      case AvailableIdleState
           | GeneralFailureIdleState
           | TlsErrorIdleState
           | UnavailableIdleState =>

        sys.error("Not connecting")

      case activeState: ActiveState =>

        activeState.subState match {

          case _: ConnectedSubState | DisconnectingSubState =>

            sys.error("Not connecting")

          case _: ConnectingSubState =>

            asyncPost(activeState.handler) {
              activeState.subState = ConnectedSubState(webSocket)
              _connectionState = CONNECTED
              asyncPost(mainHandler)(
                connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
              )
            }

        }

    }

  override def onPong(payload: Buffer) = ()

  def registerListener(listener: ConnectionStateListener) =
    if (!connectionStateListeners.contains(listener)) {
      if (connectionStateListeners.isEmpty) {
        context.registerReceiver(connectionStateReceiver, connectionStateFilter)
        handleConnectivityStateChange()
      }
      connectionStateListeners = connectionStateListeners + listener
      listener.onConnectionStateChanged(_connectionState)
    }

  def registerListener(listener: NotificationReceiptListener) =
    if (!notificationReceiptListeners.contains(listener)) {
      notificationReceiptListeners = notificationReceiptListeners + listener
    }

  def requestConnection(token: ConnectionRequestToken, retry: Boolean) {
    if (!connectRequestTokens.contains(token)) {
      connectRequestTokens = connectRequestTokens + token
    }
    if ((_connectionState == ServerConnection.AVAILABLE
      || _connectionState == ServerConnection.GENERAL_FAILURE
      || _connectionState == ServerConnection.TLS_ERROR)
      && (!hasFailed || retry)) {

      connect()

    }
  }

  def sendCommand(command: Command, responseCallback: ResponseCallback) = state match {

    case _: IdleState =>

      sys.error("Not connected")

    case activeState: ActiveState =>

      activeState.subState match {

        case _: ConnectingSubState | DisconnectingSubState =>

          sys.error("Not connected")

        case connectedState: ConnectedSubState =>

          asyncPost(activeState.handler) {
            val jsonRpcRequestMessage = Command.write(command, Some(Right(commandIdentifier)))
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
                (jsonRpcRequestMessage.id.get.right.get ->
                  PendingRequest(jsonRpcRequestMessage, responseCallback))
            } match {

              case Failure(e) =>

                state match {

                  case _: IdleState =>

                    sys.error("Already disconnected")

                  case activeState: ActiveState =>

                    doClose(
                      activeState.handler,
                      dueToGeneralFailure = true,
                      dueToTlsError = false
                    )

                }

              case _ =>

            }
          }

      }

  }

  def unregisterListener(listener: ConnectionStateListener) =
    if (connectionStateListeners.contains(listener)) {
      connectionStateListeners = connectionStateListeners - listener
      if (connectionStateListeners.isEmpty) {
        context.unregisterReceiver(connectionStateReceiver)
      }
    }

  def unregisterListener(listener: NotificationReceiptListener) =
    if (notificationReceiptListeners.contains(listener)) {
      notificationReceiptListeners = notificationReceiptListeners - listener
    }

  def unrequestConnection(token: ConnectionRequestToken) =
    if (connectRequestTokens.contains(token)) {
      connectRequestTokens = connectRequestTokens - token
      if (connectRequestTokens.isEmpty) {
        if (_connectionState == ServerConnection.CONNECTING
          || _connectionState == ServerConnection.CONNECTED) {

          disconnect(1001)

        }
      }
    }

}
