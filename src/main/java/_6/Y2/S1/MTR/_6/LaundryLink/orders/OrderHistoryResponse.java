package _6.Y2.S1.MTR._6.LaundryLink.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class OrderHistoryResponse {
    private Integer logID;
    private Integer statusBeforeID;
    private String statusBeforeLabel;
    private Integer statusAfterID;
    private String statusAfterLabel;
    private LocalDate logDate;
    private LocalTime logTime;
}
