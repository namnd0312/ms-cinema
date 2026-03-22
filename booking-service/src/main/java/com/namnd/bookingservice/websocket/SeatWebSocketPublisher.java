package com.namnd.bookingservice.websocket;

import com.namnd.bookingservice.dto.SeatStatusMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes seat status changes to WebSocket STOMP topic per showtime.
 * Topic: /topic/showtime/{showtimeId}/seats
 */
@Component
public class SeatWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public SeatWebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Broadcast seat status change to all subscribers of the showtime topic */
    public void publishSeatUpdate(Long showtimeId, List<Long> seatIds, String status) {
        String destination = "/topic/showtime/" + showtimeId + "/seats";
        messagingTemplate.convertAndSend(destination, new SeatStatusMessage(seatIds, status));
    }
}
