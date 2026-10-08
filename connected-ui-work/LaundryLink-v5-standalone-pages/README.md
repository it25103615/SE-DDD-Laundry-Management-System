# LaundryLink frontend pages

This package is the complete static, upload-ready LaundryLink V4 experience. Every page—not only the landing page—uses the same energetic presentation system while preserving its original purpose, content hierarchy and blue brand identity. Cyan, teal, purple, orange and green remain supporting accents.

## V4 all-page improvements

- Premium gradient headers and decorative background shapes on every screen
- Glass cards, colourful borders, stronger shadows and staged reveal motion
- Enhanced tables, forms, side navigation, order steps and empty states
- Role-aware visual accents for customer, staff, rider, admin, manager, owner and support screens
- Automatic success, warning, error and information badge styling
- Consistent mobile, tablet and desktop presentation

## Included pages

- `public/`: landing page, services, pricing and 404
- `auth/`: login, registration and password recovery
- `customer/`: dashboard, five-step new-order flow, orders, tracking, payments, addresses, profile, feedback and complaints
- `staff/`: dashboard, processing board, order processing, receiving and issue reports
- `rider/`: dashboard, task list and task detail
- `admin/`: dashboard, orders, service catalog, staff accounts, rider assignment, complaints, billing, reports, roles and permissions
- `manager/`: live shop operations, staff performance and inventory alerts
- `owner/`: executive analytics, revenue trends and business insights
- `support/`: customer-care queue, resolution performance and quick tools
- `css/`: shared design styles and section-specific extensions
- `js/`: lightweight counters and button micro-interactions
- `assets/`: original LaundryLink hero artwork generated for this package

## Upload notes

1. Copy the `css`, `public`, `customer`, `staff`, `rider` and `admin` folders together so their relative links continue to work.
2. Start with `public/index.html`. Internal static links work as long as the folder structure is preserved.
3. Forms use semantic HTML and browser validation. Replace the demonstration form actions with your Spring MVC routes when controllers are ready.
4. Demonstration names, orders, prices and dates should be replaced with server-rendered values during backend integration.
