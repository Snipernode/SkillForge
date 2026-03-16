# SkillForge Plugin

## Overview

**SkillForge** is a Spigot/Paper 1.21.5 Minecraft plugin that implements a comprehensive RPG progression system with Everpoints, skill trees, and player customization.

### Core Features
- **Everpoint System**: Currency earned from XP (5 XP = 1 EP) and mob kills (dynamic EP based on mob type)
- **Skill Trees**: Seven categories (Combat, Mining, Agility, Intellect, Farming, Fishing, Magic) with 50+ total upgradeable nodes
- **Dynamic Progression**: Skill point costs scale from 5 EP to 25 EP over first 10 purchases
- **Item Shop**: Survival-obtainable items purchasable with Everpoints
- **Cosmetics System**: 10 unlockable cosmetic items for 10,000 EP each (all non-head items)
- **Gamemode System**: Choose between CASUAL (keep inventory) or ROGUELITE (lose inventory, keep skills)
- **Stamina System**: Sprint and jump drain stamina, which regenerates over time (scales with Agility)
- **Thirst System**: Thirst drains over time, drink water to restore it (scales with Intellect)
- **Innate Skills**: Each player gets one random innate skill chosen from 11 unique abilities
- **Ability System**: Hotbar slot binding with crouch + click activation, per-ability cooldowns, particle effects
- **Dev Tools**: `/debate` command for OPs to instantly max all stats (does NOT affect innate skills)

### Target Users
Server administrators looking to add flexible RPG progression without forcing specific playstyles on players. Replaces traditional grindy skill systems with player-driven progression.

### Tech Stack
- Java 21
- Spigot/Paper API 1.21.3
- Maven build system
- YAML data persistence

## Recent Changes

- **2026-03-09**: Version 20.x - Crates, Vote Hook, NPC Jobs/Careers
  - Added new crate types in `config.yml`:
    - `furniture_vanilla`
    - `furniture_modded`
    - `vote`
  - Extended crate reward parser for Nexo rewards:
    - `NEXO:<item_id>:<amount>`
    - `NEXO_ITEM:<item_id>:<amount>`
  - Added `VoteRewardSystem` with dynamic NuVotifier hook:
    - Accepts Votifier events by reflection
    - Duplicate vote protection
    - Configurable accepted services and per-vote commands
    - Grants `vote` crate keys (item or ledger path)
    - Optional KDHUD Discord-link requirement before rewarding votes
  - Added NPC job/career system:
    - `Merchant` and `Mercenary` career tracks
    - Daily contract targets
    - Payroll every 14 in-game days
    - Offline inactivity firing
    - Rehire lock/cooldown
    - Rank progression by completed contract days
    - Mercenary-only player bounty posting and claiming
    - Voice-line hooks (message + sound) via config
    - Optional KDHUD Discord-link requirement for joining/starting jobs
  - Added `/job` command:
    - Player flow: `status`, `join`, `accept`, `quit`, `voice`
    - Admin/NPC flow: `offer`, `setrank`
  - Added `skillforge.job.admin` permission and command registration in `plugin.yml`.
  - Skill usability and panel coverage updates:
    - Added tree/registry sync so registry-only skills are auto-added to Skill Panel categories
    - `/skillpanel bind <id>` now enforces unlock level and supports full registry skill IDs
    - Ability trigger path now resolves registry aliases (e.g., underscore variants)
    - Unknown bound skills now do passive refresh fallback instead of hard failure
  - Book GUI split behavior:
    - Added optional lower-area transparency mask for generic_54 background overrides
    - Keeps player inventory section visible while allowing top GUI art styling
    - Config keys:
      - `gui.bind.background_override.keep_player_inventory_visible`
      - `gui.bind.background_override.player_inventory_start_ratio`

