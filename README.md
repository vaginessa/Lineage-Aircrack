# Aircrack on Sailfish Lineage
The goal of this project was to modify LineageOS 16.1 to support the [aircrack-ng](https://aircrack-ng.org/) wireless security testing suite on a Google Pixel (codename sailfish). The Google Pixel does not have a wireless card capable of packet injection, so this README has a guide on including kernel modules that allow a supported, external USB wireless card. If your goal is simply to install the aircrack suite, a special add-on package is included that will copy [precompiled](https://github.com/kriswebdev/android_aircrack) aircrack binaries to /system/xbin. If you would like a control app, [Hijacker](https://github.com/chrisk44/Hijacker) has been forked to interface with this implementation. 

This guide uses sailfish as the target device, but adaptation of this method to other devices supported by Lineage should be relatively easy. 

### By setting up this project, your phone will be *less* secure. This should only be used for educational purposes. 
#### This project is very technical as I have not had the time to implement a simple installer. Many features of Hijacker do not work. 
I explain how to pull source and build everything. Experience compiling a kernel and android apps is not required, but will help.

## Setting up the drivers
This guide assumes the target device is a Google Pixel (Sailfish). If you would like to build for another device, start [here](https://wiki.lineageos.org/devices/). Look for your device, and follow the corresponding build guide.

1. Follow [this guide](https://wiki.lineageos.org/devices/sailfish/build) to set up the build tools and source tree for LineageOS 16.1 on Sailfish. **Stop** after extracting proprietary blobs. It is assumed the source tree root is at Lineage/
2. Generate the kernel menuconfig

    a. Change directories to the kernel root. 
    ```bash
    cd Lineage/kernel/google/marlin
    ```
    The Marlin codename is for the Google Pixel XL, which has rougly the same kernel as the Sailfish. For another device, this directory will likely be `Lineage/kernel/<manufacturer>/<codename>`
  
    b. Generate the kernel configuration file.
    ```bash
    make ARCH=arm64 lineageos_marlin_defconfig SELINUX_DEFCONFIG=selinux_defconfig
    ```
    The defconfig file is generally of the form `lineageos_<device-name>_defconfig` and is the specified value for the TARGET_KERNEL_CONFIG variable in the BuildConfig file. ARCH should be set to 'arm' if your device is only 32 bit. 
    
    c. Run menuconfig
    ```bash
    make ARCH=arm64 menuconfig
    ```
    An ncurses menu will appear. Use the arrow keys to navigate. For options with an arrow, hitting \<enter\> goes into it's submenu. Use \<esc\> to back out of a submenu or exit editting the config. Use \<spacebar\> to enable/disable modules. 
    
    d. Go to "Networking support" -> "Wireless" and enable the following:
    ```
    [ * ] Generic IEEE 802.11 Networking Stack (mac80211)
    [ * ] cfg80211 wireless extensions compatibility
    ```
    
    e. Go back to the root menu, then to "Device Drivers" -> "Network device support" -> "Wireless LAN". Here you will enable the specific drivers for your device. You can find out what wireless chipset you have by checking [here](https://www.aircrack-ng.org/doku.php?id=compatible_cards). For example, if you have a ralink rt2800usb device, find "Ralink driver support" and enable the rt2800usb driver options. You may need to enable the "Ralink driver support" option before any submenu options appear. Be sure to enable Promiscuous mode if the option is available. 
    
    f. Save and exit. This will generate a `.config` file in the local directory. 

3. Backup the default LineageOS defconfig from `Lineage/kernel/<vendor>/<device>/arch/arm64/configs/lineageos_<device-name>_defconfig`. Then, copy the generated config file to overwrite this file with the following command: 
```bash
cp .config Lineage/kernel/google/marlin/arch/arm64/configs/lineageos_marlin_defconfig
```

4. Install the firmware blob. The firmware blob does not change across devices, so this can be copied from the internet, or, more easily, from your linux computer. 

    a. Identify your wireless card with `lspci -k`. This will show all of the devices currently in use by the kernel, so grepping for your network adapter and then running `lspci -vv -s XX:XX.X` with the ID will make it easier to find. Look for the line `Kernel driver in use` and make a note of the driver name.
    
    b. Using the name of the driver found in the previous step, run 
    ```
    modinfo <driver>
    ```
    c. Using the information you obtained about the name of the driver, `modinfo`, and the internet, search through `/usr/lib/firmware` for the firmware file required by the device. In some cases, `modinfo` will directly state the filename of the firmware. 
    
    d. Copy the firmware binary to `Lineage/vendor/google/sailfish/proprietary/vendor/firmware`
    
    e. Add the line `vendor/firmware/<firmware filename>` to `device-proprietary-files-vendor.txt` under `Lineage/device/google/marlin/sailfish` 
    ```
    cat "vendor/firmware/<firmware.bin>" >> Lineage/device/google/marlin/sailfish/device-proprietary-files-vendor.txt
    ```
    
    f. To tell the Lineage build system to include the new firmware blob, run
    ```
    sudo Lineage/device/google/marlin/sailfish/setup-makefiles.sh
    ```

5. Continue the Lineage build as normal, moving onto the build cache step on the [wiki build page](https://wiki.lineageos.org/devices/sailfish/build). 

6. Complete the ROM build. LineageOS now supports your external wireless card. 

## Installing the standalone binaries
If you just want the aircrack binaries as executable programs from the command line, an addon package has been made for LineageOS 16.1. See the addon-wifi directory for the signed package.

1. [Install LineageOS normally](https://wiki.lineageos.org/devices/sailfish/install).
2. After sideloading LineageOS but *before* rebooting, flash my [wifi addon](https://github.com/pkelly916/Lineage-Aircrack/tree/master/addon-wifi). 
3. *Optional:* Install the [su addon](https://download.lineageos.org/extras). If you would prefer to not root the entire device, LineageOS's basic su can be replaced with another root implementation such as [SuperSU](https://supersu.en.uptodown.com/android) or [Magisk](https://www.xda-developers.com/how-to-install-magisk/). A root implementation is *required* to use the aircrack binaries. 
4. Open a root terminal and run airmon-ng, aircrack-ng, etc as normal. 

## Hijacker

[Hijacker](https://github.com/chrisk44/Hijacker) is a Graphical User Interface for the penetration testing tools [Aircrack-ng and Airodump-ng](https://www.aircrack-ng.org/). It offers a simple and easy UI to use these tools without typing commands in a console and copy&pasting MAC addresses. 

The original Hijacker implementation assumes that a root shell spawned by the app is never halted, but due to Lineage's battery management system this is not always true. As a workaround, I have modified the shell commands Hijacker runs to be more persistent. I have also changed some of the ways Hijacker interacts with the underlying aircrack binaries as they run to be more reliable. Previously, Hijacker would watch what aircrack et al. would send to standard output. However, due to the privilege changes required to make Hijacker run aircrack on LineageOS, reading from standard output was no longer reliable. Instead, watchdogs were modified or created to watch for result files and act accordingly. I have not ported the MDK or Reaver exploits, so they will not work. WPA and WPA2 cracking works perfectly. This implementation can capture IVs and crack WEP keys, but it cannot use Aireplay to speed up the process. 

To build Hijacker, open the Hijacker directory in Android Studio and build the APK. 

To install Hijacker, copy the built APK to your LineageOS install and tap on it in the file manager. You may need to allow the installation of unknown apps.
