package _6.Y2.S1.MTR._6.LaundryLink.dto;

import java.time.LocalDateTime;

// WHY: The single shape returned by both GET /tasks and GET /my-work —
//      keeping them identical means the frontend renders both lists with
//      the same rendering code.
// HOW: deliverID/orderID identify the task; type distinguishes "pickup"/
//      "delivery" (drives which columns the UI shows); statusID is the
//      raw numeric status for logic, status is its display label;
//      priorityTimestamp is used only for sort order, not shown in the UI.
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
