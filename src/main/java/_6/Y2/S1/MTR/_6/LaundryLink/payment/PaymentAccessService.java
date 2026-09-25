package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class PaymentAccessService {
    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String STAFF = "STAFF";
    private static final String CUSTOMER_SERVICE_MANAGER = "CUSTOMER_SERVICE_MANAGER";
    private static final String CSM = "CSM";

    private final PaymentManagementRepository paymentManagementRepository;

    public PaymentAccessService(PaymentManagementRepository paymentManagementRepository) {
        this.paymentManagementRepository = paymentManagementRepository;
    }

    public Integer resolveUserID(Principal principal, Integer headerUserID) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            try {
                return Integer.valueOf(principal.getName());
            } catch (NumberFormatException ex) {
                throw new AccessDeniedException("Authenticated user cannot be matched to a user record");
            }
        }

        if (headerUserID == null) {
            throw new AccessDeniedException("User identity is required");
        }

        return headerUserID;
    }

    public Integer resolveCustomerID(Principal principal, Integer headerUserID) {
        Integer userID = resolveUserID(principal, headerUserID);
        requireCustomer(userID);
        return userID;
    }

    public void verifyOrderBelongsToCustomer(BillingDetails billingDetails, Integer customerID) {
        if (!billingDetails.getUserID().equals(customerID)) {
            throw new AccessDeniedException("Order does not belong to the current customer");
        }
    }

    public void requireCustomer(Integer userID) {
        String userType = findUserType(userID);
        if (!"CUSTOMER".equalsIgnoreCase(userType)) {
            throw new AccessDeniedException("Customer access is required");
        }
    }

    public void requireManagementUser(Integer userID) {
        String userType = findUserType(userID);
        if (!ADMIN.equalsIgnoreCase(userType)
                && !MANAGER.equalsIgnoreCase(userType)
                && !CUSTOMER_SERVICE_MANAGER.equalsIgnoreCase(userType)
                && !CSM.equalsIgnoreCase(userType)) {
            throw new AccessDeniedException("Management access is required");
        }
    }

    public void requireStaffOrManagementUser(Integer userID) {
        String userType = findUserType(userID);
        if (!STAFF.equalsIgnoreCase(userType)
                && !ADMIN.equalsIgnoreCase(userType)
                && !MANAGER.equalsIgnoreCase(userType)
                && !CUSTOMER_SERVICE_MANAGER.equalsIgnoreCase(userType)
                && !CSM.equalsIgnoreCase(userType)) {
            throw new AccessDeniedException("Staff or management access is required");
        }
    }

    private String findUserType(Integer userID) {
        return paymentManagementRepository.findUserType(userID).orElseThrow(() ->
                new AccessDeniedException("User does not exist"));
    }
}
