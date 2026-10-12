# Support workflow

Customers choose Feedback, Question or Complaint. Feedback requests include a rating; questions and complaints require a topic choice in the browser: Payments & billing, Laundry & items, Pickup & delivery, Account & booking or General. An optional related order must belong to the customer.

New cases enter the shared CSM queue before assignment. Customer service can reply directly to feedback and use **Handle this myself**, then save, to take ownership. Topic hints suggest a manager for billing, laundry staff for garment issues and a rider for pickup/delivery. The CSM chooses an active recipient and saves the assignment; an in-app notification links the recipient to their case. Management oversight remains available to owners/admins.

The assignee, the submitting customer and CSM share one case conversation. Assigned managers, staff and riders only see their own assigned cases and cannot reassign them. All permitted replies persist in the database and notify the other party. Resolution requires a valid transition and a resolution note.

CSM/staff queues have separate Feedback, Questions and Complaints browsing controls, with additional topic and status filters. Customers instead see My support requests: simple cards showing their own submitted requests, current status and a View updates & reply action. Management filters and priority badges are hidden from the customer view. Staff-raised garment issues appear under Complaints and are tagged Laundry & items. Historical customer cases keep the General topic rather than inventing a category.

Case history is private to CSM/CUSTOMER_SERVICE_MANAGER accounts. The history tab is hidden for customers, managers, riders, laundry staff, owners and admins; the backend also omits the history field for those roles. Their existing case visibility, conversation and resolution permissions are preserved. The seven-role integration tests verify this restriction.

## Conversation fixes and evidence (8 October 2026)

- A case now opens to Conversation by default. Details & assignment and Case history remain separate tabs.
- A successful reply displays the returned server conversation immediately; it does not depend on the case list refresh succeeding. Failed sends show an error inside the visible conversation and retain the draft.
- Assigned customer cases no longer incorrectly pause polling because the hidden assignee selector has no staff options. New replies refresh every 15 seconds while visible, including while a draft is being composed; case edits and reply drafts are preserved.
- Assignment/status errors also appear inside the open case panel.
- Full Maven regression suite: 300 tests, zero failures, errors or skips. Real database tests cover topics, CSM queue visibility, assignment, authenticated reply POSTs and customer/CSM readback for all seven staff role variants, plus notifications and resolution. Fixtures roll back.
- Frontend audit: 53 pages, 28 JavaScript files; no broken literal local links, syntax errors or missing required controller elements.
- Browser checks confirmed feedback rating visibility, question topics and the saved conversation in case #6 from both customer and rider views.
- The reported rider message in case #6 was absent from the database when inspected. No historical message was fabricated or resent. Existing messages were preserved.

Migration 014 adds the topic field and is included in Initialize-SupportDatabase.ps1; it was applied to the current local database. Fresh initialize_database.sql also includes the field. Restart the running application after rebuilding to load the updated Java and frontend resources.
