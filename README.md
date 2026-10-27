# TimerPlus

TimerPlus adds a configurable timer to Paper servers. It supports a global timer shared by all players, personal timers for individual players, and automatic stopping based on in-game goals. It is intended for challenge runs, speedruns and events.

## Overview

- Global timer controlled with `/timer`, personal timers with `/ptimer`
- Stopwatch (counting up) and countdown (counting down) modes
- Display in the action bar, the boss bar, or both
- Customizable designs using MiniMessage, including animated gradients
- Goals: stop the timer when a mob dies or an advancement is earned
- Rules: death actions, pausing on an empty server, auto-start
- Timer state is saved and survives server restarts
- English and German translations included

## Timers

The global timer is visible to all players. Each player can hide it for themselves with `/timer toggle` and use a personal timer instead. Time is measured in real time and does not depend on server TPS.

The time can be set or adjusted while the timer is running:

```
/timer set 1h30m
/timer add -30s
```

Accepted formats: `30`, `30s`, `5m`, `1h30m`, `1d2h`, `01:30`, `1:30:00`.

In countdown mode, chat warnings with a sound are sent at 60, 30, 10 and 5 to 1 seconds remaining. The seconds are configurable.

## Display

Each timer state (idle/paused, running, finished) has its own template. Templates use MiniMessage, so colors, gradients and rainbow text are supported. The `%phase%` placeholder animates gradients.

The time format is built from the placeholders `{d}`, `{h}`, `{m}`, `{s}` and `{ms}`. Parts in square brackets are only shown once the time reaches that unit, for example `[{h}:]{mm}:{ss}` shows hours only after one hour.

Five designs are included. Additional designs can be added in `config.yml`. Players can select their own design with `/ptimer design`.

## Goals

The global timer stops automatically when:

- a configured entity dies (the Ender Dragon by default; Wither, Warden and others can be added), or
- a player earns a configured advancement.

Goals are managed in game with `/timer goal add|remove|list` or in the config. When a goal is reached, the plugin shows a title and plays a sound. It can also run console commands. The best time is stored and announced when it is beaten.

## Rules

| Rule | Description |
|---|---|
| `death-action` | `NONE`, `SPECTATOR`, `KICK`, `PAUSE_TIMER`, `STOP_TIMER` or `RESET_TIMER` |
| `only-while-running` | Apply rules only while the global timer is running |
| `pause-when-empty` | Pause the timer when no players are online |
| `resume-when-player-joins` | Resume automatically when a player joins again |
| `auto-start-on-join` | Start the timer when the first player joins |
| `resume-on-startup` | Continue a running timer after a server restart |

Players with the `timerplus.bypass` permission are exempt from death actions.

## Commands

| Command | Description |
|---|---|
| `/timer resume`, `pause`, `reset`, `stop` | Control the global timer |
| `/timer set <time>` | Set the time (countdown: start time) |
| `/timer add <time>` | Add or subtract time |
| `/timer mode <up\|down>` | Switch between stopwatch and countdown |
| `/timer lap [name]`, `laps` | Record and list split times |
| `/timer design [name]` | Change the global design |
| `/timer toggle` | Hide or show the global timer for yourself |
| `/timer goal <list\|add\|remove>` | Manage goals |
| `/timer rule <rule> <value>` | Change rules |
| `/timer setup` | Open the settings menu |
| `/timer language <de\|en>` | Change the language |
| `/timer info` | Show state, time and best time |
| `/timer reload` | Reload the configuration |
| `/ptimer ...` | Same controls for the personal timer |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `timerplus.timer.control` | OP | Control and configure the global timer |
| `timerplus.timer.toggle` | Everyone | Hide or show the global timer for yourself |
| `timerplus.ptimer` | Everyone | Use a personal timer |
| `timerplus.bypass` | Nobody | Exempt from death actions |

## Requirements

- Paper 26.3 or newer
- Java 25

The plugin has no other dependencies. Folia is not supported.

## Installation

1. Place the jar file in the `plugins` folder.
2. Start the server once to generate the configuration.
3. Adjust `plugins/TimerPlus/config.yml` and run `/timer reload`.
4. Start the timer with `/timer resume`.

## API

TimerPlus fires a `TimerStateChangeEvent` whenever a timer changes state. The `TimerManager` is registered with the Bukkit `ServicesManager` for use by other plugins.

## Support

Bug reports and feature requests are welcome on the issue tracker.
