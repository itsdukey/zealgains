# Zealgains

A RuneLite plugin for Soul Wars clan management. Tracks red and blue kill calls in Friends Chat, enforces call rules, monitors avatar HP for dump timing, and highlights banned or notable players.

---

## Overview

Zealgains is built for Zealgains FC ranks and fraggers who need to coordinate calls and kills. It automates the tedious parts — enforcing call order, capping calls before 12:00, detecting out-of-order corrections, alerting when a caller disconnects, and tracking avatar readiness — so ranks can focus on running the game.

---

## Call Tracking

Players call their kills in FC chat using the format `r1r2`, `b3`, `r1r2r3`, etc.

- **Sequential enforcement** — Calls are processed in order. A player cannot hold `r3` without `r2` already being claimed.
- **Out-of-order correction** — If a player calls `b2` before `b1`, the `b2` is held as pending. When they (or someone else) fills `b1`, their `b2` is automatically resolved. This handles the common case of mistyping the order.
- **3-call cap before 12:00** — Players cannot claim more than 3 slots until 12:00 remains on the timer. Extra calls are rejected and the slot stays open for others.
- **Team lock** — Each player is locked to one team per game. Their first call determines their team; any attempt to call for the other team is rejected.
- **B5 rule** — B5 is only valid after 12:00 on the timer and only if R5 has not been claimed. If R5 and B5 are called on the same game tick, R5 wins. If B5 is already claimed and R5 is called later, R5 is rejected.

---

## Runner Callouts

Frag runners sign up using any of the following formats in FC chat:

| Format | Team |
|--------|------|
| `^r` `r^` `>r` `r>` | Red runner |
| `^b` `b^` `>b` `b>` | Blue runner |

Runners are displayed in the overlay and side panel below the call lists. They are cleared on game reset.

---

## Avatar Dump Alerts

The plugin reads the avatar health and strength widgets every game tick:

- **Blue avatar** — widgets `375,15` (health) and `375,19` (strength)
- **Red avatar** — widgets `375,16` (health) and `375,20` (strength)

When both health and strength are at their observed maximum (100%), an alert fires in game chat and as a RuneLite notification:

- **Dumps 2–4** — Alert fires immediately when the avatar is ready.
- **Dump 1** — Suppressed (fires at the very start of every game, not useful).
- **Dump 5** — Gated behind the dump window: alert only fires when ≤5:00 remains (or ≤4:45 with 40+ FC members). The check retries every tick so the alert fires the moment both conditions are true.

The kill-5 message includes the team and dump time:
> `5th dump for Red at 5:00 — Dump the winning kill now!`

Avatar alerts are filtered by your team: if you are on Red, you only see Blue avatar alerts (your enemy), and vice versa. Team is derived automatically from your own calls. This can be overridden in Developer Options.

---

## Disconnect Alerts

If a player who holds active calls leaves the Friends Chat, an alert prints in game chat listing which calls they held. This alert fires only once per player per game — if they rejoin and leave again, no second alert is sent.

---

## Live Timer & Score

When enabled, the overlay and side panel display:
- **Countdown timer** — live MM:SS game timer
- **Kill score** — current kills scored by each team (e.g. `R: 2  B: 1`)

This can be toggled in General Settings → Show Timer & Score.

---

## End-of-Game Summary

When a game ends and Auto-Clear is enabled, a summary prints to game chat:

```
═══ Zealgains: Game Summary ═══
Red Calls — R1: PlayerA  R2: PlayerB  R3: PlayerC
Blue Calls — B1: PlayerD  B2: PlayerE
Final Score — Red 3 / Blue 2  |  Time remaining: 2:14
```

This only fires on auto-clear (game end detection), not on manual `::zgreset`.

---

## Ban List

The plugin can download a remote ban list and highlight matching FC members.

- Set a raw URL to a plain-text file of banned names (one per line) in **ZG Ranks → Ban List URL**
- Highlighted in the configurable Ban List Color (default red)
- Optional notification when a banned player joins the FC
- Use `::zgsync` to force-refresh the list (5-minute cooldown)

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

| Command | Description |
|---------|-------------|
| `::zgreset` | Clears all tracked calls and runners |
| `::zgreset r2 b3` | Resets specific slots. Remaining callers reshuffle into sequential order and open slots are announced in chat |
| `::zgreset r34` | Shorthand — resets R3 and R4 in one command |
| `::zgsync` | Force-refreshes the ban list (5-minute cooldown) |

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
| **Rules Guide** | Discord link, call rules, runner callout formats |
| **General Settings** | Display mode, timer/score toggle, auto-clear, hide outside game, avatar alerts |
| **General Settings Guide** | Command reference and overlay behaviour |
| **ZG Ranks** | Rule break alerts, cross-world detection, FL/PM highlights, ban list |
| **ZG Ranks Guide** | Explanation of every ZG Ranks option and `::zgsync` |
| **Developer Options** | Override call filter for avatar alerts |

---

## Discord

For rules and methods: **discord.gg/riseabove**