- **2026-02-25**: CraftMine skill-tree scaffold TODO pass
  - Completed `CraftMineLayout` slot math helpers:
    - `toSlot(x,y)` now validates bounds and maps into 9x6 grid slots
    - `fromSlot(slot)` now returns valid grid coordinates or `{-1,-1}` for invalid slots
  - Completed `CraftMineSkillTreeManager` TODO sections:
    - Added unlock side effects (`applySkillEffects`) and unlock feedback (`playUnlockAnimation`)
    - Implemented slot-to-node mapping with exact-match logic
    - Added parent-null safety to unlock checks
  - Completed `CraftMineScreenTransport_Nexo` TODO sections:
    - Added open/update/close flow with tracked per-player views
    - Added inventory click routing through `SkillTreeClickEvent`
    - Added cleanup on inventory close and player quit
    - Added basic node rendering state (`Unlocked`, prerequisites, and glow for unlocked nodes)
  - Integrated scaffold into SkillForge runtime:
    - Added `SkillGraphSystem` to convert SkillForge categories into graph nodes
    - Added `/skillpanel tree [category]` and `/skillpanel graph [category]`
    - Updated Panels tab first button to open the graph skill tree
    - Graph clicks now use real SkillForge upgrades (SP cost + max level checks)
  - Advancement-style visual revamp for the graph tree:
    - Added top category tabs (`combat`, `mining`, `agility`, `intellect`, `farming`, `fishing`, `magic`, `mastery`)
    - Added wood-toned workspace with connector links between nodes
    - Reserved top row for tabs and moved node layout to staggered rows below
    - Added level summary + close control in the top-right slot
    - Restricted upgrades to exact node clicks (prevents accidental upgrades from background clicks)
  - Journal + health HUD follow-up:
    - Personal Journal layout adjusted to a cleaner two-page spread (less overlap/edge crowding)
    - Added 5-state custom health glyph rendering (empty, quarter, half, three-quarter, full)
    - Added Nexo font injection for private glyph codepoints (`\uE100`-`\uE104`)
    - Default health conversion set to `mc_hearts_per_gui_heart: 2.0` (each GUI heart = 2 MC hearts)
    - Added optional local texture override import during Nexo injection:
      - `New Imports/QuestBook - Blue.png`
      - `New Imports/Crate sprite sheet.png` / `crates sprite sheet.png` (copied as `crates_sprite_sheet.png` when present)

- **2025-12-26**: Version 19.0 - Guild Cards with Skill Bars
  - Added GuildCardSystem for creating player-bound guild card name tags
  - Guild cards display player's head and skill progress bars for all 7 categories
  - Each skill category shows visual bar (▰▱ format) with count (x/total)
  - Cards show Everpoints and Innate Skill level
  - `/guildcard` command creates guild cards with custom display names
  - Cards are personalized with player name and skill progress
  - Perfect for showcasing player progression and achievements
  - Used as name tag item with custom model data for identification

- **2025-12-25**: Version 18.0 - Quest Limits and Time Management
  - Added 3-quest maximum per player (prevents quest hoarding/farming)
  - Added 5 Minecraft day timeout per quest (120,000 ticks = 2 weeks game time)
  - Quests automatically cancel if time expires (checked every 5 minutes)
  - Player receives notification when quest expires
  - Quest start time tracked in Quest object
  - Player cannot start new quest if they already have 3 active
  - Timeout displayed when quest is started ("5 Minecraft days")
  - Expiry checker runs server-wide asynchronously every 5 minutes
  - Prevents infinite farming by forcing time management and quest switching

- **2025-12-25**: Version 17.0 - Quest System Expansion with Godlike Tier
  - Renamed EXTREME tier to GODLIKE (more impactful)
  - Updated difficulty scaling:
    - EASY: 5 (standard/items), 100 (farming), 50 (fishing)
    - NORMAL: 10 (standard/items), 200 (farming), 100 (fishing)
    - HARD: 20 (standard/items), 500 (farming), 250 (fishing)
    - HARDCORE: 100 (items), 100,000 (mob kills), 1000 (farming), 500 (fishing)
    - GODLIKE: 500 (items), 1,000,000 (mob kills), 5000 (farming), 2500 (fishing)
  - Added QuestType enum for different activity categories
  - Separate scaling for mob kills (very high at Godlike: 1M zombies)
  - Separate scaling for farming (higher due to easier mechanic)
  - Separate scaling for fishing (moderate difficulty)
  - Created QuestFactory with pre-made quests for all categories:
    - Combat: Zombie Slayer, Creeper Killer, Skeleton Slayer, Spider Hunter
    - Mining: Diamond Miner, Netherite Master, Iron Collector, Gold Digger
    - Farming: Wheat Farmer, Potato Master, Carrot Collector, Animal Breeder
    - Fishing: Master Fisherman, Treasure Hunter
    - Building: Master Builder, Demolition Expert
  - Can create custom quests for any item with QuestFactory

