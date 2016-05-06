package com.dhpcs.liquidity

import java.io.{File, IOException}
import java.util.concurrent.TimeUnit
import javax.net.ssl.{KeyManager, SSLContext, SSLException, TrustManager}

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.ConnectivityManager
import android.os.{Handler, HandlerThread, Looper}
import com.dhpcs.jsonrpc._
import com.dhpcs.liquidity.ServerConnection._
import com.dhpcs.liquidity.models._
import okhttp3.ws.{WebSocket, WebSocketCall, WebSocketListener}
import okhttp3.{OkHttpClient, RequestBody, ResponseBody}
import okio.Buffer
import play.api.libs.json.Json

import scala.util.{Failure, Right, Success, Try}

object ServerConnection {

  PRNGFixes.apply()

  sealed trait ConnectionState

  case object UNAVAILABLE extends ConnectionState

  case object GENERAL_FAILURE extends ConnectionState

  case object TLS_ERROR extends ConnectionState

  case object UNSUPPORTED_VERSION extends ConnectionState

  case object AVAILABLE extends ConnectionState

  case object CONNECTING extends ConnectionState

  case object WAITING_FOR_VERSION_CHECK extends ConnectionState

  case object ONLINE extends ConnectionState

  case object DISCONNECTING extends ConnectionState

  trait ConnectionStateListener {

    def onConnectionStateChanged(connectionState: ConnectionState)

  }

  trait NotificationReceiptListener {

    def onZoneNotificationReceived(zoneNotification: ZoneNotification)

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

  private case object UnsupportedVersionIdleState extends IdleState

  private case object AvailableIdleState extends IdleState

  private case class ActiveState(handler: Handler) extends State {

    var subState: SubState = _

  }

  private sealed trait SubState

  private case class ConnectingSubState(webSocketCall: WebSocketCall) extends SubState

  private sealed trait ConnectedSubState extends SubState {

    val webSocket: WebSocket

  }

  private case class WaitingForVersionCheckSubState(webSocket: WebSocket) extends ConnectedSubState

  private case class OnlineSubState(webSocket: WebSocket) extends ConnectedSubState

  private case object DisconnectingSubState extends SubState

  private case class PendingRequest(requestMessage: JsonRpcRequestMessage,
                                    callback: ResponseCallback)

  private sealed trait CloseCause

  private case object GeneralFailure extends CloseCause

  private case object TlsError extends CloseCause

  private case object UnsupportedVersion extends CloseCause

  private case object ServerDisconnect extends CloseCause

  private case object ClientDisconnect extends CloseCause

  private val ServerEndpoint = "https://liquidity.dhpcs.com/ws"

  private var instance: ServerConnection = _

  private def asyncPost(handler: Handler)(body: => Unit) {
    handler.post(new Runnable() {

      override def run() = body

    })
  }

