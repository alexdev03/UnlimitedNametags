<div align="center">

   <h1><b>Unlimited Name Tags Plugin</b> 🎮✨</h1>
   <p><i>A powerful tool to customize and manage player name tags like never before!</i></p>

   <br>

  <img alt="GitHub Release" src="https://img.shields.io/github/release/alexdev03/unlimitednametags.svg">
  <a href="https://www.codefactor.io/repository/github/alexdev03/unlimitednametags"><img src="https://www.codefactor.io/repository/github/alexdev03/unlimitednametags/badge" alt="CodeFactor" /></a>
  <br>
  <a href="https://www.spigotmc.org/resources/unlimitednametags.117526/"><img alt="SpigotMC" src="https://img.shields.io/badge/-SpigotMC-blue?style=for-the-badge&logo=SpigotMC"></a>
  <a href="https://builtbybit.com/resources/unlimitednametags.46172/"><img alt="BuiltByBit" src="https://img.shields.io/badge/-BuiltByBit-lightblue?style=for-the-badge&logo=BuiltByBit"></a>
  <br>
  <a href="https://discord.gg/W4Fu8fqCKs"><img alt="Discord" src="https://img.shields.io/badge/-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <br>
  <br>
  <img alt="Unlimited Name Tags in Action" src="https://i.imgur.com/w7zlGaO.gif">

</div>

---

## 📌 **Overview**
Unlimited Name Tags is a robust plugin designed for Minecraft servers running **Paper 1.20.1+** or **Spigot 1.20.2+**. While the plugin supports Spigot, **Paper is highly recommended** for optimal performance and compatibility with advanced features.

Enhance your server's customization by tailoring player name tags, integrating dynamic placeholders, and supporting vanish plugins seamlessly. Built with ease-of-use and flexibility in mind, this plugin elevates the player experience while simplifying server management.

---

## 🌟 **Features**
- **🎨 Customizable Name Tags**: Change colors, formats, lines, and add extra details to player name tags.
- **🚀 Smooth Movement**: Name tags are not entities that are teleported every tick. This ensures that when a player moves, the name tag does not lag and follows the player smoothly, as the movement is entirely client-side. The effect is identical to vanilla name tag movement.
- **⚡ Placeholder Support**: Integrate with [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) for dynamic, real-time information.
- **👥 Relational Placeholders**: Fully supports relational placeholders to display dynamic information based on the relationship between players.
- **🕵️ Vanish Integration**: Automatically hide name tags for vanished players.
- **⚙️ Easy Configuration**: Simple yet powerful configuration via `settings.yml`.
- **🛠️ Bedrock Support**: Limited Bedrock compatibility through Geyser; text displays are converted into armor stands due to platform restrictions.
- **📏 Conditional Lines**: Add or remove lines dynamically based on specific conditions or placeholders.
- **🔤 Multiple Text Formatters**: Choose from **MINIMESSAGE**, **MINEDOWN**, **LEGACY**, or **UNIVERSAL** formats:
   - **UNIVERSAL**: The most resource-intensive but supports all formatting options (except for **MINEDOWN**).  
     Examples:
      - **LEGACY OF LEGACY**: `&x&0&8&4&c&f&bc`
      - **LEGACY**: `&#084cfbc`
      - **MINEDOWN**: `&#084cfbc&`
      - **MINIMESSAGE**: `<color:#084cfbc>`
- **🌌 Simulate Lunar Client's Show Own Nametag Mod**: Enable the ability to display your own name tag, simulating the behavior of Lunar Client's mod.
- **🔄 Placeholder Replacements**: Customize placeholder outputs dynamically. Example:
   - Placeholder: `%advancedvanish_is_vanished%`
   - Replacements:
      - **"Yes"**: `&7[V]&r`
      - **"No"**: `""`

---

## 🚀 **Getting Started**

### **Installation**