- **2025-12-25**: Version 16.0 - Quest Difficulty Scaling
  - Added QuestDifficulty enum: EASY → NORMAL → HARD → HARDCORE → EXTREME
  - Standard quest scaling: 5 → 10 → 20 → 50 → 100
  - Farming quest scaling (higher due to easier mechanic): 100 → 200 → 500 → 1000 → 2000
  - Quest auto-scales to next difficulty when completed (unless already EXTREME)
  - Quest name updates to show difficulty (e.g., "Zombie Slayer - Normal")
  - New quest requirements shown to player on auto-scale
  - Farming quests use double scaling to balance difficulty disparity
  - Player cannot farm same quest - must progress through difficulties
  - Maximum difficulty is EXTREME (100 kills/items or 2000 crops for farming)

- **2025-12-25**: Version 15.0 - Quest Completion System
  - Added QuestLeader system for managing quest completion
  - Quest class to define quests with requirements and tracking types
  - Validates quest completion by checking QuestHandler for required progress
  - When quest completed: resets ENTIRE activity progress (not partial) to prevent farming
  - Prevents players from standing still and farming the same quest repeatedly
  - Sends reset command back to QuestHandler to clear player activity data
  - Visual effects (fireworks + levelup sound) on quest completion
  - Methods: startQuest(), completeQuest(), cancelQuest(), hasActiveQuest(), getQuestProgress()
  - Per-player quest tracking with multiple simultaneous quests supported

- **2025-12-25**: Version 14.0 - Quest Tracking System
  - Added QuestHandler system for tracking player activities
  - Tracks mob kills, item collection, animal breeding, block breaking, block placement
  - Per-activity per-item tracking (e.g., "mobkill_zombie", "item_diamond")
  - Also tracks "all" variants (e.g., "mobkill_all" for total mobs, "item_all" for all items)
  - Methods to update, get, set, and reset quest progress
  - All data stored in PlayerData for persistence
  - Ready to receive commands from other systems in the plugin
  - No automatic actions unless commanded by other files

- **2025-12-25**: Version 13.0 - Complete Skill Tree GUI
  - Updated Skill Tree GUI to display all 7 categories (previously only showed 4)
  - Added Farming Skills category (7 skills including innate abilities)
  - Added Fishing Skills category (6 skills including innate abilities)
  - Added Magic Skills category (9 skills including innate abilities)
  - Expanded inventory size from 27 to 45 slots to accommodate all categories
  - Each category now easily accessible with proper icon representation (hoe for farming, fishing rod for fishing, amethyst for magic)
  - Players can now view and upgrade all 59 skills across all categories

- **2025-12-24**: Version 12.0 - Innate Skill Enhancements
  - Added innate skill reroll protection: Players can only reroll when ALL skills are level 100
  - First assignment has no restrictions (perfect for fresh players)
  - Auto-roll functionality: `/innate roll` now generates random 1-11 automatically instead of requiring manual entry
  - New Innate Upgrade GUI: Visual interface for upgrading innate skills (levels 1-20)
  - GUI shows current level, available skill points, and cost per level
  - Click any level in GUI to upgrade directly to that level (costs 10 SP × levels skipped)
  - Green dye = owned levels, Yellow = affordable upgrades, Red = insufficient SP
  - Added `/innate help` command showing all available commands
  - Improved command clarity with unified help system

