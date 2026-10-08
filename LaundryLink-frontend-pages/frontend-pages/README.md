# LaundryLink frontend pages

This package is a static, upload-ready continuation of the existing LaundryLink frontend. It preserves the existing colour variables, navigation and footer patterns, button classes, square card treatment, form layout, spacing and naming style.

## Included pages

- `public/`: services and pricing
- `customer/`: dashboard, five-step new-order flow, orders, tracking, payments, addresses, profile, feedback and complaints
- `staff/`: dashboard, processing board, order processing, receiving and issue reports
- `rider/`: dashboard, task list and task detail
- `admin/`: dashboard, orders, service catalog, staff accounts, rider assignment, complaints, billing, reports, roles and permissions
- `css/`: shared design styles and section-specific extensions

## Upload notes

1. Copy the `css`, `public`, `customer`, `staff`, `rider` and `admin` folders together so their relative links continue to work.
2. The links to the project's existing landing and authentication pages assume this package remains beside the project source tree. Update those few links after moving files into your final static directory.
3. Forms use semantic HTML and browser validation. Replace the demonstration form actions with your Spring MVC routes when controllers are ready.
4. Demonstration names, orders, prices and dates should be replaced with server-rendered values during backend integration.
