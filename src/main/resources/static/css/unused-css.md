# Unused CSS Selectors

Checked against all HTML files and JS files (including dynamically-constructed
class names via `classList.add`, `className =`, and template literals) in the
uploaded webfiles.zip.

Some seem like selectors we could use in the future so I am leaving them for now.

## global.css

- `.empty_state` (and `.empty_state::before`)
- `.service_option` (and `.service_option input`)
- `.timeline_item` / `.timeline_item.done`
- `.loading_spinner`
- `.section_band` (and `.section_band .subtitle, .section_band .muted, .section_band a`)
- `.skeleton`
- `.notification_badge` (and `.notification_badge::after`)
- `.unstyled_form` (the `form:not(.unstyled_form)` exception is moot since nothing ever carries this class)

## dashboard.css

- `.task_address`
- `.timeline_item` / `.timeline_item.done` (defined again here, same story as global.css)

## public/public.css

- `.trust_strip` / `.trust_item` (and `.trust_item strong`)
- `.testimonial` (and `.testimonial::before`)
- `.process_step`
- `.hero_saas` (and `.hero_saas h1`, `.hero_saas p`, `.hero_saas::before`)
- `.hero_visual` (and `.hero_visual img`)
- `.floating_chip` / `.chip_one` / `.chip_two`
- `.stat_number`
- `.logo_cloud` (and `.logo_cloud span`)
- `.faq_item` (and `.faq_item summary`, `.faq_item p`)

This looks like a whole unused "SaaS-style landing hero + FAQ + trust bar"
section that never got wired into `about.html` / `services.html` / etc.

---

**Confirmed NOT unused** (verified via dynamic class construction in JS,
so a plain-text search alone would have missed them):

- `body.v4_auth`, `.v4_public`, `.v4_staff`, `.v4_rider`, `.v4_admin`,
  `.v4_manager`, `.v4_owner` — built via `` `v4_${role}` `` in `global-pre.js`.
  (Side note: the JS role list also includes `customer` and `support`, which
  produce `v4_customer` / `v4_support` classes with no matching CSS rule —
  not unused CSS, but a gap in the other direction.)
- `.v4_shape`, `.v4_shape_one`, `.v4_shape_two`, `.v4_reveal`, `.v4_visible` —
  set via `className`/`classList.add` in `global-pre.js`.
- `.connected_toast`, `.list_mode` — set via `className`/`classList.add` in `global-post.js`.
- `.pickup_section`, `.delivery_section` — both literal in rider HTML and
  built dynamically via `` `${row.dataset.type}_section` `` in `rider.js`.
- All `status_*`, `alert_*` classes — confirmed via `classify()` and
  `className =` assignments in `rider.js` and `order-processing.js`.
