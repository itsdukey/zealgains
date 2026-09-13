# Zealgains

A RuneLite plugin for Soul Wars clan management. Tracks red and blue kill calls in Friends Chat, enforces call rules, monitors avatar HP for dump timing, and highlights banned or notable players.

---

## Overview

Zealgains is built for Zealgains FC ranks and fraggers who need to coordinate calls and kills. It automates the tedious parts — enforcing call order, capping calls before 12:00, detecting out-of-order corrections, alerting when a caller disconnects, and tracking avatar readiness — so ranks can focus on running the game.

---

## Installation Note

On a fresh install, the plugin's config panel sometimes doesn't appear right away — this is a known RuneLite client behavior on first-time Plugin Hub installs, not specific to Zealgains. If this happens, fully close and reopen RuneLite once; the config panel will be there after the restart.

---

## Call Tracking

Players call their kills in FC chat using the format `r1r2`, `b3`, `r1r2r3`, etc. **The call must be the first thing in the message** — `r1` registers, but `"who has r1"` or `"i'll take r2"` do not, since the call doesn't start the message.

- **Sequential enforcement** — Calls must be made in order. Calling `b2` before `b1` is rejected with an alert and the slot stays open — there is no automatic correction or queuing.
- **3-call cap before 12:00** — Players cannot claim more than 3 slots until 12:00 remains. Extra calls are rejected and the slot stays open.
- **Team lock** — Each player is locked to one team per game. Their first call determines their team; cross-team calls are rejected.
- **B5 rule** — B5 is only valid after 12:00 and only if R5 has not been claimed. If R5 and B5 are called on the same tick, R5 wins. If B5 is already claimed and R5 is called later, R5 is rejected. When B5 is claimed, R5 disappears from the overlay.

### Call parsing

The plugin walks the message from the start, capturing the leading run of same-team call tokens (`r1`, `r123`, `r1r2r3`, `r1 r2 r3`, etc. are all valid). Parsing stops at the first token that isn't a call, or rejects outright if a later token is a valid call for the *other* team (mixed-team messages).

