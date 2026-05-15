# UI/UX Agent Prompt — 01 Merchant Auth

Use this prompt with a separate UI/UX-specialized agent.

Expected returned artifact:

```text
prototypes/ui-redesign/01_merchant_auth.html
```

---

```text
You are designing the first standalone HTML prototype for MiniFin.

Attached context files:
- planning/01_business_requirements.md
- planning/02_user_journeys.md
- planning/03_functional_requirements.md
- planning/04_architecture.md
- planning/06_implementation_guide.md
- planning/design-details/access_matrix.md
- planning/design-details/api_contracts.md
- planning/design-details/schema_drafts.md
- planning/design-details/ui_prototypes.md
- planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md

Use the Markdown files as product/domain context.
There are no previous MiniFin HTML prototypes yet, so this file must establish the first visual system baseline for later MiniFin prototypes.

Design direction:
- serious fintech operational SaaS, not a marketing page;
- quiet, dense, scan-friendly, dashboard-oriented;
- restrained but not monochrome;
- desktop-first, optimized for 1440px width;
- responsive enough for tablet;
- avoid decorative hero sections, giant cards, or marketing whitespace;
- use realistic operational copy and sample data;
- UI copy should be in English.

Important:
- The product name is exactly: "MiniFin".
- Do not include unrelated product, marketplace, vendor, bank, or card-network brand names.
- HTML title must be exactly: "MiniFin — Merchant Auth".
- Create one standalone HTML file.
- Use embedded CSS and, if useful, embedded React.
- No backend calls.
- Use realistic sample data.
- Environment label should be "Local Sandbox".
- This is a prototype artifact, not production app code.

Task:
Design the Merchant Login/Register screen for `MDB-UI-01`.

Audience:
Merchant owner / first merchant employee, later merchant admin.

Primary user:
Marta Weiss — founder/operator of Northstar Bikes GmbH.

Screen goal:
Let a merchant register the company and first admin user, understand email verification, log in, see current merchant session state, and understand that Stripe Connect onboarding/API keys/payments are later steps not available until KYB starts.

Domain sample:
- Company: Northstar Bikes GmbH
- Country: DE
- Business type: company
- First employee: Marta Weiss
- Email: marta@northstar-bikes.example
- Role after registration: merchant_admin
- Merchant KYB status: NOT_STARTED
- Employee status before verification: EMAIL_UNVERIFIED
- Employee status after verification: ACTIVE
- Environment: Local Sandbox
- Host: merchants.miniefin.local

Must include:

1. Merchant app shell / auth surface
- Establish a reusable MiniFin merchant dashboard visual system.
- Show product brand "MiniFin".
- Show surface label "Merchant Dashboard".
- Show host or environment context: merchants.miniefin.local / Local Sandbox.
- Include a restrained navigation preview for future merchant areas:
  - Overview
  - Onboarding
  - API Keys
  - Webhooks
  - Payments
  - Settlements
  - Disputes
- Future areas should look unavailable/locked until login or KYB, not fake-functional.

2. Auth mode switch
- Register merchant
- Verify email
- Login
- Current session
- Access denied / wrong role

Use tabs, segmented control, or a similar dense operational control.

3. Merchant registration state
Show a registration form with:
- company name
- country
- business type
- admin email
- password
- confirm password
- terms/compliance acknowledgement

Show validation and state examples:
- weak password
- duplicate global email
- missing company name
- invalid country/business type

Show what happens on success:
- merchantId
- employeeId
- role = merchant_admin
- employeeStatus = EMAIL_UNVERIFIED
- merchantStatus = NOT_STARTED
- next required action = verify email

4. Email verification state
Show:
- pending verification state;
- success state;
- expired/invalid token state;
- development-only local token panel for Local Sandbox.

The local token panel must be visually marked as development-only, not a production email substitute.

5. Login state
Show:
- login form with email/password;
- successful login state;
- invalid credentials error;
- email not verified error;
- blocked/frozen account placeholder as future enforcement hook.

6. Current merchant session
Show authenticated session summary:
- merchantId
- employeeId
- company name
- email
- role = merchant_admin
- employeeStatus = ACTIVE
- merchantStatus = NOT_STARTED
- session type = opaque server-side cookie

Show next unavailable steps as a clear operational checklist:
- Verify email: done
- Start Stripe Connect onboarding: later slice
- Generate API key: locked until KYB approved
- Configure webhooks: locked until API key setup
- View payments: empty until API integration

Do not make later-scope actions look successful.

7. Wrong-role / protected endpoint denial
Represent:
- unauthenticated access to merchant session returns 401;
- end-user session attempting merchant dashboard returns 403;
- merchant employee can access only own merchant.

This state should be visible somewhere in the screen, for example as an access diagnostic panel or drawer.

8. Audit and security hints
Show business-readable audit/security facts:
- merchant.registered
- merchant.email_verified
- merchant.login_succeeded
- merchant.login_failed
- merchant.logout
- global email uniqueness across user pools
- password never stored in plaintext
- server-side opaque session cookie

These should appear as operational UI details, not as long documentation text.

9. Interaction
- Switching auth modes updates the visible panel locally.
- Register action can show local success state.
- Verify action can show success or invalid-token state.
- Login action can show authenticated session state.
- Access denied panel can show 401 vs 403 examples.
- No persistence is required.

10. Visual quality
- Dense but readable layout.
- Stable dimensions for controls and panels.
- No overlapping text.
- No hero marketing composition.
- No nested decorative cards.
- Cards are acceptable only for individual repeated/status items or forms.
- Use small badges for statuses: EMAIL_UNVERIFIED, ACTIVE, NOT_STARTED, LOCKED, 401, 403.
- Use a palette suitable for fintech operations; avoid one-note purple/blue-slate/beige-only themes.

Deliverable:
Return one complete standalone HTML prototype.

Save the file as:
01_merchant_auth.html
```
