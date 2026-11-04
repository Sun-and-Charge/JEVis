# Simpler permission management – concept (OpenJEVis #1211)

Status: proposal for discussion. Nothing in the permission model itself changes.

## How permissions work today

- **Ownership:** every object belongs to one or more groups (relationship *owner*, object → group).
- **Membership:** a user gets rights on everything a group owns through a membership per right: read, write,
  execute, create, delete (user → group). The web service checks each request against these memberships
  (`UserRightManagerForWS`); Sys Admins bypass all checks. Objects marked public are readable by everyone.
- **Roles:** a role bundles group rights. They are not evaluated at run time. The Control Center
  (`RoleManager`) copies the role's group rights into ordinary memberships of each role member when the role is saved.
- **Tools today:**
  - the object editor tabs *Mitglieder* (group memberships) and *Berechtigungen* (owners);
  - the role editor;
  - the bulk dialogs "Freigabe hinzufügen / entfernen";
  - the *Gebäude-Vorlage*, which creates the groups of a location (building group, groups with and without entry
    point, one group per module) and an admin role.

## What makes it hard

1. **Rights can't be seen in one place.** The answer to "what may user X do here?" is spread over owner groups,
   up to five memberships per group and roles. Nothing shows the effective result.
2. **Too many groups for small sites.** The building template creates about ten groups per location. That fits
   large organisations, but a single site with a handful of users only needs "may view" and "may edit".
3. **Roles drift.** Because roles are copied into memberships, a membership changed by hand later no longer matches
   the role, and nothing shows that.
4. **Mistakes are silent.** If an object has no owner group, or a user is in no group, the object or the tree is
   empty, with no hint why.

## Proposal

### 1. "Wer darf was?" – effective rights view (read-only)
- **Per object:** a new object editor tab lists all users with their effective rights, shown as read, write, create,
  delete, execute ticks. Each tick says where it comes from, as "via group X" or "via role Y".
- **Per user:** a new tab on the user object lists the groups and, for a chosen object, the effective rights.
- **Computation:** it uses the same logic as the web service (`UserRightManagerForWS`). A small read-only endpoint
  returns the result, so the view always matches what the server really allows.

### 2. Simple sharing wizard for small sites
- **Entry point:** a context-menu entry "Zugriff einrichten …" on an organisation or building.
- **Presets:** *Nur ansehen*, *Bearbeiten* and *Verwalten*.
- **Steps:** choose users and assign each a preset. That's it.
- **What it creates:** the wizard creates or reuses exactly one group per preset and location, e.g. "Solarpark –
  Ansehen", and sets owner and memberships.
- **Larger setups:** the building template stays available. The wizard is the small-site alternative, not a
  replacement.

### 3. Consistency check
A Sys Admin function "Berechtigungen prüfen" lists:
- objects without an owner group (only Sys Admins can see them);
- users without any membership;
- empty groups and groups that own nothing;
- role members whose memberships no longer match their role, with a button to re-apply the role.

### 4. Small UI improvements in the existing editors
- The membership tab shows the five rights as one row of checkboxes per group, with the presets above as shortcuts.
- When the tree is empty after login, a hint says that the user has no read rights yet and whom to ask.

## Effort and order

| Step | Effort | Benefit |
|---|---|---|
| 1. Effective rights view | M (2–3 days) | High: it answers most support questions |
| 3. Consistency check | S–M (1–2 days) | Finds broken setups |
| 2. Sharing wizard | M (2–3 days) | Makes small sites easy |
| 4. UI improvements | S (1 day) | Fewer clicks |

Suggested order: 1 → 3 → 2 → 4. Each step can be released on its own.

## Not part of this concept

- Changing the permission model itself, e.g. evaluating roles at run time or per-attribute rights. That would
  mean a database and web service change with a migration of all existing memberships.
- Single sign-on group mapping. "Entra ID Group" on the user exists and is out of scope here.
