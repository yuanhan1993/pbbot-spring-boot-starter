package net.lz1998.pbbot.handler

import net.lz1998.pbbot.alias.Frame
import net.lz1998.pbbot.bot.BotFactory
import net.lz1998.pbbot.boot.EventProperties
import net.lz1998.pbbot.bot.BotContainer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

open class WebSocketHandler(
    eventProperties: EventProperties,
    open var botFactory: BotFactory,
    open var frameHandler: FrameHandler
) : BinaryWebSocketHandler() {
    @Autowired
    open lateinit var botContainer: BotContainer

    open val sessionMap = mutableMapOf<Long, WebSocketSession>()

    open var executor: ExecutorService = ThreadPoolExecutor(
        eventProperties.corePoolSize,
        eventProperties.maxPoolSize,
        eventProperties.keepAliveTime,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(eventProperties.workQueueSize)
    );

    var log = LoggerFactory.getLogger(this.javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val xSelfId = session.handshakeHeaders["x-self-id"]?.get(0)?.toLong() ?: 0L
        if (xSelfId == 0L) {
            session.close()
            return
        }
        sessionMap[xSelfId] = session
        log.info("$xSelfId connected")
        botContainer.bots[xSelfId] = botFactory.createBot(xSelfId, session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val xSelfId = session.handshakeHeaders["x-self-id"]?.get(0)?.toLong() ?: 0L
        if (xSelfId == 0L) {
            return
        }
        sessionMap.remove(xSelfId, session)
        log.info("$xSelfId disconnected")
        botContainer.bots.remove(xSelfId)
    }


    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        val frame = Frame.parseFrom(message.payload)
        val xSelfId = session.handshakeHeaders["x-self-id"]?.get(0)?.toLong() ?: 0L
        log.info("receive binaryMessage,sessionSelfId:" + xSelfId + ",botId:" + frame.botId)
        if (xSelfId == 0L) {
            return
        }
        val botId = frame.botId
        if (xSelfId != botId) {
            return
        }
        session.sendMessage(PingMessage())
        executor.execute {
            frameHandler.handleFrame(frame)
        }
    }
}
