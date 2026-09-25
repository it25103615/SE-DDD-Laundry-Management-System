package _6.Y2.S1.MTR._6.LaundryLink.address.dto;

import _6.Y2.S1.MTR._6.LaundryLink.address.Address;
public record AddressResponse(Integer addressID, String nickname, String street, String city, String state, String deliveryInstructions, Boolean isDefault) {
    public static AddressResponse from(Address address) { return new AddressResponse(address.getAddressID(), address.getNickname(), address.getStreet(), address.getCity(), address.getState(), address.getDeliveryInstructions(), address.getIsDefault()); }
}
