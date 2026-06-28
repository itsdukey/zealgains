# Zealgains

A RuneLite plugin for Soul Wars clan management. Tracks red and blue kill calls in Friends Chat, enforces call rules, monitors avatar HP for dump timing, and highlights banned or notable players.

---

## Overview

Zealgains is built for Zealgains FC ranks and fraggers who need to coordinate calls and kills. It automates the tedious parts — enforcing call order, capping calls before 12:00, detecting out-of-order corrections, alerting when a caller disconnects, and tracking avatar readiness — so ranks can focus on running the game.

---

## Call Tracking

Players call their kills in FC chat using the format `r1r2`, `b3`, `r1r2r3`, etc. Calls can appear anywhere in the message — `"guess i can do r5 too"` registers as an r5 call.

- **Sequential enforcement** — Calls must be made in order. Calling `b2` before `b1` is rejected with an alert and the slot stays open — there is no automatic correction or queuing.
- **3-call cap before 12:00** — Players cannot claim more than 3 slots until 12:00 remains. Extra calls are rejected and the slot stays open.
- **Team lock** — Each player is locked to one team per game. Their first call determines their team; cross-team calls are rejected.
- **B5 rule** — B5 is only valid after 12:00 and only if R5 has not been claimed. If R5 and B5 are called on the same tick, R5 wins. If B5 is already claimed and R5 is called later, R5 is rejected. When B5 is claimed, R5 disappears from the overlay.

### Noise filter

Messages containing any of the following are ignored before call detection runs:

`?` · `need` · `open` · `who` · `call` · `want` · `you` · `getting` · `go get` · `grab` · `grabbing`

### Username protection

Call patterns embedded in usernames are rejected. A match is only accepted if the `r#` / `b#` sequence is not immediately preceded or followed by a letter, digit, underscore, or hyphen — so `r1username`, `b34coolname`, and `b1-tag` are all ignored.

---

## Runner Callouts

Frag runners sign up using any of the following formats in FC chat:

| Format | Team |
|--------|------|
| `^r` `r^` `>r` `r>` | Red runner |
| `^b` `b^` `>b` `b>` | Blue runner |

Runners are displayed in the overlay below the call lists and cleared on game reset.

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

Avatar alerts are filtered by your team — you only see alerts for the enemy avatar. Team is detected from varbit 3815 (set automatically when you join a Soul Wars team) and from your own call history as a fallback.

---

## Obelisk Warning

The plugin highlights the Soul Obelisk in **red** with **DO NOT DUMP** text and optionally deprioritizes the Sacrifice option in three situations:

1. **Obelisk is white (uncontrolled)** — dumping here is always wasted regardless of avatar HP.
2. **Obelisk is the wrong color for your team** — red player on a blue obelisk, or blue player on a red obelisk.
3. **Obelisk is your team's color but the avatar isn't at full HP+strength** — dumping now is off-color.

The **DO NOT DUMP** overlay is only visible to players who have an active kill call or have signed up as a runner, so spectators do not see it. Enable **Always Show Dump Overlay** (Developer Options) to bypass this.

**Prevent Dumps When Not Ready** (General Settings) deprioritizes the Sacrifice-Fragments left-click option on the obelisk, making Walk Here the default. Right-clicking still lets you Sacrifice normally.

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

Colors for the header, team names, and score line are individually configurable under **Color Options**. Only fires on auto-clear; manual `::zgreset` skips it.

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
| `::zgreset` | Shows a confirmation dialog, then clears all tracked calls and runners |
| `::zgreset r2 b3` | Resets specific slots. Remaining callers reshuffle into sequential order; open slots announced in chat |
| `::zgreset r34` | Shorthand — resets R3 and R4 in one command |
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

| Section | Contents |
|---------|----------|
| **Rules Guide** | Call rules, team lock rules, dumping rules, and runner callout formats |
| **General Settings** | Display mode, auto-clear, end-of-game summary, timer/score, hide outside game, dump alerts, obelisk highlight, prevent dumps |
| **Color Options** | Per-element color pickers and global opacity slider for alerts, overlay, and summaries |
| **General Settings Guide** | Descriptions of every General Settings option |
| **Overlay Usage** | How to read the on-screen call tracker overlay |
| **Chat Commands** | Local commands (`::zgreset`) and rank broadcast commands (`!zgreset`) |
| **ZG Ranks Settings** | Rule break alerts, cross-world detection, FL/PM highlights, ban list — for ZG Star Ranks only |
| **ZG Ranks Guide** | Rank commands (`!zgreset`, `::zgsync`) and explanations of every ZG Ranks option |
| **Developer Options** | Advanced overrides — not needed for normal play |

---

## Discord

For rules and methods: **discord.gg/riseabove**