- If the message doesn't start with a call, it's ignored entirely — no filter needed for things like `"who has r1?"`, `"need r2"`, `"call r3"`.
- If it does start with a call, anything typed after it as a **separate word** (`r1 ty`, `r2 sounds good`) is fine **unless** it contains a flagged word — this catches cases where the call is real text but not actually a claim, e.g. `"r1 taken"` or `"r2 still open?"`.
- If garbage is glued directly onto the call with **no space** (e.g. `r1!`, `b12\`), **up to 1 stray character is tolerated** and folded into the same flagged-word check. **2 or more glued-on characters reject the whole call outright** (e.g. `b12ss`, `B34coolname`) — this is what stops ordinary chat that happens to start with call-shaped digits from being parsed as a call attempt, while still catching genuine fat-fingered keystrokes like a stray backslash before Enter.

Flagged words checked only in the text *after* the call: `?` · `need` · `open` · `who` · `call` · `want` · `you` · `getting` · `go get` · `grab` · `grabbing` · `available` · `anyone got` · `free` · `someone` · `anybody` · `is there` · `can i` · `taken` · `unclaimed` · `uncalled` · `please` · `pls` · `plz` · `wasn't` · `was not`

### Username protection

A username like `r1username` or `b34coolname` can never register as a call — the parser only tolerates a single stray character glued directly onto a call token before rejecting it outright (see **Call parsing** above), and usernames are always longer than that.

---

## Runner Callouts

Frag runners sign up using any of the following formats in FC chat:

| Format | Team |
|--------|------|
| `^r` `r^` `>r` `r>` | Red runner |
| `^b` `b^` `>b` `b>` | Blue runner |

Runners are displayed in the overlay below the call lists and cleared on game reset.

---

## Compact Overlay

Enable **Compact Overlay** (General Settings) to shrink the on-screen call tracker — useful on RuneLite's Fixed/Classic client layout where screen space is tight. Each slot's Red and Blue call share one line (e.g. `R1 PlayerName` on the left, `B1 PlayerName` on the right) instead of two stacked team lists, and a row is skipped entirely once neither team has claimed that slot. Runners collapse onto a single `Runners: Name (R), Name (B)` line. Off by default.

Right below it, **Overlay Size %** (General Settings) scales all overlay text — title, calls, runners, timer, score — at once. 100% is RuneLite's default size; lower it to shrink the overlay further, or raise it for readability. The overlay's border automatically resizes to fit whatever it's showing at the chosen size, so it always hugs the text instead of leaving empty space or wrapping.

---

## Avatar Dump Alerts

The plugin reads the avatar health and strength widgets every game tick:

- **Blue avatar** — widgets `375,15` (health) and `375,19` (strength)
- **Red avatar** — widgets `375,16` (health) and `375,20` (strength)

When both health and strength are at their observed maximum, an alert fires in game chat and as a RuneLite notification — but only if you have **at least 16 Soul Fragments** in your inventory (the minimum needed for a dump). Alerts are suppressed below this threshold.

- **Dumps 2–4** — Alert fires immediately when the avatar is ready.
- **Dump 1** — Suppressed (fires at game start, not useful).
- **Dump 5** — Gated behind the dump window: ≤5:00 remaining (or ≤4:45 with 40+ FC members). Retries every tick until both conditions are met.
- **Early warning** — When 40+ FC members are present, a warning fires at 5:05 reminding players not to dump at 5:00 and to wait for 4:45.

Avatar alerts are filtered by your team — you only see alerts for the enemy avatar. Team is detected with a three-tier fallback: varbit 3815 (set automatically when you join a Soul Wars team), your equipped Soul Wars cape if the varbit isn't set, and your own call history as a last resort.

---

## Disabling Fragging Features

**Enable Fragging Features** (General Settings, top of the section) is a master toggle for players who don't want the frag-calling system at all. Turning it off hides the call tracker overlay and disables dump-ready alerts and the kill-5 pre-warning. The **DO NOT DUMP** obelisk warning still shows, but Sacrifice is no longer deprioritized — dumps are never blocked by this toggle.

---

## Obelisk Warning

The plugin highlights the Soul Obelisk in **red** with **DO NOT DUMP** text and optionally deprioritizes the Sacrifice option in four situations:

1. **Obelisk is white (uncontrolled)** — dumping here is always wasted regardless of avatar HP.
2. **Obelisk is the wrong color for your team** — red player on a blue obelisk, or blue player on a red obelisk.
3. **Obelisk is your team's color but the avatar isn't at full HP+strength** — dumping now is off-color.
4. **You're in the game but hold no specific kill call** — the warning stays on permanently for uncalled participants, even if the avatar is at full HP.

By default (**Dump Warning Visibility: Always**, General Settings), the overlay and Sacrifice deprioritization apply to everyone, including spectators. Switching that setting to **Smart Filter** hides both from anyone the plugin can't confirm is actively playing (via varbit, Soul Wars cape, or call history). **Always Show Dump Overlay** (Developer Options) is a dev override that forces it back on for everyone even while Smart Filter is selected — useful for testing.

**Prevent Dumps When Not Ready** (General Settings) deprioritizes the Sacrifice-Fragments left-click option on the obelisk, making Walk Here the default, in the same four situations as the highlight above. Right-clicking still lets you Sacrifice normally.

---

## Disconnect Alerts

If a player who holds active calls leaves the Friends Chat, an alert prints listing their held calls. Fires once per player per game.

---

## Live Timer & Score

When enabled, the overlay displays a live countdown timer and the current kill score for each team. Toggle under **General Settings → Show Timer & Score**.

---

## End-of-Game Summary

When a game ends and Auto-Clear is on, a summary prints to game chat (if enabled under **General Settings → End-of-Game Summary**):

```
=== Zealgains: Game Summary ===
Red Calls — R1: PlayerA  R2: PlayerB  R3: PlayerC
Blue Calls — B1: PlayerD  B2: PlayerE
Final Score — Red 3 - Blue 2  —  Time remaining: 2:14
```

Colors for the header, team names, and score line are individually configurable under **Color Options**. Only fires on auto-clear; a manual reset skips it.

Auto-clear triggers on game-end chat messages and also on idle-kick (detected via varbit 3815, which drops to 0 whenever the player leaves Soul Wars for any reason).

---

## Ban List

The plugin downloads a remote ban list and highlights matching FC members.

- Set a raw URL to a plain-text file of banned names (one per line) in **ZG Ranks Settings → Ban List URL**
- Highlighted in the configurable Ban List Color (default: red)
- Optional notification when a banned player joins the FC
- Use `::zgsync` to force-refresh the list (5-minute cooldown, rank-only)

---

## Friends List & PM Highlighting

- **Highlight if on FL** — Colors FC members who are on your Friends List
- **PM Checker Highlight** — Colors FC members who are currently online and PM-able
- Colors are individually configurable

---

## Cross-World Call Detection

The plugin tracks the majority world of the Friends Chat. Calls from players on a different world are ignored and an alert fires if **Alert Cross-World Calls** is enabled.

---

## Commands

### Local (you only)

| Command | Description |
|---------|-------------|
| `::zgsync` | Force-refreshes the ban list (5-minute cooldown; rank-only) |
| `::zgteam` | Debug — prints team detection state to chat: varbit value, cached team, call-history team, resolved team, obelisk warn status |

### Rank Broadcast (Captain+ only)

| Command | Description |
|---------|-------------|
| `!zgreset r2 b3` | Broadcasts a targeted reset to all plugin users in the FC. Syncs everyone's overlay. 15-second cooldown. |
| `!zgreset r34` | Shorthand form of the broadcast reset |

The broadcast command prints to FC chat and is silently parsed by every other user running the plugin — no server required. Only fires if the sender is Captain rank or above in the FC.

When using targeted reset, the reshuffle caps blue open-slot announcements at B4 before 12:00.

---

## Display Options

| Mode | Description |
|------|-------------|
| Overlay | On-screen overlay only |
| Side Panel | RuneLite nav panel only |
| Both | Overlay and panel simultaneously |
| None | Hidden — tracking still runs in the background |

---

## Configuration Sections

Sections appear top-to-bottom in this order in the config panel:

| Section | Contents |
|---------|----------|
| **Rules Guide** | Call rules, dumping rules, and how to frag. |
| **Valid Callouts** | Valid call formats, invalid examples, flagged-word list, and frag runner callouts. |
| **General Settings** | Enable Fragging Features (master toggle), display mode, compact overlay, overlay size, auto-clear, end-of-game summary, timer/score, hide outside game, dump alerts, kill-5 pre-warning, frag count, obelisk highlight, prevent dumps, dump warning visibility |
| **General Settings Guide** | Descriptions of every General Settings option |
| **Color Options** | Per-element color pickers and global opacity slider for alerts, overlay, and summaries |
| **ZG Ranks Settings** | Rule break alerts, cross-world detection, FL/PM highlights, ban list, left-click add/remove on FC members — for ZG Star Ranks only |
| **ZG Ranks Guide** | Rank commands (`!zgreset`, `::zgsync`) and explanations of every ZG Ranks option |
| **Overlay Usage** | How to read the on-screen call tracker overlay |
| **Developer Options** | Advanced overrides — not needed for normal play |

---

## Rules & Methods

For rules and methods, please ask a Star Rank in FriendsChat.
