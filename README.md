# CatchRect

A minimalist Android arcade game where your reflexes are the only thing standing between a high score and game over.

<a href="https://play.google.com/store/apps/details?id=com.helltar.catchrect">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="200">
</a>

## 📱 Gameplay

A paddle sits at the bottom of the screen and squares rain down from above. Slide the
paddle — drag anywhere on screen, or use the arrow keys / `A` · `D` — to catch the good
squares and dodge the rest. You start with **3 lives**; lose them all and it's game over.

### Catch these

| Square | Effect |
|--------|--------|
| ⬜ White | **+1 point** — the backbone of your score |
| 🟩 Green | **+1 life** |
| 🟦 Shield | **Blocks red hits for a few seconds** |
| 🟪 Slow Motion | **Slows every falling square for a few seconds** |

### Avoid these

| Square | Effect |
|--------|--------|
| 🟥 Red | **−1 life** — and some fall fast |
| 🟧 Platform Slow | **Your paddle turns sluggish for a few seconds** |
| 🔀 Invert Controls | **Left becomes right for a few seconds** |

### Combos & difficulty

Catch white squares back-to-back to build a **combo multiplier** — the longer the streak,
the more each catch is worth:

- **5 in a row → ×2**
- **10 in a row → ×3**
- **20 in a row → ×5**

Catching a red square or letting a white one slip past resets the streak to zero. And the
higher your score climbs, the harder it gets: squares fall faster, spawn more often, and
your paddle gradually shrinks. How long can you survive?

After each run you get a breakdown — survival time, best combo, whites caught, power-ups
used, and hits absorbed by your shield.

## 📖 The Story Behind CatchRect

The first version of CatchRect was made back in **2014** as a demo project for [AMPASIDE](https://github.com/Helltar/AMPASIDE) — an IDE for creating **JavaME** games (remember those old phones with .jar games?) using a Pascal-based language called **MIDlet Pascal**.

This Android version is a small tribute to the original — a reminder of where it all started.

## 📸 Screenshots

<p align="center">
  <img src="https://helltar.com/projects/catchrect-android/screenshots/pixel-9-gameplay-screenshot_20260311_133554.png" width="280" alt="gameplay screenshot"/>
  &nbsp;&nbsp;
  <img src="https://helltar.com/projects/catchrect-android/screenshots/pixel-9-leaderboard-screenshot_20260311_134035.png" width="280" alt="leaderboard screenshot"/>
</p>

## ☕ Development Process

**99.7%** of this code was written by AI.

My grueling, monumental contribution — the remaining **0.3%** — consisted entirely of pressing `Y`, writing slightly passive-aggressive prompts, and occasionally nodding at the screen with an air of deep technical authority.
