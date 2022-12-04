# Excavator Mod

A [Minecraft](https://minecraft.net) (Java Edition) mod based on [`Fabric`](https://fabricmc.net/). The Mod extends the minecart ecosystem with an Excavator and features to improve the overall minecart experience.

![brief](doc/mining_area.jpg)

[Screenshots in documentation readme here](doc/README.md).

## Distribution file download

Main distribution channels:

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/excavator
- Modrinth: https://modrinth.com/mod/excavator

----
## Version history

This was my first MC mod, so I did my learning during the development. The first version was developed with the Forge loader, but later on I switched to Fabric. You can see the detailed changes in the [changelog](CHANGELOG.md).

----

## Screenshots and recipes

- [Screenshots](doc/README.md#screenshots)

- [Recipes](doc/README.md#recipes)

----

## Content

### Excavator
It is a new type of minecart what you can craft with a unique [recipe](doc/README.md#excavator-recipe). It digs a 1x3 tunnel by adding rail and optionally torches. This allows you to execute a mining area [pattern](doc/README.md#mining-area-blueprint). It works as a hopper minecart with the digging functionality on it. It places rail and torch (in every 6 block). You can move the minecart by hand, by furnace minecart or by using power rail and redstone torch. If the excavator detects any hazards (eg: cliff, lava , water) it will stop digging and indicates the hazard.

**Usage**
 - Craft the Excavator minecart
 - Place it on the rail (it will be in "MissingToolchain" state )
 - You have to add to the Excavator inventory:
   - **Rail** (this is consumed), which is placed once the 3 block in front of the excavator is mined. It could be simple [Rail](https://minecraft.fandom.com/wiki/Rail) or [Powered Rail](https://minecraft.fandom.com/wiki/Powered_Rail)
   - **Torch** (this is consumed), which is placed within distance of 6 blocks or at the turns. This is optional if you like darkness :smiling_imp:. You can use [Torch or Soul Torch](https://minecraft.fandom.com/wiki/Torch), [Redstone Torch](https://minecraft.fandom.com/wiki/Redstone_Torch)
   - **Tools** (it is not consumed). It must be a [Pickaxe](https://minecraft.fandom.com/wiki/Pickaxe) and [Shovel](https://minecraft.fandom.com/wiki/Shovel). The type of the tools determines the digging speed of the Excavator.
 - Push the Excavator with something e.g.: [Minecart with Furnace](https://minecraft.fandom.com/wiki/Minecart_with_Furnace)

**Excavator Status**

If the excavator is in one of the following state, the excavator is not activated.

* Stopped by Hazard (e.g.: Lava, Water, Cliff)
* Inventory Full
* Missing Toolchain
* Emergency Stop by a [Sign](https://minecraft.fandom.com/wiki/Sign) placed in front of the Excavator


## Planned features

- Better Excavator status display (replace the current particle effect method)
- Custom model and animation for the Excavator
- Mining area definition so you can control the excavation area
- Minecart trains (better minecart connectivity and physics)
- More minecart types:
- - Hopper minecart: black list
- - RailPickup minecart: this would allow you to pick up the rail behind the minecart and feed it back to the Excavator
- Mining logic for resupply (carrier return to a base an brings supply to the excavator). This would allow full automation of the excavator.