1. **Download the Plugin**  
   Get the latest Unlimited Name Tags JAR from:
    - [BuiltByBit](https://builtbybit.com/resources/unlimitednametags.46172/)
    - [SpigotMC](https://www.spigotmc.org/resources/unlimitednametags.117526/)

2. **Add Dependencies**  
   Download [PacketEvents](https://modrinth.com/plugin/packetevents) and place it in the `plugins` directory.

3. **Upload to Server**  
   Place both JAR files (`UnlimitedNameTags` and `PacketEvents`) in the `plugins` directory.

4. **Restart the Server**  
   Restart your server to load the plugin.

5. **Configure**  
   Customize the `settings.yml` file located in the plugin's folder to suit your server’s needs.

---

## 🛠️ **Commands**

#### Main:
- **`/unt`**: Displays the plugin version and a list of available commands. *(Permission: none)*
- **`/unt reload`**: Reloads the plugin configuration without restarting the server. *(Permission: `unt.reload`)*
- **`/unt debug`**: Performs a debug operation for troubleshooting. *(Permission: `unt.debug`)*
- **`/unt debugger <true/false>`**: Enable or disable the debugger. *(Permission: `unt.debug`)*

#### Name Tag Management:
- **`/unt show <player>`**: Displays the name tag for a specific player. *(Permission: `unt.show`)*
- **`/unt hide <player>`**: Hides the name tag for a specific player. *(Permission: `unt.hide`)*
- **`/unt refresh <player>`**: Refreshes the name tag of a specific player for the command sender. *(Permission: `unt.refresh`)*

#### Customization and Configuration:
- **`/unt billboard <type>`**: Sets the default billboard type (e.g., `CENTER`, `FIXED`, etc.). *(Permission: `unt.billboard`)*
- **`/unt formatter <formatter>`**: Sets the default name tag formatter. *(Permission: `unt.formatter`)*

#### Managing Other Players' Name Tags:
- **`/unt hideOtherNametags [-h]`**: Hides the name tags of other players. Use the `-h` flag to suppress the confirmation message. *(Permission: `unt.hideOtherNametags`)*
- **`/unt showOtherNametags [-h]`**: Displays the name tags of other players. Use the `-h` flag to suppress the confirmation message. *(Permission: `unt.showOtherNametags`)*

---

## 🔒 **Default Permissions**
- **`unt.shownametags`**: Enabled by default. Revoking this permission hides other name tags globally for the player.
- **`unt.showownnametag`**: Enabled by default. Revoking this permission hides the player's own name tag.

---

## 🔌 **Integrations**

Unlimited Name Tags works seamlessly with:

- **[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)**: Add dynamic data (e.g., health, rank) to name tags. Relational placeholders are fully supported.
- **Vanish Plugins**: Automatically hide name tags for invisible players.
- **TypeWriter**: Adjust name tags during cinematic mode.
- **[Nexo](https://polymart.org/resource/nexo.6901)**: Ensures compatibility with 3D helmets.
- **[Oraxen](https://oraxen.com/)**: Ensures compatibility with 3D helmets.
- **MiniPlaceholders**: Works when using MiniMessage for advanced formatting.
- **Custom Plugins**: Easily hook into your custom plugins to extend functionality.

---

## 📜 **Supported Versions**
- **Paper**: Fully supported from **1.20.1+** *(highly recommended)*.
- **Spigot**: Supported from **1.20.2+**, but Paper is preferred for enhanced performance. (Not all spigot versions can be supported)

> **Note**: Versions below **1.19.4** do not support text displays because the required packet functionality does not exist. Additionally, clients connecting with **ViaBackwards** are not compatible and will not display name tags correctly. For the best experience, ensure both the server and clients are using compatible versions.
> You may connect with 1.19.4 client using **ViaBackwards** and **ViaVersion** and a server on one of the supported versions.

---

## 💬 **Support**

Need help? Join our [Discord Server](https://discord.gg/W4Fu8fqCKs)! For **pre-sale questions**, feel free to use the **#chat** channel. If you need support, please open a ticket and ensure your license is verified to gain access to assistance.

---

Take your server customization to the next level with **Unlimited Name Tags**! 🚀
