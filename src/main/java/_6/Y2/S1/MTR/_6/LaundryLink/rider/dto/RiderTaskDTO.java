package _6.Y2.S1.MTR._6.LaundryLink.rider.dto;

import java.time.LocalDateTime;

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
