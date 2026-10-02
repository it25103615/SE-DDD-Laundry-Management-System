package _6.Y2.S1.MTR._6.LaundryLink.address.dto;

import jakarta.validation.constraints.*;
public record AddressRequest(@NotBlank @Size(max = 50) String nickname, @NotBlank @Size(max = 100) String street, @NotBlank @Size(max = 30) String city, @NotBlank @Size(max = 30) String state, @Size(max = 250) String deliveryInstructions, Boolean isDefault) { }