- **2025-12-24**: Version 11.0 - Innate Skills and Enhanced Abilities
  - Added Innate Skill System: Players get one random innate skill from 11 unique options
  - Innate skills can be assigned using `/innate <1-11>` after using Minecraft's `/roll` command
  - Innate skills are independent from /debate command (god mode doesn't affect them)
  - Innate skills are upgradeable with skill points (`/innate upgrade`)
  - Innate skills have individual cooldowns and level-scaling effects
  - Added 11 innate skills: Magic Invisibility, Meteor Strike, Whirlwind, Shadowstep, and 7 more unique abilities
  - Enhanced all 5 active abilities with particle effects and sound effects
  - Ability effects: Fireball (knockback+burn), Frostbolt (snowflakes+slow), Mana Shield (enchant aura), Dodge (cloud trail), Strength (explosion)
  - Invisibility spell: Makes player and armor invisible for 10 seconds with smoke effects

- **2025-12-18**: Version 8.1 - Cosmetics fix and expanded skill tree
  - Fixed cosmetics GUI not displaying items (inventory state tracking)
  - Expanded skill tree from 4 categories (12 skills) to 7 categories (31 skills)
  - Added Farming tree: Growth, Harvest Yield, Animal Breeding, Pest Control, Irrigation
  - Added Fishing tree: Fisher's Luck, Quick Cast, Rare Catch, Deep Sea Diver
  - Added Magic tree: Fireball, Frostbolt, Mana Shield, Spellcraft, Mana Pool
  - Enhanced existing trees with new skills: Combat (Defense, Regeneration), Mining (Prospector, Stoneworking, Excavation), Agility (Acrobatics, Parkour, Climbing Mastery), Intellect (Arcana, Alchemy, Scholarship)
  - Updated cosmetics count from 8 to 10 in all UI displays

- **2025-12-18**: Version 7.0 - Major update with survival systems
  - Added Gamemode Selection (CASUAL/ROGUELITE) on first join
  - Implemented Stamina System (sprint/jump drain, regeneration)
  - Implemented Thirst System (time drain, drink water to restore)
  - Unified `/sp` command for battlepass and skill tree access
  - Dynamic mob kill EP rewards (100 EP for bosses, 5-30 for normal mobs)
  - Removed head cosmetics, updated to non-head cosmetic items
  
- **2025-10-09**: Complete plugin implementation with all core systems
  - Implemented dual EP gain system (XP conversion + mob kill bonuses)
  - Added XP remainder tracking for accurate 5:1 conversion
  - Created interactive GUI menus for battlepass, skills, shop, and cosmetics
  - Implemented YAML-based player data persistence

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Plugin Architecture
- **Main Plugin Class**: `SkillForgePlugin` - Entry point, manages initialization and shutdown
- **Event Listeners**: `PlayerEventListener` - Handles player join/quit, mob kills, and XP gains
- **Command System**: Individual command classes for each command (9 total commands)
- **GUI System**: Interactive inventory GUIs using Bukkit inventory API

### Data Layer
- **Technology**: YAML file storage (one file per player)
- **Data Model**: `PlayerData` class tracks EP, SP, XP remainder, skill levels, and cosmetics
- **Persistence**: `PlayerDataManager` handles load/save operations
- **Storage Location**: `plugins/SkillForge/playerdata/*.yml`

### Core Systems

#### Everpoint System (`EverpointSystem`)
- Tracks XP remainders per player to ensure exact 5:1 conversion
- Grants bonus EP from mob kills (configurable)
- Manages skill point purchase costs with dynamic pricing

#### Skill Tree System (`SkillTreeSystem`)
- 31 pre-defined skill nodes across 7 categories
- Each skill has configurable max levels and costs
- Categories: Combat (5 skills), Mining (6 skills), Agility (6 skills), Intellect (6 skills), Farming (5 skills), Fishing (4 skills), Magic (5 skills)
- Passive bonuses (health, damage, speed, XP gain, etc.)

#### Ability System (`AbilityExecutionSystem`)
- Players bind skills to hotbar slots (1-9)
- Activate with crouch + left/right click on bound items
- 5 active abilities with particle effects: Fireball, Frostbolt, Mana Shield, Dodge, Strength
- Per-ability cooldowns (1.5s to 4s)
- Damage and duration scale with skill level

#### Innate Skill System (`InnateSkillSystem`)
- Each player is assigned one random innate skill from a pool of 11
- Assign using `/innate <number>` or auto-roll with `/innate roll`
- Reroll protection: Can only change innate skill when ALL skills are level 100
- Innate skills have individual cooldowns and per-level scaling
- Upgradeable via visual GUI or `/innate upgrade` command (10 SP per level)
- GUI shows all 20 levels with cost breakdown and current progress
- NOT affected by `/debate` god mode command
- Available skills: Magic Invisibility, Meteor Strike, Whirlwind, Shadowstep, and 7 more unique abilities

#### GUI System
- **Main Panel GUI**: Central hub for accessing all features
- **Skill Tree GUI**: 7 categories with expanded skill lists
- **Ability Binding GUI**: Bind skills to hotbar slots for activation
- **Cosmetics GUI**: Fixed inventory tracking - all 10 cosmetics display correctly
- **Gamemode Selection GUI**: First-join selection between CASUAL and ROGUELITE
- **Innate Upgrade GUI**: Visual interface for level progression (1-20) with affordability indicators

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sp` | Open main skill panel interface | `skillforge.use` |
| `/skillforge` | Open skill tree interface | `skillforge.use` |
| `/everpoints` | Show EP balance | `skillforge.use` |
| `/skillpoints` | Show SP balance | `skillforge.use` |
| `/innate` | Use your innate skill instantly | `skillforge.use` |
| `/innate upgrade` | Open innate skill upgrade GUI | `skillforge.use` |
| `/innate roll` | Auto-roll and assign random innate skill (1-11) | `skillforge.use` |
| `/innate <1-11>` | Assign specific innate skill number | `skillforge.use` |
| `/innate help` | Show all innate skill commands | `skillforge.use` |
| `/debate` | Max all stats (OP only, excludes innate) | `skillforge.admin` |
| `/reloadforge` | Reload config | `skillforge.admin` |

## Skill Tree Details

### Combat (5 skills)
- Endurance, Critical Strike, Strength, Defense, Regeneration

### Mining (6 skills)
- Mining Efficiency, Fortune, Tool Durability, Prospecting, Stoneworking, Excavation

### Agility (6 skills)
- Speedstep, Dodge, Jump Boost, Acrobatics, Parkour, Climbing Mastery

### Intellect (6 skills)
- Wisdom, Enchanting Mastery, Brewing Mastery, Arcana, Alchemy, Scholarship

### Farming (5 skills)
- Growth, Harvest Yield, Animal Breeding, Pest Control, Irrigation

### Fishing (4 skills)
- Fisher's Luck, Quick Cast, Rare Catch, Deep Sea Diver

### Magic (5 skills)
- Fireball, Frostbolt, Mana Shield, Spellcraft, Mana Pool

## Configuration

Located at `src/main/resources/config.yml`:
- `xp-to-ep-ratio`: XP to EP conversion rate (default: 5)
- `ep-per-mob-kill`: Bonus EP from mob kills (default: 5)
- `base-cost`: Starting skill point cost (default: 5 EP)
- `level-10-cost`: Cost at 10th purchase (default: 25 EP)
- `unlock-cost`: Cosmetic unlock cost (default: 10,000 EP)
- `gamemodes.enabled`: Enable gamemode system (default: true)
- `stamina.enabled`: Enable stamina system (default: true)
- `thirst.enabled`: Enable thirst system (default: true)

## Build & Deployment

### Building
```bash
mvn clean package
```
Output: `target/SkillForge-12.0.jar`

### Installation
1. Place `SkillForge-12.0.jar` in your server's `plugins/` folder
2. Start or restart the server
3. Configure settings in `plugins/SkillForge/config.yml` if needed
4. Reload with `/reloadforge` or restart server

### Requirements
- Spigot or Paper 1.21.5 server
- Java 21 runtime

## External Dependencies

### Spigot API
- Spigot API 1.21.3-R0.1-SNAPSHOT (provided by server)
- Bukkit event system for player/entity events
- Inventory API for GUI menus

### Build Tools
- Apache Maven 3.9.9
- Maven Compiler Plugin 3.11.0
- Maven Shade Plugin 3.5.0

## Development Notes

### Testing on Live Server
While the plugin compiles successfully, it should be tested on a live Spigot 1.21.5 server to verify:
- Event timing and ordering under real server conditions
- XP handling from various sources (mining, smelting, mob kills, etc.)
- GUI interactions and menu navigation
- Data persistence across server restarts
- All 31 skills function correctly with their bonuses
- Innate skill reroll protection working correctly when all skills reach level 100
- Innate skill upgrade GUI displaying all levels (1-20) with correct cost calculations
- Cosmetics display and unlock properly in all cases
