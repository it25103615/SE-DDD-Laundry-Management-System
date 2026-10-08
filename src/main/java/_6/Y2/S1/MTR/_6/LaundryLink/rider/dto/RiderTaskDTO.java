package _6.Y2.S1.MTR._6.LaundryLink.rider.dto;

import java.time.LocalDateTime;

/**
 * One row in a rider's task table, sent as JSON to {@code riderAll.js}.
 *
 * @param deliverID         the {@code delivery.deliverID}
 * @param orderID           the related order
 * @param type              {@code "pickup"} or {@code "delivery"}
 * @param statusID          current order status ID
 * @param status            status label for display
 * @param customerName      built in SQL; empty string if missing
 * @param address           the address chosen for the order (the customer's default address
 *                          when the order has none), built in SQL; empty string if missing
 * @param phoneNumber       customer's phone; empty string if missing
 * @param pickupScheduled   customer's chosen pickup time; used for pickups only
 * @param priorityTimestamp the sort key: {@code pickup_scheduled} for pickups, the time the
 *                          order entered Awaiting Delivery for deliveries
 */
public record RiderTaskDTO(
        Integer deliverID,
        Integer orderID,
        String type,
        Integer statusID,
        String status,
        String customerName,
        String address,
        String phoneNumber,
        LocalDateTime pickupScheduled,
        LocalDateTime priorityTimestamp
) {}
