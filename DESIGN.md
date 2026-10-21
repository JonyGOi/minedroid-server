# Design System - MineDroid Server

## Visual Identity & Tone
MineDroid Server adopts a **modern, focused, high-precision dark mode** tailored for mobile system administration and Minecraft server hosting. It removes visual clutter, excessive card nesting, redundant technical copy, and ad-hoc colors, replacing them with a disciplined Material 3 architecture.

## Color Palette & Semantic Roles
- **Background (`surface-dim`):** `#0E1015` (Deep slate obsidian)
- **Primary Card Surface (`surface`):** `#161920` (Subtle 1dp elevation)
- **Container / Input Surface (`surface-container`):** `#1E232D` (Inner fields & chips)
- **Outline / Border (`outline`):** `#2A303C` (Subtle 1px boundary)
- **Primary Accent (`primary`):** `#10B981` (Vibrant Emerald for running state & primary actions)
- **Secondary Accent (`secondary`):** `#0EA5E9` (Sky Blue for network, Bedrock & Playit tunnels)
- **Warning (`tertiary`):** `#F59E0B` (Amber for proxy states & pending actions)
- **Error / Destructive (`error`):** `#EF4444` (Coral Red for stop, kill & deletion)
- **Text High Contrast (`on-surface`):** `#F8FAFC` (95% opacity white)
- **Text Medium Contrast (`on-surface-variant`):** `#94A3B8` (Muted slate)

## Spacing & Rhythm
Strict 8dp geometric grid:
- **Component Padding:** `16dp` outer, `12dp` inner.
- **Section Gaps:** `16dp` between top-level cards, `8dp` between related items.
- **Touch Target Floor:** `48dp` minimum for all buttons and icon controls.

## Typography (Material 3 Scale)
- **Headline Medium:** 20sp Bold (Screen titles)
- **Title Medium:** 16sp SemiBold (Card titles & server names)
- **Body Medium:** 14sp Regular (Descriptions & options)
- **Label Medium:** 12sp SemiBold (Badges, tags, hints)
- **Data / Addresses:** 13sp SemiBold Monospace (IPs, ports, commands)

## Anti-Patterns Banished
1. ❌ **No Nested Cards:** No cards inside cards inside cards. Inner groupings use flat container tints (`surface-container`) or subtle dividers without heavy borders.
2. ❌ **No Emoji as Icons:** Replace all emoji (`☕`, `🎮`, `⭐`, `🌐`) with consistent Material 3 vector icons (`Icons.Default.Dns`, `Icons.Default.Devices`, `Icons.Default.Terminal`, etc.).
3. ❌ **No Paragraph Cramming:** Remove walls of instructional text. Replace with crisp, glanceable status chips and tooltips.
4. ❌ **No Redundant Controls:** Don't present separate start buttons for server and tunnel when the server lifecycle already manages both.