  private def createSslSocketFactory(keyManagers: Array[KeyManager],
                                     trustManagers: Array[TrustManager]) = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagers,
      trustManagers,
      null
    )
    sslContext.getSocketFactory
  }

  def getInstance(context: Context,
                  filesDir: File,
                  clientId: String) = {
    if (instance == null) {
      instance = new ServerConnection(context, filesDir, clientId)
    }
    instance
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

class ServerConnection private(context: Context,
                               filesDir: File,
                               clientId: String) extends WebSocketListener {

  private lazy val client = new OkHttpClient.Builder()
    .sslSocketFactory(createSslSocketFactory(
      ClientKey.getKeyManagers(filesDir, clientId),
      ServerTrust.getTrustManagers(context.getResources.openRawResource(R.raw.liquidity_dhpcs_com))
    ))
    .readTimeout(0, TimeUnit.SECONDS)
    .writeTimeout(0, TimeUnit.SECONDS)
    .build()

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

  def clientKey = ClientKey.getPublicKey(filesDir, clientId)

  private def connect() = state match {

    case _: ActiveState =>

      sys.error("Already connecting/connected/disconnecting")

    case UnavailableIdleState =>

      sys.error("Connection unavailable")

    case AvailableIdleState
         | GeneralFailureIdleState
         | TlsErrorIdleState
         | UnsupportedVersionIdleState =>

      doOpen()

  }

  def connectionState = _connectionState

  private def disconnect(code: Int) = state match {

    case _: IdleState =>

      sys.error("Already disconnected")

    case activeState: ActiveState =>

      asyncPost(activeState.handler)(activeState.subState match {

        case DisconnectingSubState =>

          sys.error("Already disconnecting")

        case ConnectingSubState(webSocketCall) =>

          activeState.subState = DisconnectingSubState
          asyncPost(mainHandler) {
            _connectionState = DISCONNECTING
            connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
          }

          webSocketCall.cancel()

        case WaitingForVersionCheckSubState(webSocket) =>

          try {
            webSocket.close(code, null)
          } catch {
            case _: IOException =>
          }

        case OnlineSubState(webSocket) =>

          activeState.subState = DisconnectingSubState
          asyncPost(mainHandler) {
            _connectionState = DISCONNECTING
            connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
          }

          try {
            webSocket.close(code, null)
          } catch {
            case _: IOException =>
          }

      })

  }

  private def doClose(handler: Handler,
                      closeCause: CloseCause,
                      reconnect: Boolean = false) {
    handler.getLooper.quit()
    commandIdentifier = 0
    pendingRequests = Map.empty
    asyncPost(mainHandler) {
      closeCause match {

        case GeneralFailure =>

          hasFailed = true
          state = GeneralFailureIdleState
          _connectionState = GENERAL_FAILURE
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))

        case TlsError =>

          hasFailed = true
          state = TlsErrorIdleState
          _connectionState = TLS_ERROR
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))

        case UnsupportedVersion =>

          hasFailed = true
          state = UnsupportedVersionIdleState
          _connectionState = UNSUPPORTED_VERSION
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))

        case ServerDisconnect =>

          hasFailed = true
          state = AvailableIdleState
          _connectionState = AVAILABLE
          connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))

        case ClientDisconnect =>

          hasFailed = false
          if (connectRequestTokens.nonEmpty) {
            doOpen()
          } else {
            state = AvailableIdleState
            _connectionState = AVAILABLE
            connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
          }

      }
    }
  }

  private def doOpen() {
    val handlerThread = new HandlerThread("ServerConnection")
    handlerThread.start()
    val handler = new Handler(handlerThread.getLooper)
    val activeState = ActiveState(handler)
    state = activeState
    asyncPost(handler) {
      val webSocketCall = WebSocketCall.create(
        client,
        new okhttp3.Request.Builder().url(ServerEndpoint).build
      )
      webSocketCall.enqueue(this)
      activeState.subState = ConnectingSubState(webSocketCall)
    }
    _connectionState = CONNECTING
    connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
  }

  private def handleConnectivityStateChange() =
    if (!isConnectionAvailable) {
      state match {

        case AvailableIdleState
             | GeneralFailureIdleState
             | TlsErrorIdleState
             | UnsupportedVersionIdleState =>

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

  override def onClose(code: Int, reason: String) =
    asyncPost(mainHandler)(state match {

      case _: IdleState =>

        sys.error("Already disconnected")

      case activeState: ActiveState =>

        asyncPost(activeState.handler)(activeState.subState match {

          case _: ConnectingSubState =>

            sys.error("Not connected or disconnecting")

          case _: WaitingForVersionCheckSubState =>

            doClose(
              activeState.handler,
              UnsupportedVersion
            )

          case _: OnlineSubState =>

            doClose(
              activeState.handler,
              ServerDisconnect
            )

          case DisconnectingSubState =>

            doClose(
              activeState.handler,
              ClientDisconnect
            )

        })

    })

  override def onFailure(e: IOException, response: okhttp3.Response) =
    asyncPost(mainHandler)(state match {

      case _: IdleState =>

        sys.error("Already disconnected")

      case activeState: ActiveState =>

        asyncPost(activeState.handler)(activeState.subState match {

          case DisconnectingSubState =>

            doClose(
              activeState.handler,
              ClientDisconnect
            )

          case _ =>

            if (response == null) {

              e match {

                case _: SSLException =>

                  /*
                   * Client rejected server certificate.
                   */
                  doClose(
                    activeState.handler,
                    TlsError
                  )

                case _ =>

                  doClose(
                    activeState.handler,
                    GeneralFailure
                  )

              }

            } else {

              if (response.code == 400) {

                /*
                 * Server rejected client certificate.
                 */
                doClose(
                  activeState.handler,
                  TlsError
                )

              } else {

                doClose(
                  activeState.handler,
                  GeneralFailure
                )

              }

            }

        })

    })

  override def onMessage(message: ResponseBody) = message.contentType match {

    case WebSocket.BINARY =>

      sys.error("Received binary frame")

    case WebSocket.TEXT =>

      readJsonRpcMessage(message.string) match {

        case Left(error) =>

          sys.error(error)

        case Right(jsonRpcMessage) =>

          asyncPost(mainHandler)(state match {

            case _: IdleState =>

              sys.error("Not connected")

            case activeState: ActiveState =>

              jsonRpcMessage match {

                case jsonRpcNotificationMessage: JsonRpcNotificationMessage =>

                  asyncPost(activeState.handler) {

                    Notification.read(jsonRpcNotificationMessage).fold {
                      sys.error(s"No notification type exists with method" +
                        s"=${jsonRpcNotificationMessage.method}")
                    }(_.fold({ errors =>
                      sys.error(s"Invalid Notification: $errors")
                    }, {

                      case SupportedVersionsNotification(compatibleVersionNumbers) =>

                        activeState.subState match {

                          case _: ConnectingSubState =>

                            sys.error("Not connected")

                          case _: OnlineSubState =>

                            sys.error("Already online")

                          case WaitingForVersionCheckSubState(webSocket) =>

                            if (!compatibleVersionNumbers.contains(VersionNumber)) {

                              asyncPost(mainHandler)(
                                disconnect(1001)
                              )

                            } else {

                              asyncPost(activeState.handler) {
                                activeState.subState = OnlineSubState(webSocket)
                                asyncPost(mainHandler) {
                                  _connectionState = ONLINE
                                  connectionStateListeners.foreach(
                                    _.onConnectionStateChanged(_connectionState)
                                  )
                                }
                              }

                            }

                          case DisconnectingSubState =>

                        }

                      case KeepAliveNotification =>

                        activeState.subState match {

                          case _: ConnectingSubState =>

                            sys.error("Not connected")

                          case _: WaitingForVersionCheckSubState =>

                            sys.error("Waiting for version check")

                          case _: OnlineSubState =>

                          case DisconnectingSubState =>

                        }

                      case zoneNotification: ZoneNotification =>

                        activeState.subState match {

                          case _: ConnectingSubState =>

                            sys.error("Not connected")

                          case _: WaitingForVersionCheckSubState =>

                            sys.error("Waiting for version check")

                          case _: OnlineSubState =>

                            asyncPost(activeState.handler)(
                              asyncPost(mainHandler)(
                                notificationReceiptListeners.foreach(
                                  _.onZoneNotificationReceived(zoneNotification)
                                )
                              )
                            )

                          case DisconnectingSubState =>

                        }

                    }))

                  }

                case jsonRpcResponseMessage: JsonRpcResponseMessage =>

                  asyncPost(activeState.handler)(activeState.subState match {

                    case _: ConnectingSubState =>

                      sys.error("Not connected")

                    case _: WaitingForVersionCheckSubState =>

                      sys.error("Waiting for version check")

                    case _: OnlineSubState =>

                      jsonRpcResponseMessage.id.fold {
                        sys.error(s"JSON-RPC message ID missing, jsonRpcResponseMessage" +
                          s".eitherErrorOrResult=${jsonRpcResponseMessage.eitherErrorOrResult}")
                      } { id =>
                        id.right.toOption.fold {
                          sys.error(s"JSON-RPC message ID was not a number, id=$id")
                        } { commandIdentifier =>
                          asyncPost(activeState.handler)(
                            pendingRequests.get(commandIdentifier).fold {
                              sys.error(s"No pending request exists with commandIdentifier" +
                                s"=$commandIdentifier")
                            } { pendingRequest =>
                              pendingRequests = pendingRequests - commandIdentifier
                              Response.read(
                                jsonRpcResponseMessage,
                                pendingRequest.requestMessage.method
                              ).fold({ errors =>
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

                    case DisconnectingSubState =>

                  })

                case jsonRpc_Message =>

                  asyncPost(activeState.handler)(activeState.subState match {

                    case _: ConnectingSubState =>

                      sys.error("Not connected")

                    case _: WaitingForVersionCheckSubState =>

                      sys.error("Waiting for version check")

                    case _: OnlineSubState =>

                      sys.error(s"Received $jsonRpc_Message")

                    case DisconnectingSubState =>

                  })

              }

          })

      }

  }

  override def onOpen(webSocket: WebSocket, response: okhttp3.Response) =
    asyncPost(mainHandler)(state match {

      case _: IdleState =>

        sys.error("Not connecting")

      case activeState: ActiveState =>

        asyncPost(activeState.handler)(activeState.subState match {

          case _: ConnectedSubState | DisconnectingSubState =>

            sys.error("Not connecting")

          case _: ConnectingSubState =>

            activeState.subState = WaitingForVersionCheckSubState(webSocket)
            asyncPost(mainHandler) {
              _connectionState = WAITING_FOR_VERSION_CHECK
              connectionStateListeners.foreach(_.onConnectionStateChanged(_connectionState))
            }

        })

    })

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
      || _connectionState == ServerConnection.TLS_ERROR
      || _connectionState == ServerConnection.UNSUPPORTED_VERSION)
      && (!hasFailed || retry)) {

      connect()

    }
  }

  def sendCommand(command: Command, responseCallback: ResponseCallback) = state match {

    case _: IdleState =>

      sys.error("Not connected")

    case activeState: ActiveState =>

      asyncPost(activeState.handler)(activeState.subState match {

        case _: ConnectingSubState | DisconnectingSubState =>

          sys.error(s"Not connected")

        case _: WaitingForVersionCheckSubState =>

          sys.error("Waiting for version check")

        case onlineSubState: OnlineSubState =>

          val jsonRpcRequestMessage = Command.write(command, Some(Right(commandIdentifier)))
          commandIdentifier = commandIdentifier + 1
          try {
            onlineSubState.webSocket.sendMessage(
              RequestBody.create(
                WebSocket.TEXT,
                Json.stringify(
                  Json.toJson(jsonRpcRequestMessage)
                )
              )
            )
            pendingRequests = pendingRequests +
              (jsonRpcRequestMessage.id.get.right.get ->
                PendingRequest(jsonRpcRequestMessage, responseCallback))
          } catch {

            /*
             * We do nothing here because we count on receiving a call to onFailure due to a
             * matching read error.
             */
            case _: IOException =>
          }

      })

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
          || _connectionState == ServerConnection.WAITING_FOR_VERSION_CHECK
          || _connectionState == ServerConnection.ONLINE) {

          disconnect(1001)

        }
      }
    }

}
